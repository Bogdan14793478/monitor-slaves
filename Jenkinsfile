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
                sh 'echo "Running on docker agent"'
            }
        }
    }
}

