// Пайплайн мониторинга нод через JavaMelody с уведомлениями в Telegram (success/failure).
// Токен и бот берутся из: Manage Jenkins → Configure System → Telegram Notifications.

node {
    // Сводка для Telegram (заполняется в stage Monitor, используется в finally)
    def summary = [
        totalNodes: 0,
        totalSessions: 0,
        totalActiveThreads: 0,
        totalThreads: 0,
        totalUsedMemory: 0L,
        totalMaxMemory: 0L,
        avgCpuPercent: 0.0f,
        hasCpu: false,
        hasMemory: false
    ]

    try {
        stage('Checkout') {
            checkout scm
        }

        stage('Monitor Nodes via JavaMelody') {
            echo "=== Jenkins Nodes Monitoring via JavaMelody ==="
            echo "Time: ${new Date()}"
            echo ""

            echo "Collecting Java information from all nodes via JavaMelody API..."

            String nodeName = null
            Map mapByNodeName = new net.bull.javamelody.RemoteCallHelper(nodeName).collectJavaInformationsListByName()

            if (mapByNodeName == null || mapByNodeName.isEmpty()) {
                error("❌ ERROR: No nodes found or failed to collect information")
            }

            echo "Found ${mapByNodeName.size()} node(s)"
            echo ""
            echo "=" * 80
            echo ""

            def totalNodes = mapByNodeName.size()
            def totalThreads = 0
            def totalActiveThreads = 0
            def totalSessions = 0
            def totalUsedMemory = 0L
            def totalMaxMemory = 0L
            def totalSystemCpuLoad = 0.0
            def nodesWithCpuInfo = 0

            for (nodeEntry in mapByNodeName.entrySet()) {
                def nodeNameKey = nodeEntry.key
                def java = nodeEntry.value

                echo ""
                echo "Node: ${nodeNameKey}"
                echo "  Host: ${java.host ?: 'N/A'}"
                echo "  OS: ${java.os ?: 'N/A'}"
                echo "  Java Version: ${java.javaVersion ?: 'N/A'}"
                echo "  JVM Version: ${java.jvmVersion ?: 'N/A'}"
                echo "  PID: ${java.pid ?: 'N/A'}"
                echo "  Server Info: ${java.serverInfo ?: 'N/A'}"
                echo "  Context Path: ${java.contextPath ?: 'N/A'}"
                echo "  Start Date: ${java.startDate ?: 'N/A'}"
                echo "  Available Processors: ${java.availableProcessors ?: 'N/A'}"
                echo ""

                def sessionsCount = java.sessionCount ?: 0
                def activeThreadCount = java.activeThreadCount ?: 0
                def threadCount = java.threadCount ?: 0

                echo "  Sessions Count: ${sessionsCount}"
                echo "  Active HTTP Threads: ${activeThreadCount}"
                echo "  Total Threads: ${threadCount}"

                totalSessions += sessionsCount
                totalActiveThreads += activeThreadCount
                totalThreads += threadCount

                def systemLoadAverage = java.systemLoadAverage
                def systemCpuLoad = java.systemCpuLoad

                if (systemLoadAverage != null) {
                    echo "  System Load Average: ${String.format("%.2f", systemLoadAverage)}"
                }
                if (systemCpuLoad != null && systemCpuLoad >= 0) {
                    // JVM может возвращать 0–1 (доля) или уже 0–100 (проценты) — нормализуем к 0–100%
                    def nodeCpuPercent = (systemCpuLoad <= 1.0) ? (systemCpuLoad * 100.0) : (systemCpuLoad > 100.0 ? 100.0 : systemCpuLoad)
                    echo "  System CPU Load: ${String.format("%.2f%%", nodeCpuPercent)}"
                    totalSystemCpuLoad += nodeCpuPercent
                    nodesWithCpuInfo++
                }

                echo ""

                def memory = java.memoryInformations
                if (memory != null) {
                    def usedMemory = memory.usedMemory ?: 0L
                    def maxMemory = memory.maxMemory ?: 0L
                    totalUsedMemory += usedMemory
                    totalMaxMemory += maxMemory
                    def usedMemoryStr = usedMemory < 1024 * 1024 * 1024 ? String.format("%.2f MB", usedMemory / (1024.0 * 1024.0)) : String.format("%.2f GB", usedMemory / (1024.0 * 1024.0 * 1024.0))
                    def maxMemoryStr = maxMemory < 1024 * 1024 * 1024 ? String.format("%.2f MB", maxMemory / (1024.0 * 1024.0)) : String.format("%.2f GB", maxMemory / (1024.0 * 1024.0 * 1024.0))
                    echo "  Memory: ${usedMemoryStr} / ${maxMemoryStr}"
                }

                def threads = java.getThreadInformationsList()
                def deadlocked = threads?.findAll { it.deadlocked } ?: []
                echo "  Threads: ${threads?.size() ?: 0}, Deadlocked: ${deadlocked.size()}"
                echo ""
                echo "-" * 80
            }

            echo ""
            echo "=" * 80
            echo "Summary: Nodes=${totalNodes}, Sessions=${totalSessions}, ActiveThreads=${totalActiveThreads}, TotalThreads=${totalThreads}"
            if (totalMaxMemory > 0) {
                echo "Memory: ${String.format("%.2f GB", totalUsedMemory / (1024.0 * 1024.0 * 1024.0))} / ${String.format("%.2f GB", totalMaxMemory / (1024.0 * 1024.0 * 1024.0))}"
            }
            if (nodesWithCpuInfo > 0) {
                echo "Avg CPU: ${String.format("%.2f%%", totalSystemCpuLoad / nodesWithCpuInfo)}"
            }
            echo "=" * 80

            // Сохраняем сводку для Telegram
            summary.totalNodes = totalNodes
            summary.totalSessions = totalSessions
            summary.totalActiveThreads = totalActiveThreads
            summary.totalThreads = totalThreads
            summary.totalUsedMemory = totalUsedMemory
            summary.totalMaxMemory = totalMaxMemory
            summary.hasMemory = (totalMaxMemory > 0)
            summary.hasCpu = (nodesWithCpuInfo > 0)
            summary.avgCpuPercent = nodesWithCpuInfo > 0 ? (totalSystemCpuLoad / nodesWithCpuInfo) : 0.0f
        }
    } catch (org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException e) {
        echo "❌ ERROR: Script Approval required!"
        currentBuild.result = 'FAILURE'
        throw e
    } catch (Exception e) {
        echo "❌ ERROR: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        throw e
    } finally {
        // Форматированное уведомление в Telegram (использовано / свободно)
        try {
            def ok = (currentBuild.result == null || currentBuild.result == 'SUCCESS')
            def emoji = ok ? '✅' : '❌'
            def jobName = env.JOB_NAME ?: 'unknown'
            def buildNum = env.BUILD_NUMBER ?: '?'
            def buildUrl = env.BUILD_URL ?: ''

            def lines = []
            lines << "${emoji} ${jobName} #${buildNum} ${ok ? 'OK' : 'FAILED'}"
            lines << ""

            if (summary.totalNodes > 0) {
                lines << "📦 Ноды: ${summary.totalNodes}"
                lines << "🔌 Сессии: ${summary.totalSessions}"
                lines << "🧵 Потоки: активных ${summary.totalActiveThreads} / всего ${summary.totalThreads}"
                lines << ""
            }

            if (summary.hasMemory && summary.totalMaxMemory > 0) {
                def usedGb = summary.totalUsedMemory / (1024.0 * 1024.0 * 1024.0)
                def maxGb = summary.totalMaxMemory / (1024.0 * 1024.0 * 1024.0)
                def freeGb = maxGb - usedGb
                def usedPct = (summary.totalUsedMemory * 100.0) / summary.totalMaxMemory
                lines << "💾 Память:"
                lines << "   использовано ${String.format('%.2f', usedGb)} GB (${String.format('%.1f', usedPct)}%)"
                lines << "   свободно  ${String.format('%.2f', freeGb)} GB"
                lines << ""
            }

            if (summary.hasCpu) {
                lines << "🖥 CPU (средняя загрузка): ${String.format('%.1f', summary.avgCpuPercent)}%"
                lines << ""
            }

            lines << "🔗 ${buildUrl}"

            def msg = lines.join('\n')
            echo "Sending Telegram notification (${msg.length()} chars)..."
           // lib not work, use curl instead
           // telegramSend(message: msg)

              withCredentials([
                  usernamePassword(
                    credentialsId: 'telegram_auth',
                    usernameVariable: 'TG_CHAT_ID',
                    passwordVariable: 'TG_TOKEN'
                  )
                ]) {
                  def encoded = java.net.URLEncoder.encode(msg, 'UTF-8')
                  sh 'curl -s -X POST "https://api.telegram.org/bot${TG_TOKEN}/sendMessage" -d "chat_id=${TG_CHAT_ID}" -d "text=' + encoded + '"'
                }
            
            echo "Telegram notification sent."
        } catch (Exception ex) {
            echo "Telegram notify failed: ${ex.message}"
            ex.printStackTrace()
            // Частые причины: 1) не отправлен /sub боту в Telegram  2) В Jenkins → Telegram Notifications поле Usernames пусто или без твоего username
        }
    }
}
