// CI/CD pipeline for the Calculator API.
// Assumes a Linux Jenkins agent (e.g. Jenkins in Docker with /var/run/docker.sock
// mounted) that has: JDK 21, Maven, docker CLI and the docker compose plugin.
// See docs/JENKINS_SETUP.md.

pipeline {
    agent any

    environment {
        DOCKERHUB_USER   = 'akshat09128'      // TODO: set your Docker Hub username
        NOTIFY_EMAIL     = 'akshat.betala@gmail.com'       // TODO: set the notification address
        IMAGE_NAME       = "${DOCKERHUB_USER}/calculator-api"
        IMAGE_TAG        = "${BUILD_NUMBER}"
        COMPOSE_PROJECT_NAME = 'calculator'
        NGINX_PORT       = '80'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '15'))
        disableConcurrentBuilds()          // one deploy at a time keeps re-runs safe
        timeout(time: 30, unit: 'MINUTES')
    }

    triggers {
        githubPush()                       // GitHub webhook -> /github-webhook/
        // Fallback when the webhook can't reach Jenkins: poll every 5 minutes
        // pollSCM('H/5 * * * *')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                // A failing test makes mvn exit non-zero, which fails the stage and the build
                sh 'mvn -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build -t "$IMAGE_NAME:$IMAGE_TAG" -t "$IMAGE_NAME:latest" .
                    docker image ls "$IMAGE_NAME"
                '''
            }
        }

        stage('Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                  usernameVariable: 'DOCKERHUB_LOGIN',
                                                  passwordVariable: 'DOCKERHUB_TOKEN')]) {
                    // Single-quoted so Groovy never interpolates the secret
                    sh '''
                        echo "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_LOGIN" --password-stdin
                        docker push "$IMAGE_NAME:$IMAGE_TAG"
                        docker push "$IMAGE_NAME:latest"
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    docker compose build nginx
                    docker compose up -d --no-build --remove-orphans
                    docker compose ps
                '''
                // Fixed project name + no container_name in compose + --remove-orphans
                // means re-running simply rolls the stack forward to this build's image.
                sh '''
                    docker compose up -d --no-build --remove-orphans
                    docker compose ps
                '''
                // Poll health *through nginx* so both containers and the proxy route are checked
                sh '''
                    for i in $(seq 1 30); do
                        if docker compose exec -T nginx wget -qO- http://127.0.0.1/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
                            echo "Application is UP (attempt $i)"
                            exit 0
                        fi
                        echo "Waiting for application to become healthy ($i/30)..."
                        sleep 5
                    done
                    echo "Application did not become healthy in time"
                    docker compose logs --tail=100 calculator-app
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            emailext(
                to: "${NOTIFY_EMAIL}",
                subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Build succeeded and ${IMAGE_NAME}:${IMAGE_TAG} is deployed.

Job:    ${env.JOB_NAME}
Build:  #${env.BUILD_NUMBER}
URL:    ${env.BUILD_URL}
"""
            )
        }
        failure {
            emailext(
                to: "${NOTIFY_EMAIL}",
                subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Build failed.

Job:    ${env.JOB_NAME}
Build:  #${env.BUILD_NUMBER}
URL:    ${env.BUILD_URL}
Console: ${env.BUILD_URL}console
""",
                attachLog: true
            )
        }
    }
}
