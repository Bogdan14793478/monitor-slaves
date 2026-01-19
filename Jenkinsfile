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
                        def result = sh(
                            script: """
                                curl -s --connect-timeout 5 --max-time 10 -u admin:admin123 '${url}/computer/api/json?tree=computer[displayName,offline,offlineCauseReason,executors[progressExecutable[url]],numExecutors,description,idle]' 2>&1
                            """,
                            returnStdout: true
                        ).trim()
                        
                        // Проверяем, что это валидный JSON
                        if (result && result.startsWith("{") && !result.contains("curl:") && !result.contains("Could not resolve") && !result.contains("Connection refused") && !result.contains("timeout")) {
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
                                echo "Response preview: ${result.take(200)}"
                            }
                        } else {
                            echo "❌ Failed to connect to: ${url}"
                            if (result.length() > 0) {
                                echo "Response: ${result.take(200)}"
                            }
                        }
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
