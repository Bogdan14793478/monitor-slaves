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
                    echo "Jenkins URL env: ${env.JENKINS_URL}"
                    echo "Time: ${new Date()}"
                    echo ""
                    
                    // Пробуем определить URL Jenkins из переменных окружения агента
                    def agentJenkinsUrl = sh(
                        script: 'echo $JENKINS_URL 2>/dev/null || echo "NOT_SET"',
                        returnStdout: true
                    ).trim()
                    echo "Agent JENKINS_URL: ${agentJenkinsUrl}"
                    
                    // Пробуем определить hostname/IP агента
                    def agentHostname = sh(
                        script: 'hostname -I 2>/dev/null || hostname 2>/dev/null || echo "UNKNOWN"',
                        returnStdout: true
                    ).trim()
                    echo "Agent hostname/IP: ${agentHostname}"
                    echo ""
                    
                    // Получаем информацию о всех агентах через Jenkins API
                    // Пробуем разные варианты URL для доступа к Jenkins master
                    def jenkinsUrl = null
                    def agentsJson = null
                    
                    // Docker агенты запускаются на Multipass VM (192.168.64.14)
                    // Jenkins запущен на хосте, доступен по 192.168.64.1:8080
                    def urlsToTry = []
                    
                    // Добавляем URL из переменной окружения агента (если есть)
                    if (agentJenkinsUrl && agentJenkinsUrl != 'NOT_SET' && agentJenkinsUrl != '') {
                        urlsToTry.add(agentJenkinsUrl)
                        echo "Added agent JENKINS_URL: ${agentJenkinsUrl}"
                    }
                    
                    // Добавляем стандартные варианты
                    urlsToTry.addAll([
                        'http://192.168.64.1:8080',       // IP хоста (из конфигурации) - ПРИОРИТЕТ
                        'http://jenkins:8080',             // Имя контейнера (если в той же сети)
                        'http://192.168.97.2:8080',       // IP Jenkins в monitoring-network
                        'http://localhost:8080'            // Fallback
                    ])
                    
                    echo "URLs to try: ${urlsToTry}"
                    echo ""
                    
                    for (url in urlsToTry) {
                        echo "Trying URL: ${url}"
                        
                        // Сначала проверяем доступность хоста
                        def hostCheck = sh(
                            script: """
                                timeout 3 bash -c 'echo > /dev/tcp/${url.replaceAll("http://", "").replaceAll(":8080", "")}/8080' 2>&1 || echo "PORT_CLOSED"
                            """,
                            returnStdout: true
                        ).trim()
                        
                        if (hostCheck.contains("PORT_CLOSED") || hostCheck.contains("timeout")) {
                            echo "⚠️  Port 8080 not accessible on ${url}"
                        }
                        
                        // Пробуем подключиться с детальной диагностикой
                        def testResult = sh(
                            script: """
                                curl -s --connect-timeout 5 --max-time 10 -u admin:admin123 '${url}/computer/api/json?tree=computer[displayName,offline,offlineCauseReason,executors[progressExecutable[url]],numExecutors,description,idle]' 2>&1
                            """,
                            returnStdout: true
                        ).trim()
                        
                        echo "Response preview: ${testResult.take(300)}"
                        
                        // Проверяем, что это валидный JSON ответ
                        if (testResult && testResult.startsWith("{") && !testResult.contains("curl:") && !testResult.contains("Could not resolve") && !testResult.contains("Connection refused") && !testResult.contains("timeout")) {
                            try {
                                // Пробуем распарсить, чтобы убедиться что это валидный JSON
                                def testParse = parseJson(testResult)
                                agentsJson = testResult
                                jenkinsUrl = url
                                echo "✅ Successfully connected to Jenkins at: ${jenkinsUrl}"
                                break
                            } catch (Exception e) {
                                echo "❌ Invalid JSON response from: ${url}"
                                echo "Error: ${e.message}"
                                echo "Response: ${testResult.take(500)}"
                            }
                        } else {
                            echo "❌ Failed to connect to: ${url}"
                            if (testResult.length() > 0) {
                                echo "Response: ${testResult.take(500)}"
                            }
                        }
                    }
                    
                    if (!agentsJson) {
                        error("Failed to connect to Jenkins API from any URL")
                    }
                    
                    echo "Raw JSON response: ${agentsJson}"
                    
                    // Парсим JSON используя функцию parseJson (определена выше)
                    def agents = parseJson(agentsJson)
                    def computers = agents.computer ?: []
                    
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
                    echo "=" * 80
                    
                    // Детальная информация по каждому агенту
                    for (comp in computers) {
                        def name = comp.displayName ?: 'Unknown'
                        def isOffline = comp.offline ?: true
                        def offlineReason = comp.offlineCauseReason ?: ''
                        def numExecutors = comp.numExecutors ?: 0
                        def description = comp.description ?: ''
                        def isIdle = comp.idle ?: false
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
