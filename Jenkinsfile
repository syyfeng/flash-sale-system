pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'your-registry.com'   // TODO: replace with your Docker Hub / private registry
        IMAGE_TAG       = "${env.BUILD_NUMBER ?: 'latest'}"
    }

    stages {

        // ──────────────────────────────────────────────
        // 1. Build – compile all Maven modules
        // ──────────────────────────────────────────────
        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests -B'
            }
        }

        // ──────────────────────────────────────────────
        // 2. Docker – build & push images
        // ──────────────────────────────────────────────
        stage('Docker') {
            steps {
                script {
                    def services = [
                        [name: 'flash-sale-discovery',  dockerfile: 'flash-sale-discovery/Dockerfile'],
                        [name: 'flash-sale-gateway',    dockerfile: 'flash-sale-gateway/Dockerfile'],
                        [name: 'flash-sale-order',      dockerfile: 'flash-sale-order/Dockerfile'],
                        [name: 'flash-sale-inventory',  dockerfile: 'flash-sale-inventory/Dockerfile'],
                    ]

                    for (svc in services) {
                        def image = "${DOCKER_REGISTRY}/${svc.name}:${IMAGE_TAG}"

                        sh "docker build -t ${image} -f ${svc.dockerfile} ."
                        sh "docker push ${image}"

                        // Also tag as 'latest'
                        sh "docker tag ${image} ${DOCKER_REGISTRY}/${svc.name}:latest"
                        sh "docker push ${DOCKER_REGISTRY}/${svc.name}:latest"
                    }
                }
            }
        }

        // ──────────────────────────────────────────────
        // 3. Deploy – roll out to Kubernetes
        // ──────────────────────────────────────────────
        stage('Deploy') {
            steps {
                sh 'kubectl apply -f k8s/'

                // Rolling restart to pull the latest images
                sh 'kubectl rollout restart deployment/flash-sale-discovery'
                sh 'kubectl rollout restart deployment/flash-sale-gateway'
                sh 'kubectl rollout restart deployment/flash-sale-order'
                sh 'kubectl rollout restart deployment/flash-sale-inventory'

                // Wait for rollouts to finish
                sh 'kubectl rollout status deployment/flash-sale-discovery  --timeout=120s'
                sh 'kubectl rollout status deployment/flash-sale-gateway    --timeout=120s'
                sh 'kubectl rollout status deployment/flash-sale-order      --timeout=180s'
                sh 'kubectl rollout status deployment/flash-sale-inventory  --timeout=180s'
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline completed – all services deployed to Kubernetes.'
        }
        failure {
            echo '❌ Pipeline failed – check the logs above.'
        }
    }
}
