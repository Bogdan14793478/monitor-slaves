pipeline {
    agent any
    
    stages {
        stage('Checkout') {
            steps {
                echo "Checking out code..."
                checkout scm
            }
        }
        
        stage('Monitor Slaves') {
            steps {
                echo "Моніторинг Jenkins агентів"
                echo "Branch: ${env.BRANCH_NAME}"
                echo "Build: ${env.BUILD_NUMBER}"
                sh '''
                    echo "Checking Jenkins agents status..."
                    echo "This is a placeholder for monitoring logic"
                '''
            }
        }
    }
    
    post {
        always {
            echo "Pipeline completed for branch: ${env.BRANCH_NAME}"
        }
    }
}

