// Скриптовый Jenkinsfile для мониторинга агентов через JavaMelody API
// Использует JavaMelody Monitoring Plugin напрямую
// ТРЕБУЕТСЯ: Одобрить скрипты в Script Approval (Manage Jenkins -> In-process Script Approval)

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
                    def cpuPercentStr = String.format("%.2f%%", cpuPercent)
                    echo "  System CPU Load: ${cpuPercentStr}"
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
                    def usedMemoryStr = usedMemory < 1024 ? "${usedMemory} B" : 
                        (usedMemory < 1024 * 1024 ? String.format("%.2f KB", usedMemory / 1024.0) :
                        (usedMemory < 1024 * 1024 * 1024 ? String.format("%.2f MB", usedMemory / (1024.0 * 1024.0)) :
                        String.format("%.2f GB", usedMemory / (1024.0 * 1024.0 * 1024.0))))
                    def maxMemoryStr = maxMemory < 1024 ? "${maxMemory} B" : 
                        (maxMemory < 1024 * 1024 ? String.format("%.2f KB", maxMemory / 1024.0) :
                        (maxMemory < 1024 * 1024 * 1024 ? String.format("%.2f MB", maxMemory / (1024.0 * 1024.0)) :
                        String.format("%.2f GB", maxMemory / (1024.0 * 1024.0 * 1024.0))))
                    echo "    Used Memory: ${usedMemoryStr}"
                    echo "    Max Memory: ${maxMemoryStr}"
                    
                    if (maxMemory > 0) {
                        def memoryPercent = (usedMemory / maxMemory) * 100.0
                        def memoryPercentStr = String.format("%.2f%%", memoryPercent)
                        echo "    Memory Usage: ${memoryPercentStr}"
                    }
                    
                    if (usedPermGen > 0) {
                        def usedPermGenStr = usedPermGen < 1024 ? "${usedPermGen} B" : 
                            (usedPermGen < 1024 * 1024 ? String.format("%.2f KB", usedPermGen / 1024.0) :
                            (usedPermGen < 1024 * 1024 * 1024 ? String.format("%.2f MB", usedPermGen / (1024.0 * 1024.0)) :
                            String.format("%.2f GB", usedPermGen / (1024.0 * 1024.0 * 1024.0))))
                        echo "    Used Perm Gen: ${usedPermGenStr}"
                    }
                    if (maxPermGen > 0) {
                        def maxPermGenStr = maxPermGen < 1024 ? "${maxPermGen} B" : 
                            (maxPermGen < 1024 * 1024 ? String.format("%.2f KB", maxPermGen / 1024.0) :
                            (maxPermGen < 1024 * 1024 * 1024 ? String.format("%.2f MB", maxPermGen / (1024.0 * 1024.0)) :
                            String.format("%.2f GB", maxPermGen / (1024.0 * 1024.0 * 1024.0))))
                        echo "    Max Perm Gen: ${maxPermGenStr}"
                    }
                    if (usedNonHeap > 0) {
                        def usedNonHeapStr = usedNonHeap < 1024 ? "${usedNonHeap} B" : 
                            (usedNonHeap < 1024 * 1024 ? String.format("%.2f KB", usedNonHeap / 1024.0) :
                            (usedNonHeap < 1024 * 1024 * 1024 ? String.format("%.2f MB", usedNonHeap / (1024.0 * 1024.0)) :
                            String.format("%.2f GB", usedNonHeap / (1024.0 * 1024.0 * 1024.0))))
                        echo "    Used Non-Heap: ${usedNonHeapStr}"
                    }
                    if (usedPhysical > 0) {
                        def usedPhysicalStr = usedPhysical < 1024 ? "${usedPhysical} B" : 
                            (usedPhysical < 1024 * 1024 ? String.format("%.2f KB", usedPhysical / 1024.0) :
                            (usedPhysical < 1024 * 1024 * 1024 ? String.format("%.2f MB", usedPhysical / (1024.0 * 1024.0)) :
                            String.format("%.2f GB", usedPhysical / (1024.0 * 1024.0 * 1024.0))))
                        echo "    Used Physical Memory: ${usedPhysicalStr}"
                    }
                    if (usedSwap > 0) {
                        def usedSwapStr = usedSwap < 1024 ? "${usedSwap} B" : 
                            (usedSwap < 1024 * 1024 ? String.format("%.2f KB", usedSwap / 1024.0) :
                            (usedSwap < 1024 * 1024 * 1024 ? String.format("%.2f MB", usedSwap / (1024.0 * 1024.0)) :
                            String.format("%.2f GB", usedSwap / (1024.0 * 1024.0 * 1024.0))))
                        echo "    Used Swap Space: ${usedSwapStr}"
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
                def totalUsedMemoryStr = totalUsedMemory < 1024 ? "${totalUsedMemory} B" : 
                    (totalUsedMemory < 1024 * 1024 ? String.format("%.2f KB", totalUsedMemory / 1024.0) :
                    (totalUsedMemory < 1024 * 1024 * 1024 ? String.format("%.2f MB", totalUsedMemory / (1024.0 * 1024.0)) :
                    String.format("%.2f GB", totalUsedMemory / (1024.0 * 1024.0 * 1024.0))))
                def totalMaxMemoryStr = totalMaxMemory < 1024 ? "${totalMaxMemory} B" : 
                    (totalMaxMemory < 1024 * 1024 ? String.format("%.2f KB", totalMaxMemory / 1024.0) :
                    (totalMaxMemory < 1024 * 1024 * 1024 ? String.format("%.2f MB", totalMaxMemory / (1024.0 * 1024.0)) :
                    String.format("%.2f GB", totalMaxMemory / (1024.0 * 1024.0 * 1024.0))))
                def avgMemoryPercentStr = String.format("%.2f%%", avgMemoryPercent)
                echo "Total Used Memory: ${totalUsedMemoryStr}"
                echo "Total Max Memory: ${totalMaxMemoryStr}"
                echo "Average Memory Usage: ${avgMemoryPercentStr}"
            }
            
            if (nodesWithCpuInfo > 0) {
                def avgCpuLoad = (totalSystemCpuLoad / nodesWithCpuInfo) * 100.0
                def avgCpuLoadStr = String.format("%.2f%%", avgCpuLoad)
                echo "Average System CPU Load: ${avgCpuLoadStr}"
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
