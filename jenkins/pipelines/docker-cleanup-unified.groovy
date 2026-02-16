pipeline {
    agent any

    environment {
        // Imagens que NUNCA devem ser apagadas
        // Inclui todas as imagens jenkins/* e imagens custom geradas manualmente
        PROTECTED_IMAGES = 'jenkins[/\\_-].*|jenkins:.*|maven:.*|openjdk:.*|node:.*|python:.*'

        CONTAINER_RETENTION_HOURS = '168'   // 7 dias
        WORKSPACE_RETENTION_DAYS  = '7'
    }

    triggers {
        cron('0 2 * * 6')
    }

    stages {

        stage('Pre-Cleanup Report') {
            steps {
                sh '''
                    echo "════════════════════════════════════════"
                    echo "  PRE-CLEANUP  $(date)"
                    echo "════════════════════════════════════════"

                    echo "\n[Disco]"
                    docker system df

                    echo "\n[Imagens presentes]"
                    docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}"

                    echo "\n[Containers parados]"
                    docker ps -a --filter "status=exited" \
                        --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"
                '''
            }
        }

        stage('Safe Cleanup') {
            steps {
                sh '''
                    # 1. Imagens dangling (<none>:<none>) — seguro, nunca têm tag
                    echo "\n[1] Imagens dangling..."
                    docker image prune -f

                    # 2. Containers parados há mais de 7 dias
                    echo "\n[2] Containers parados > ${CONTAINER_RETENTION_HOURS}h..."
                    docker container prune -f --filter "until=${CONTAINER_RETENTION_HOURS}h"

                    # 3. Volumes dangling
                    echo "\n[3] Volumes dangling..."
                    docker volume prune -f

                    # 4. Networks não usadas
                    echo "\n[4] Networks não usadas..."
                    docker network prune -f

                    # 5. Build cache > 7 dias
                    echo "\n[5] Build cache antigo..."
                    docker builder prune -f --filter "until=${CONTAINER_RETENTION_HOURS}h"

                    # 6. Workspaces Jenkins antigos
                    echo "\n[6] Workspaces Jenkins > ${WORKSPACE_RETENTION_DAYS} dias..."
                    find /var/jenkins_home/workspace -maxdepth 1 -type d \
                        -mtime +${WORKSPACE_RETENTION_DAYS} \
                        -exec rm -rf {} + 2>/dev/null || true
                '''
            }
        }

        stage('Verify Protected Images') {
            steps {
                script {
                    def missing = sh(
                        script: '''
                            docker images --format "{{.Repository}}:{{.Tag}}" \
                                | grep -E "${PROTECTED_IMAGES}" \
                                | wc -l
                        ''',
                        returnStdout: true
                    ).trim().toInteger()

                    sh '''
                        echo "\n[Imagens protegidas ainda presentes]"
                        docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" \
                            | grep -E "${PROTECTED_IMAGES}" || echo "AVISO: nenhuma imagem protegida encontrada"
                    '''

                    if (missing == 0) {
                        unstable(message: "Nenhuma imagem protegida encontrada após cleanup")
                    }
                }
            }
        }

        stage('Post-Cleanup Report') {
            steps {
                sh '''
                    echo "\n════════════════════════════════════════"
                    echo "  POS-CLEANUP  $(date)"
                    echo "════════════════════════════════════════"
                    docker system df

                    echo "\n[Jenkins home]"
                    du -sh /var/jenkins_home 2>/dev/null || true
                '''
            }
        }
    }

    post {
        always {
            sh '''
                mkdir -p /var/jenkins_home/cleanup-reports
                REPORT="/var/jenkins_home/cleanup-reports/cleanup-$(date +%Y%m%d-%H%M%S).log"
                docker system df > "$REPORT"
                docker images --format "{{.Repository}}:{{.Tag}}\t{{.Size}}" >> "$REPORT"
                find /var/jenkins_home/cleanup-reports -type f -name "cleanup-*.log" \
                    | sort -r | tail -n +31 | xargs rm -f 2>/dev/null || true
            '''
        }
        success {
            echo "Cleanup concluido com sucesso"
        }
        unstable {
            echo "AVISO: verificar imagens protegidas"
        }
        failure {
            echo "FALHA no cleanup - verificar logs"
        }
    }
}
