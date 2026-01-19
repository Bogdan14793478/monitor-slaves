// Функция для получения информации об агентах через Jenkins API
// Используем @NonCPS для обхода sandbox ограничений
@NonCPS
def getAgentsInfo() {
    def computers = []
    def jenkins = jenkins.model.Jenkins.getInstance()
    
    // Получаем все компьютеры (агенты + master)
    def allComputers = jenkins.getComputers()
    
    // Преобразуем в список для обработки
    for (computer in allComputers) {
        def compInfo = [:]
        compInfo.displayName = computer.displayName ?: 'Unknown'
        compInfo.offline = computer.isOffline()
        compInfo.numExecutors = computer.numExecutors
        compInfo.description = computer.node?.nodeDescription ?: ''
        compInfo.idle = computer.isIdle()
        
        // Получаем причину offline статуса
        if (compInfo.offline) {
            def offlineCause = computer.getOfflineCause()
            compInfo.offlineCauseReason = offlineCause ? offlineCause.toString() : ''
        } else {
            compInfo.offlineCauseReason = ''
        }
        
        // Получаем информацию об executors и активных задачах
        def executorsList = []
        def executors = computer.executors
        for (executor in executors) {
            def execInfo = [:]
            def executable = executor.currentExecutable
            if (executable) {
                execInfo.progressExecutable = [:]
                execInfo.progressExecutable.url = executable.url ?: ''
            }
            executorsList.add(execInfo)
        }
        compInfo.executors = executorsList
        
        computers.add(compInfo)
    }
    
    return computers
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
                    
                    // Используем встроенные Groovy API Jenkins для получения информации об агентах
                    // Это работает без curl и не зависит от сетевой доступности
                    echo "Using Jenkins Groovy API to get agent information..."
                    
                    def computers = getAgentsInfo()
                    
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
