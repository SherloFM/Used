pipeline {
    agent { label 'used-ec2' }
    options {
        disableConcurrentBuilds()
        skipStagesAfterUnstable()
        skipDefaultCheckout(true)
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '2'))
    }
    triggers { pollSCM('H/5 * * * *') }
    environment {
        JAVA_HOME = '/opt/used/java17'
        MAVEN_OPTS = '-Xms64m -Xmx384m'
    }
    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
            }
        }
        stage('Build and isolated service tests') {
            steps {
                // Explicitly exclude the currently unproven Selenium runner.
                sh '''
                    export PATH="$JAVA_HOME/bin:$PATH"
                    bash mvnw -B -ntp -pl '!e2e-tests' -DargLine=-Xmx384m clean verify
                '''
            }
            post {
                always {
                    junit testResults: 'services/*/target/surefire-reports/TEST-*.xml', allowEmptyResults: false
                }
            }
        }
        stage('Tracked secret check') {
            steps {
                sh 'bash deploy/ci/check-tracked-secrets.sh'
            }
        }
        stage('PostgreSQL concurrency tests') {
            steps {
                // Runs separately because it starts a disposable PostgreSQL
                // container and must never point at the production database.
                sh '''
                    export PATH="$JAVA_HOME/bin:$PATH"
                    bash mvnw -B -ntp -pl services/marketplace-service \
                        -Dtest=PostgresConcurrencyIT test
                '''
            }
            post {
                always {
                    junit testResults: 'services/marketplace-service/target/surefire-reports/TEST-*PostgresConcurrencyIT.xml', allowEmptyResults: false
                }
            }
        }
        stage('Archive JARs') {
            steps {
                archiveArtifacts artifacts: 'services/*/target/*.jar', fingerprint: true
            }
        }
        stage('Deploy and health checks') {
            // Configure this as a single Pipeline-from-SCM job for the trusted
            // deployment branch only. Do not run pull requests on this node.
            steps {
                sh 'bash deploy/ec2/deploy.sh "build-${BUILD_NUMBER}-$(git rev-parse --short HEAD)"'
            }
        }
    }
}
