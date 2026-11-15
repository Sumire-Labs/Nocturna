#!/usr/bin/env groovy
pipeline {
    agent any
    tools { jdk "jdk-25" }
    stages {
        stage('Build and test') {
            steps { sh './gradlew clean build --no-daemon' }
        }
    }
    post { success { archiveArtifacts artifacts: 'build/libs/**/*.jar', fingerprint: true } }
}
