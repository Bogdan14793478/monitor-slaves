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
                    echo "Jenkins URL: ${env.JENKINS_URL}"
                    echo "Time: ${new Date()}"
                    echo ""
                    
                    // Получаем информацию о всех агентах через Jenkins API
                    // Пробуем разные варианты URL для доступа к Jenkins master
                    def jenkinsUrl = null
                    def agentsJson = null
                    
                    // Вариант 1: Имя контейнера в Docker сети (если агенты в той же сети)
                    def urlsToTry = [
                        'http://jenkins:8080',
                        'http://192.168.64.1:8080',
                        env.JENKINS_URL ?: 'http://localhost:8080'
                    ]
                    
                    for (url in urlsToTry) {
                        echo "Trying URL: ${url}"
                        def testResult = sh(
                            script: """
                                curl -s -u admin:admin123 '${url}/computer/api/json?tree=computer[displayName,offline,offlineCauseReason,executors[progressExecutable[url]],numExecutors,description,idle]' 2>&1 || echo 'ERROR'
                            """,
                            returnStdout: true
                        ).trim()
                        
                        echo "Response preview: ${testResult.take(200)}"
                        
                        if (testResult && testResult != 'ERROR' && !testResult.contains("curl:") && !testResult.contains("Could not resolve") && testResult.startsWith("{")) {
                            agentsJson = testResult
                            jenkinsUrl = url
                            echo "✅ Successfully connected to Jenkins at: ${jenkinsUrl}"
                            break
                        } else {
                            echo "❌ Failed to connect to: ${url}"
                            if (testResult.length() > 0) {
                                echo "Response: ${testResult.take(200)}"
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
