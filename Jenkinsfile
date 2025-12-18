pipeline {
    agent none

    stages {
        stage('Checkout') {
            agent any
            steps {
                checkout scm
            }
        }

        stage('Monitor Slaves') {
            agent any
            steps {
                sh 'echo "Running on docker agent"'
            }
        }
    }
}

