// Скриптовый Jenkinsfile для мониторинга агентов через JavaMelody API
// Использует JavaMelody Monitoring Plugin напрямую
// ТРЕБУЕТСЯ: Одобрить скрипты в Script Approval (Manage Jenkins -> In-process Script Approval)

// Функция для форматирования размера памяти
@NonCPS
def formatMemory(long bytes) {
    if (bytes < 1024) {
        return "${bytes} B"
    } else if (bytes < 1024 * 1024) {
        return String.format("%.2f KB", bytes / 1024.0)
    } else if (bytes < 1024 * 1024 * 1024) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    } else {
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

// Функция для форматирования процентов
@NonCPS
def formatPercent(double value) {
    return String.format("%.2f%%", value)
}

node {
    try {
        stage('Checkout') {
            checkout scm
        }

        stage('Monitor Nodes via JavaMelody') {
            echo "=== Jenkins Nodes Monitoring via JavaMelody ==="
            echo "Time: ${new Date()}"
            echo ""
            
            echo "Collecting Java information from all nodes via JavaMelody API..."
            
            // Используем JavaMelody API напрямую
            // ВАЖНО: Этот код требует одобрения в Script Approval
            // Перейдите в: Manage Jenkins -> In-process Script Approval
            // И одобрите следующие сигнатуры:
            //   - new net.bull.javamelody.RemoteCallHelper java.lang.String
            //   - method net.bull.javamelody.RemoteCallHelper collectJavaInformationsListByName
            //   - method net.bull.javamelody.internal.model.JavaInformations getThreadInformationsList
            
            String nodeName = null  // null для всех нод
            Map mapByNodeName = new net.bull.javamelody.RemoteCallHelper(nodeName).collectJavaInformationsListByName()
            
            if (mapByNodeName == null || mapByNodeName.isEmpty()) {
                error("❌ ERROR: No nodes found or failed to collect information")
            }
            
            echo "Found ${mapByNodeName.size()} node(s)"
            echo ""
            echo "=" * 80
            echo ""
            
            // Статистика по всем нодам
            def totalNodes = mapByNodeName.size()
            def totalThreads = 0
            def totalActiveThreads = 0
            def totalSessions = 0
            def totalUsedMemory = 0L
            def totalMaxMemory = 0L
            def totalSystemCpuLoad = 0.0
            def nodesWithCpuInfo = 0
            
            // Обрабатываем каждую ноду
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
                
                // Информация о сессиях и потоках
                def sessionsCount = java.sessionCount ?: 0
                def activeThreadCount = java.activeThreadCount ?: 0
                def threadCount = java.threadCount ?: 0
                
                echo "  Sessions Count: ${sessionsCount}"
                echo "  Active HTTP Threads: ${activeThreadCount}"
                echo "  Total Threads: ${threadCount}"
                
                totalSessions += sessionsCount
                totalActiveThreads += activeThreadCount
                totalThreads += threadCount
                
                // Системная нагрузка
                def systemLoadAverage = java.systemLoadAverage
                def systemCpuLoad = java.systemCpuLoad
                
                if (systemLoadAverage != null) {
                    echo "  System Load Average: ${String.format("%.2f", systemLoadAverage)}"
                }
                if (systemCpuLoad != null && systemCpuLoad >= 0) {
                    def cpuPercent = systemCpuLoad * 100
                    echo "  System CPU Load: ${formatPercent(cpuPercent)}"
                    totalSystemCpuLoad += systemCpuLoad
                    nodesWithCpuInfo++
                }
                
                echo ""
                
                // Информация о памяти
                def memory = java.memoryInformations
                if (memory != null) {
                    def usedMemory = memory.usedMemory ?: 0L
                    def maxMemory = memory.maxMemory ?: 0L
                    def usedPermGen = memory.usedPermGen ?: 0L
                    def maxPermGen = memory.maxPermGen ?: 0L
                    def usedNonHeap = memory.usedNonHeapMemory ?: 0L
                    def usedPhysical = memory.usedPhysicalMemorySize ?: 0L
                    def usedSwap = memory.usedSwapSpaceSize ?: 0L
                    
                    echo "  Memory Information:"
                    echo "    Used Memory: ${formatMemory(usedMemory)}"
                    echo "    Max Memory: ${formatMemory(maxMemory)}"
                    
                    if (maxMemory > 0) {
                        def memoryPercent = (usedMemory / maxMemory) * 100.0
                        echo "    Memory Usage: ${formatPercent(memoryPercent)}"
                    }
                    
                    if (usedPermGen > 0) {
                        echo "    Used Perm Gen: ${formatMemory(usedPermGen)}"
                    }
                    if (maxPermGen > 0) {
                        echo "    Max Perm Gen: ${formatMemory(maxPermGen)}"
                    }
                    if (usedNonHeap > 0) {
                        echo "    Used Non-Heap: ${formatMemory(usedNonHeap)}"
                    }
                    if (usedPhysical > 0) {
                        echo "    Used Physical Memory: ${formatMemory(usedPhysical)}"
                    }
                    if (usedSwap > 0) {
                        echo "    Used Swap Space: ${formatMemory(usedSwap)}"
                    }
                    
                    totalUsedMemory += usedMemory
                    totalMaxMemory += maxMemory
                }
                
                echo ""
                
                // Проверка на deadlocked threads
                def threads = java.getThreadInformationsList()
                def deadlocked = new ArrayList()
                
                if (threads != null) {
                    for (thread in threads) {
                        if (thread.deadlocked) {
                            deadlocked.add(thread)
                        }
                    }
                    
                    echo "  Threads Status:"
                    echo "    Total Threads: ${threads.size()}"
                    echo "    Active Threads: ${activeThreadCount}"
                    echo "    Deadlocked Threads: ${deadlocked.size()}"
                    
                    if (deadlocked.size() > 0) {
                        echo ""
                        echo "  ⚠️  WARNING: Found ${deadlocked.size()} deadlocked thread(s)!"
                        for (thread in deadlocked) {
                            echo ""
                            echo "    Deadlocked Thread: ${thread}"
                            def stackTrace = thread.getStackTrace()
                            if (stackTrace != null) {
                                for (s in stackTrace) {
                                    echo "      ${s}"
                                }
                            }
                        }
                    }
                }
                
                echo ""
                echo "-" * 80
            }
            
            // Итоговая статистика
            echo ""
            echo "=" * 80
            echo "Summary Statistics:"
            echo "=" * 80
            echo "Total Nodes: ${totalNodes}"
            echo "Total Sessions: ${totalSessions}"
            echo "Total Active HTTP Threads: ${totalActiveThreads}"
            echo "Total Threads: ${totalThreads}"
            
            if (totalMaxMemory > 0) {
                def avgMemoryPercent = (totalUsedMemory / totalMaxMemory) * 100.0
                echo "Total Used Memory: ${formatMemory(totalUsedMemory)}"
                echo "Total Max Memory: ${formatMemory(totalMaxMemory)}"
                echo "Average Memory Usage: ${formatPercent(avgMemoryPercent)}"
            }
            
            if (nodesWithCpuInfo > 0) {
                def avgCpuLoad = (totalSystemCpuLoad / nodesWithCpuInfo) * 100.0
                echo "Average System CPU Load: ${formatPercent(avgCpuLoad)}"
            }
            
            echo ""
            echo "=" * 80
            echo "Monitoring completed successfully"
            echo "=" * 80
        }
    } catch (org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException e) {
        echo "❌ ERROR: Script Approval required!"
        echo ""
        echo "Для использования JavaMelody API необходимо одобрить скрипты:"
        echo "1. Перейдите в: Manage Jenkins -> In-process Script Approval"
        echo "2. Одобрите следующие сигнатуры:"
        echo "   - new net.bull.javamelody.RemoteCallHelper java.lang.String"
        echo "   - method net.bull.javamelody.RemoteCallHelper collectJavaInformationsListByName"
        echo "   - method net.bull.javamelody.internal.model.JavaInformations getThreadInformationsList"
        echo ""
        echo "После одобрения запустите pipeline снова."
        currentBuild.result = 'FAILURE'
        throw e
    } catch (Exception e) {
        echo "❌ ERROR: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        throw e
    }
}


// // Функция для парсинга JSON (должна быть вне pipeline блока)
// @NonCPS
// def parseJson(String json) {
//     return new groovy.json.JsonSlurper().parseText(json)
// }
// pipeline {
//     agent any

//     stages {
//         stage('Checkout') {
//             steps {
//                 checkout scm
//             }
//         }

//         stage('Monitor Slaves') {
//             steps {
//                 script {
//                     echo "=== Jenkins Agents Monitoring ==="
//                     echo "Time: ${new Date()}"
//                     echo ""
                    
//                     // Получаем информацию о всех агентах через Jenkins REST API
//                     // Используем curl, так как Groovy API требует разрешений sandbox
//                     def jenkinsUrl = null
//                     def agentsJson = null
                    
//                     // Пробуем определить URL Jenkins
//                     // Из конфигурации Docker Cloud: jenkinsUrl: "http://192.168.64.1:8080"
//                     def urlsToTry = []
                    
//                     // Добавляем URL из переменной окружения (если есть)
//                     if (env.JENKINS_URL) {
//                         urlsToTry.add(env.JENKINS_URL)
//                         echo "Added JENKINS_URL from env: ${env.JENKINS_URL}"
//                     }
                    
//                     // Добавляем стандартные варианты (из конфигурации)
//                     urlsToTry.addAll([
//                        'http://192.168.65.4:8080',       // IP хоста (из jenkins.yaml)
//                         'http://jenkins:8080',             // Имя контейнера (если в той же сети)
//                         'http://192.168.65.1:8080',       // IP Jenkins в monitoring-network
//                         'http://localhost:8080'            // Fallback
//                     ])
                    
//                     echo "URLs to try: ${urlsToTry}"
//                     echo ""
                    
//                     // Пробуем подключиться к каждому URL
//                     for (url in urlsToTry) {
//                         echo "Trying URL: ${url}"
                        
//                         // Пробуем получить данные через API
//                         // Используем простой запрос без tree параметра (может вызывать проблемы)
//                         def result = sh(
//                             script: """
//                                 curl -s --connect-timeout 5 --max-time 10 -u admin:admin123 '${url}/computer/api/json' 2>&1 || echo "CURL_ERROR"
//                             """,
//                             returnStdout: true
//                         ).trim()
                        
//                         echo "Response length: ${result.length()}"
//                         echo "Response preview (first 500 chars): ${result.take(500)}"
                        
//                         // Проверяем, что это валидный JSON
//                         if (result && result != "CURL_ERROR" && result.startsWith("{") && !result.contains("curl:") && !result.contains("Could not resolve") && !result.contains("Connection refused") && !result.contains("timeout") && !result.contains("Connection timed out")) {
//                             try {
//                                 // Пробуем распарсить JSON
//                                 def testParse = parseJson(result)
//                                 agentsJson = result
//                                 jenkinsUrl = url
//                                 echo "✅ Successfully connected to Jenkins at: ${jenkinsUrl}"
//                                 break
//                             } catch (Exception e) {
//                                 echo "❌ Invalid JSON response from: ${url}"
//                                 echo "Error: ${e.message}"
//                                 echo "Response preview: ${result.take(500)}"
//                             }
//                         } else {
//                             echo "❌ Failed to connect to: ${url}"
//                             if (result && result.length() > 0) {
//                                 echo "Full response: ${result}"
//                             } else {
//                                 echo "Empty response or connection error"
//                             }
//                         }
//                         echo ""
//                     }
                    
//                     if (!agentsJson) {
//                         error("❌ ERROR: Failed to connect to Jenkins API from any URL")
//                     }
                    
//                     // Парсим JSON
//                     def agents = parseJson(agentsJson)
//                     def computers = agents.computer ?: []
                    
//                     echo "Found ${computers.size()} computer(s) in Jenkins"
//                     echo ""
                    
//                     echo "=== Agents Status ==="
//                     echo ""
                    
//                     def total = computers.size()
//                     def online = computers.count { !it.offline }
//                     def offline = total - online
//                     def idle = computers.count { it.idle }
                    
//                     echo "Total agents: ${total}"
//                     echo "Online: ${online}"
//                     echo "Offline: ${offline}"
//                     echo "Idle: ${idle}"
//                     echo ""
                    
//                     // Общая статистика по ресурсам
//                     def totalDiskSpace = 0
//                     def agentsWithDiskInfo = 0
//                     def totalSwapUsed = 0
//                     def totalSwapTotal = 0
//                     def agentsWithSwapInfo = 0
                    
//                     computers.each { comp ->
//                         if (!comp.offline && comp.monitorData) {
//                             if (comp.monitorData['hudson.node_monitors.DiskSpaceMonitor']) {
//                                 def diskSize = comp.monitorData['hudson.node_monitors.DiskSpaceMonitor'].size ?: 0
//                                 if (diskSize > 0) {
//                                     totalDiskSpace += diskSize
//                                     agentsWithDiskInfo++
//                                 }
//                             }
//                             if (comp.monitorData['hudson.node_monitors.SwapSpaceMonitor']) {
//                                 def swapMonitor = comp.monitorData['hudson.node_monitors.SwapSpaceMonitor']
//                                 def swapTotal = swapMonitor.swapTotal ?: 0
//                                 def swapAvailable = swapMonitor.swapAvailable ?: 0
//                                 if (swapTotal > 0) {
//                                     totalSwapTotal += swapTotal
//                                     totalSwapUsed += (swapTotal - swapAvailable)
//                                     agentsWithSwapInfo++
//                                 }
//                             }
//                         }
//                     }
                    
//                     if (agentsWithDiskInfo > 0) {
//                         def avgDiskGB = ((totalDiskSpace as Long) / agentsWithDiskInfo) / (1024.0 * 1024.0 * 1024.0)
//                         echo "📊 Average Free Disk Space: ${String.format("%.2f GB", avgDiskGB as Float)} (across ${agentsWithDiskInfo} agents)"
//                     }
//                     if (agentsWithSwapInfo > 0) {
//                         def avgSwapUsedGB = ((totalSwapUsed as Long) / agentsWithSwapInfo) / (1024.0 * 1024.0 * 1024.0)
//                         def avgSwapTotalGB = ((totalSwapTotal as Long) / agentsWithSwapInfo) / (1024.0 * 1024.0 * 1024.0)
//                         def avgSwapPercent = ((totalSwapUsed as Long) / (totalSwapTotal as Long)) * 100.0
//                         def swapUsedStr = String.format("%.2f GB", avgSwapUsedGB as Float)
//                         def swapTotalStr = String.format("%.2f GB", avgSwapTotalGB as Float)
//                         def swapPercentStr = String.format("%.1f", avgSwapPercent as Float)
//                         echo "📊 Average Swap Usage: ${swapUsedStr} / ${swapTotalStr} (${swapPercentStr}% used)"
//                     }
//                     echo ""
//                     echo "=" * 80
                    
//                     // Детальная информация по каждому агенту
//                     for (comp in computers) {
//                         def name = comp.displayName ?: 'Unknown'
//                         // offline может быть boolean или null, проверяем явно
//                         def isOffline = (comp.offline == true) ? true : false
//                         def offlineReason = comp.offlineCauseReason ?: ''
//                         def numExecutors = comp.numExecutors ?: 0
//                         def description = comp.description ?: ''
//                         def isIdle = (comp.idle == true) ? true : false
//                         def executors = comp.executors ?: []
                        
//                         def status = isOffline ? "🔴 OFFLINE" : "🟢 ONLINE"
//                         def idleStatus = (isIdle && !isOffline) ? " (IDLE)" : ""
                        
//                         echo ""
//                         echo "Agent: ${name}"
//                         echo "  Status: ${status}${idleStatus}"
//                         echo "  Executors: ${numExecutors}"
//                         if (description) {
//                             echo "  Description: ${description}"
//                         }
//                         if (isOffline && offlineReason) {
//                             echo "  Offline reason: ${offlineReason}"
//                         }
                        
//                         // Проверяем активные задачи
//                         def activeTasks = executors.findAll { it.progressExecutable }
//                         if (activeTasks) {
//                             echo "  Active tasks: ${activeTasks.size()}"
//                             activeTasks.each { task ->
//                                 def taskUrl = task.progressExecutable.url ?: ''
//                                 if (taskUrl) {
//                                     echo "    - ${taskUrl}"
//                                 }
//                             }
//                         }
                        
//                         // Информация о ресурсах из monitorData
//                         if (!isOffline && comp.monitorData) {
//                             def monitorData = comp.monitorData
                            
//                             // Disk Space Monitor
//                             if (monitorData['hudson.node_monitors.DiskSpaceMonitor']) {
//                                 def diskMonitor = monitorData['hudson.node_monitors.DiskSpaceMonitor']
//                                 def size = diskMonitor.size ?: 0
//                                 if (size > 0) {
//                                     def sizeGB = (size as Long) / (1024.0 * 1024.0 * 1024.0)
//                                     def sizeMB = (size as Long) / (1024.0 * 1024.0)
//                                     def sizeStr = sizeGB >= 1 ? String.format("%.2f GB", sizeGB as Float) : String.format("%.2f MB", sizeMB as Float)
//                                     echo "  💾 Free Disk Space: ${sizeStr}"
//                                 }
//                             }
                            
//                             // Temporary Space Monitor
//                             if (monitorData['hudson.node_monitors.TemporarySpaceMonitor']) {
//                                 def tmpMonitor = monitorData['hudson.node_monitors.TemporarySpaceMonitor']
//                                 def size = tmpMonitor.size ?: 0
//                                 if (size > 0) {
//                                     def sizeGB = (size as Long) / (1024.0 * 1024.0 * 1024.0)
//                                     def sizeMB = (size as Long) / (1024.0 * 1024.0)
//                                     def sizeStr = sizeGB >= 1 ? String.format("%.2f GB", sizeGB as Float) : String.format("%.2f MB", sizeMB as Float)
//                                     echo "  📁 Free Temp Space: ${sizeStr}"
//                                 }
//                             }
                            
//                                 // Swap Space Monitor
//                                 if (monitorData['hudson.node_monitors.SwapSpaceMonitor']) {
//                                     def swapMonitor = monitorData['hudson.node_monitors.SwapSpaceMonitor']
//                                     def swapAvailable = swapMonitor.swapAvailable ?: 0
//                                     def swapTotal = swapMonitor.swapTotal ?: 0
//                                     if (swapTotal > 0) {
//                                         def swapUsed = swapTotal - swapAvailable
//                                         def swapUsedGB = (swapUsed as Long) / (1024.0 * 1024.0 * 1024.0)
//                                         def swapTotalGB = (swapTotal as Long) / (1024.0 * 1024.0 * 1024.0)
//                                         def swapPercent = ((swapUsed as Long) / (swapTotal as Long)) * 100.0
//                                         echo "  🔄 Swap: ${String.format("%.2f GB", swapUsedGB as Float)} / ${String.format("%.2f GB", swapTotalGB as Float)} (${String.format("%.1f", swapPercent as Float)}% used)"
//                                     }
//                                 }
                            
//                             // Response Time Monitor
//                             if (monitorData['hudson.node_monitors.ResponseTimeMonitor']) {
//                                 def responseMonitor = monitorData['hudson.node_monitors.ResponseTimeMonitor']
//                                 def average = responseMonitor.average ?: 0
//                                 if (average > 0) {
//                                     // Преобразуем в float для форматирования (используем безопасный способ для sandbox)
//                                     // average уже число, просто приводим к Float
//                                     def averageFloat = average as Float
//                                     echo "  ⏱️  Average Response Time: ${String.format("%.2f", averageFloat)} ms"
//                                 }
//                             }
                            
//                             // Architecture Monitor (может быть недоступен в некоторых версиях Jenkins)
//                             // Убираем эту проверку, так как ArchitectureMonitor не предоставляет поле architecture напрямую
//                             // Архитектуру можно получить через systemInfo, но это требует дополнительных запросов
//                         }
                        
//                         echo "-" * 80
//                     }
                    
//                     echo ""
//                     echo "=========================================="
//                     echo "Monitoring completed"
//                     echo "=========================================="
                    
//                     // Проверка на проблемы
//                     if (offline > 0) {
//                         echo "⚠️  WARNING: ${offline} agent(s) are offline!"
//                     }
//                     if (online == 0 && total > 0) {
//                         error("❌ ERROR: All agents are offline!")
//                     }
//                 }
//             }
//         }
//     }
    
//     post {
//         always {
//             echo "Monitoring job completed"
//         }
//         success {
//             echo "✅ All agents are healthy"
//         }
//         failure {
//             echo "❌ Monitoring detected issues"
//         }
//     }
// }
