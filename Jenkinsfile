// Функция для парсинга JSON (должна быть вне pipeline блока)
@NonCPS
def parseJson(String json) {
    return new groovy.json.JsonSlurper().parseText(json)
}

pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Monitor Slaves') {
            steps {
                script {
                    echo "=== Jenkins Agents Monitoring ==="
                    echo "Time: ${new Date()}"
                    echo ""
                    
                    // Получаем информацию о всех агентах через Jenkins REST API
                    // Используем curl, так как Groovy API требует разрешений sandbox
                    def jenkinsUrl = null
                    def agentsJson = null
                    
                    // Пробуем определить URL Jenkins
                    // Из конфигурации Docker Cloud: jenkinsUrl: "http://192.168.64.1:8080"
                    def urlsToTry = []
                    
                    // Добавляем URL из переменной окружения (если есть)
                    if (env.JENKINS_URL) {
                        urlsToTry.add(env.JENKINS_URL)
                        echo "Added JENKINS_URL from env: ${env.JENKINS_URL}"
                    }
                    
                    // Добавляем стандартные варианты (из конфигурации)
                    urlsToTry.addAll([
                        'http://192.168.64.1:8080',       // IP хоста (из jenkins.yaml)
                        'http://jenkins:8080',             // Имя контейнера (если в той же сети)
                        'http://192.168.97.2:8080',       // IP Jenkins в monitoring-network
                        'http://localhost:8080'            // Fallback
                    ])
                    
                    echo "URLs to try: ${urlsToTry}"
                    echo ""
                    
                    // Пробуем подключиться к каждому URL
                    for (url in urlsToTry) {
                        echo "Trying URL: ${url}"
                        
                        // Пробуем получить данные через API
                        // Используем простой запрос без tree параметра (может вызывать проблемы)
                        def result = sh(
                            script: """
                                curl -s --connect-timeout 5 --max-time 10 -u admin:admin123 '${url}/computer/api/json' 2>&1 || echo "CURL_ERROR"
                            """,
                            returnStdout: true
                        ).trim()
                        
                        echo "Response length: ${result.length()}"
                        echo "Response preview (first 500 chars): ${result.take(500)}"
                        
                        // Проверяем, что это валидный JSON
                        if (result && result != "CURL_ERROR" && result.startsWith("{") && !result.contains("curl:") && !result.contains("Could not resolve") && !result.contains("Connection refused") && !result.contains("timeout") && !result.contains("Connection timed out")) {
                            try {
                                // Пробуем распарсить JSON
                                def testParse = parseJson(result)
                                agentsJson = result
                                jenkinsUrl = url
                                echo "✅ Successfully connected to Jenkins at: ${jenkinsUrl}"
                                break
                            } catch (Exception e) {
                                echo "❌ Invalid JSON response from: ${url}"
                                echo "Error: ${e.message}"
                                echo "Response preview: ${result.take(500)}"
                            }
                        } else {
                            echo "❌ Failed to connect to: ${url}"
                            if (result && result.length() > 0) {
                                echo "Full response: ${result}"
                            } else {
                                echo "Empty response or connection error"
                            }
                        }
                        echo ""
                    }
                    
                    if (!agentsJson) {
                        error("❌ ERROR: Failed to connect to Jenkins API from any URL")
                    }
                    
                    // Парсим JSON
                    def agents = parseJson(agentsJson)
                    def computers = agents.computer ?: []
                    
                    echo "Found ${computers.size()} computer(s) in Jenkins"
                    echo ""
                    
                    echo "=== Agents Status ==="
                    echo ""
                    
                    def total = computers.size()
                    def online = computers.count { !it.offline }
                    def offline = total - online
                    def idle = computers.count { it.idle }
                    
                    echo "Total agents: ${total}"
                    echo "Online: ${online}"
                    echo "Offline: ${offline}"
                    echo "Idle: ${idle}"
                    echo ""
                    
                    // Общая статистика по ресурсам
                    def totalDiskSpace = 0
                    def agentsWithDiskInfo = 0
                    def totalSwapUsed = 0
                    def totalSwapTotal = 0
                    def agentsWithSwapInfo = 0
                    
                    computers.each { comp ->
                        if (!comp.offline && comp.monitorData) {
                            if (comp.monitorData['hudson.node_monitors.DiskSpaceMonitor']) {
                                def diskSize = comp.monitorData['hudson.node_monitors.DiskSpaceMonitor'].size ?: 0
                                if (diskSize > 0) {
                                    totalDiskSpace += diskSize
                                    agentsWithDiskInfo++
                                }
                            }
                            if (comp.monitorData['hudson.node_monitors.SwapSpaceMonitor']) {
                                def swapMonitor = comp.monitorData['hudson.node_monitors.SwapSpaceMonitor']
                                def swapTotal = swapMonitor.swapTotal ?: 0
                                def swapAvailable = swapMonitor.swapAvailable ?: 0
                                if (swapTotal > 0) {
                                    totalSwapTotal += swapTotal
                                    totalSwapUsed += (swapTotal - swapAvailable)
                                    agentsWithSwapInfo++
                                }
                            }
                        }
                    }
                    
                    if (agentsWithDiskInfo > 0) {
                        def avgDiskGB = (totalDiskSpace / agentsWithDiskInfo) / (1024 * 1024 * 1024)
                        echo "📊 Average Free Disk Space: ${String.format("%.2f GB", avgDiskGB)} (across ${agentsWithDiskInfo} agents)"
                    }
                    if (agentsWithSwapInfo > 0) {
                        def avgSwapUsedGB = (totalSwapUsed / agentsWithSwapInfo) / (1024 * 1024 * 1024)
                        def avgSwapTotalGB = (totalSwapTotal / agentsWithSwapInfo) / (1024 * 1024 * 1024)
                        def avgSwapPercent = (totalSwapUsed / totalSwapTotal) * 100
                        echo "📊 Average Swap Usage: ${String.format("%.2f GB", avgSwapUsedGB)} / ${String.format("%.2f GB", avgSwapTotalGB)} (${String.format("%.1f", avgSwapPercent)}% used)"
                    }
                    echo ""
                    echo "=" * 80
                    
                    // Детальная информация по каждому агенту
                    for (comp in computers) {
                        def name = comp.displayName ?: 'Unknown'
                        // offline может быть boolean или null, проверяем явно
                        def isOffline = (comp.offline == true) ? true : false
                        def offlineReason = comp.offlineCauseReason ?: ''
                        def numExecutors = comp.numExecutors ?: 0
                        def description = comp.description ?: ''
                        def isIdle = (comp.idle == true) ? true : false
                        def executors = comp.executors ?: []
                        
                        def status = isOffline ? "🔴 OFFLINE" : "🟢 ONLINE"
                        def idleStatus = (isIdle && !isOffline) ? " (IDLE)" : ""
                        
                        echo ""
                        echo "Agent: ${name}"
                        echo "  Status: ${status}${idleStatus}"
                        echo "  Executors: ${numExecutors}"
                        if (description) {
                            echo "  Description: ${description}"
                        }
                        if (isOffline && offlineReason) {
                            echo "  Offline reason: ${offlineReason}"
                        }
                        
                        // Проверяем активные задачи
                        def activeTasks = executors.findAll { it.progressExecutable }
                        if (activeTasks) {
                            echo "  Active tasks: ${activeTasks.size()}"
                            activeTasks.each { task ->
                                def taskUrl = task.progressExecutable.url ?: ''
                                if (taskUrl) {
                                    echo "    - ${taskUrl}"
                                }
                            }
                        }
                        
                        // Информация о ресурсах из monitorData
                        if (!isOffline && comp.monitorData) {
                            def monitorData = comp.monitorData
                            
                            // Disk Space Monitor
                            if (monitorData['hudson.node_monitors.DiskSpaceMonitor']) {
                                def diskMonitor = monitorData['hudson.node_monitors.DiskSpaceMonitor']
                                def size = diskMonitor.size ?: 0
                                if (size > 0) {
                                    def sizeGB = size / (1024 * 1024 * 1024)
                                    def sizeMB = size / (1024 * 1024)
                                    def sizeStr = sizeGB >= 1 ? String.format("%.2f GB", sizeGB) : String.format("%.2f MB", sizeMB)
                                    echo "  💾 Free Disk Space: ${sizeStr}"
                                }
                            }
                            
                            // Temporary Space Monitor
                            if (monitorData['hudson.node_monitors.TemporarySpaceMonitor']) {
                                def tmpMonitor = monitorData['hudson.node_monitors.TemporarySpaceMonitor']
                                def size = tmpMonitor.size ?: 0
                                if (size > 0) {
                                    def sizeGB = size / (1024 * 1024 * 1024)
                                    def sizeMB = size / (1024 * 1024)
                                    def sizeStr = sizeGB >= 1 ? String.format("%.2f GB", sizeGB) : String.format("%.2f MB", sizeMB)
                                    echo "  📁 Free Temp Space: ${sizeStr}"
                                }
                            }
                            
                            // Swap Space Monitor
                            if (monitorData['hudson.node_monitors.SwapSpaceMonitor']) {
                                def swapMonitor = monitorData['hudson.node_monitors.SwapSpaceMonitor']
                                def swapAvailable = swapMonitor.swapAvailable ?: 0
                                def swapTotal = swapMonitor.swapTotal ?: 0
                                if (swapTotal > 0) {
                                    def swapUsed = swapTotal - swapAvailable
                                    def swapUsedGB = swapUsed / (1024 * 1024 * 1024)
                                    def swapTotalGB = swapTotal / (1024 * 1024 * 1024)
                                    def swapPercent = (swapUsed / swapTotal) * 100
                                    echo "  🔄 Swap: ${String.format("%.2f GB", swapUsedGB)} / ${String.format("%.2f GB", swapTotalGB)} (${String.format("%.1f", swapPercent)}% used)"
                                }
                            }
                            
                            // Response Time Monitor
                            if (monitorData['hudson.node_monitors.ResponseTimeMonitor']) {
                                def responseMonitor = monitorData['hudson.node_monitors.ResponseTimeMonitor']
                                def average = responseMonitor.average ?: 0
                                if (average > 0) {
                                    echo "  ⏱️  Average Response Time: ${String.format("%.2f", average)} ms"
                                }
                            }
                            
                            // Architecture Monitor
                            if (monitorData['hudson.node_monitors.ArchitectureMonitor']) {
                                def archMonitor = monitorData['hudson.node_monitors.ArchitectureMonitor']
                                def arch = archMonitor.architecture ?: 'Unknown'
                                echo "  🏗️  Architecture: ${arch}"
                            }
                        }
                        
                        echo "-" * 80
                    }
                    
                    echo ""
                    echo "=========================================="
                    echo "Monitoring completed"
                    echo "=========================================="
                    
                    // Проверка на проблемы
                    if (offline > 0) {
                        echo "⚠️  WARNING: ${offline} agent(s) are offline!"
                    }
                    if (online == 0 && total > 0) {
                        error("❌ ERROR: All agents are offline!")
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo "Monitoring job completed"
        }
        success {
            echo "✅ All agents are healthy"
        }
        failure {
            echo "❌ Monitoring detected issues"
        }
    }
}
