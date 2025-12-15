pipeline {
    agent none

    stages {
        stage('Checkout') {
            agent { label 'imperator' }
            steps {
                checkout scm
            }
        }

        stage('Monitor Slaves') {
            agent { label 'imperator' }
            steps {
                sh 'echo "Running on docker agent"'
            }
        }
    }
}

