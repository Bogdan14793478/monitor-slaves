pipeline {
    agent { label 'jenkins-slave' }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Monitor Slaves') {
            steps {
                sh 'echo "Running on docker agent"'
            }
        }
    }
}

