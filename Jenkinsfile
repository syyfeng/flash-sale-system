pipeline {
    agent any

    // 1. Tool Configuration: Ensure Maven is installed/available
    tools {
        maven 'Maven3' 
    }

    // 2. Environment Variables
    environment {
        DOCKER_HUB_USER = 'sfeng42'
        DOCKER_CREDS_ID = 'docker-hub-credentials'
        IMAGE_TAG = "v${BUILD_NUMBER}"
        PATH = "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${env.PATH}"
    }

    stages {

        // ──────────────────────────────────────────────
        // Stage 1: Build – Compile and package JARs
        // ──────────────────────────────────────────────
        stage('Build') {
            steps {
                // -B: Batch mode (no colors/logs for transfer)
                // -DskipTests: Speeds up the build for deployment
                sh 'mvn clean package -DskipTests -B'
            }
        }

        // ──────────────────────────────────────────────
        // Stage 2: Docker – Build images and push to Hub
        // ──────────────────────────────────────────────
        stage('Docker') {
            steps {
                script {
                    // Define the list of services and their specific Dockerfile paths
                    def services = [
                        [name: 'flash-sale-discovery',  path: 'flash-sale-discovery'],
                        [name: 'flash-sale-gateway',    path: 'flash-sale-gateway'],
                        [name: 'flash-sale-order',      path: 'flash-sale-order'],
                        [name: 'flash-sale-inventory',  path: 'flash-sale-inventory']
                    ]

                    // Securely inject Docker Hub credentials
                    withCredentials([usernamePassword(credentialsId: DOCKER_CREDS_ID, usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        
                        // 1. Log in to Docker Hub
                        sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'

                        // 2. Loop through services to build and push
                        services.each { svc ->
                            def imageName = "${DOCKER_HUB_USER}/${svc.name}"
                            def imageWithTag = "${imageName}:${IMAGE_TAG}"
                            def imageLatest = "${imageName}:latest"

                            echo "🐳 Building and Pushing: ${svc.name}"

                            // Build the image (using the module directory as context)
                            sh "docker build -t ${imageWithTag} -f ${svc.path}/Dockerfile ."
                            sh "docker push ${imageWithTag}"
                            sh "docker tag ${imageWithTag} ${imageLatest}"
                            sh "docker push ${imageLatest}"
                        }
                    }
                }
            }
        }

        // ──────────────────────────────────────────────
        // Stage 3: Deploy – Update Kubernetes Cluster
        // ──────────────────────────────────────────────
        stage('Deploy') {
            steps {
                // Apply all YAML configurations in the k8s folder
                sh 'kubectl apply -f k8s/'

                // Force a rolling restart to ensure pods pull the new 'latest' image
                sh 'kubectl rollout restart deployment/flash-sale-discovery'
                sh 'kubectl rollout restart deployment/flash-sale-gateway'
                sh 'kubectl rollout restart deployment/flash-sale-order'
                sh 'kubectl rollout restart deployment/flash-sale-inventory'

                // Wait for the rollout to complete to ensure stability
                // Increased timeout for Order/Inventory as they are heavier
                sh 'kubectl rollout status deployment/flash-sale-discovery  --timeout=300s'
                sh 'kubectl rollout status deployment/flash-sale-gateway    --timeout=300s'
                sh 'kubectl rollout status deployment/flash-sale-order      --timeout=300s'
                sh 'kubectl rollout status deployment/flash-sale-inventory  --timeout=300s'
            }
        }
    }

    // Post-build actions
    post {
        success {
            echo '✅ Pipeline completed successfully – All services are live on Kubernetes.'
        }
        failure {
            echo '❌ Pipeline failed – Please check the logs above for errors.'
        }
    }
}