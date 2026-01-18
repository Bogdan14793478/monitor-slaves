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
                    def jenkinsUrl = env.JENKINS_URL ?: 'http://localhost:8080'
                    def apiUrl = "${jenkinsUrl}/computer/api/json?tree=computer[displayName,offline,offlineCauseReason,executors[progressExecutable[url]],numExecutors,description,idle]"
                    
                    // Используем curl для получения данных (работает без дополнительных плагинов)
                    def agentsJson = sh(
                        script: """
                            curl -s -u admin:admin123 '${apiUrl}' || echo '{"computer":[]}'
                        """,
                        returnStdout: true
                    ).trim()
                    
                    echo "Raw JSON response: ${agentsJson}"
                    
                    // Парсим JSON используя встроенный Groovy JsonSlurper с @NonCPS
                    // @NonCPS нужен для обхода sandbox ограничений Jenkins
                    @NonCPS
                    def parseJson(String json) {
                        return new groovy.json.JsonSlurper().parseText(json)
                    }
                    
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
