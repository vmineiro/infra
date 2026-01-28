pipeline {
    agent any

    environment {
        DOCKER_API_VERSION = '1.41'

        // Imagens críticas que NUNCA devem ser apagadas (regex patterns)
        PROTECTED_IMAGES = 'jenkins/.*|maven:.*|openjdk:.*|node:.*|python:.*'

        // Dias de retenção para diferentes recursos
        CONTAINER_RETENTION_DAYS = '7'
        VOLUME_RETENTION_DAYS = '30'
        WORKSPACE_RETENTION_DAYS = '7'
    }

    triggers {
        // Executar todos os sábados às 2:00 AM
        cron('0 2 * * 6')
    }

    stages {
        stage('Pre-Cleanup Report') {
            steps {
                script {
                    echo "═══════════════════════════════════════════════════════"
                    echo "  RELATÓRIO PRÉ-CLEANUP - $(date)"
                    echo "═══════════════════════════════════════════════════════"

                    sh '''
                        echo "\n📊 ESPAÇO EM DISCO ATUAL:"
                        df -h /var/lib/docker

                        echo "\n📦 RESUMO DOCKER:"
                        docker system df

                        echo "\n🖼️  IMAGENS ATUAIS:"
                        docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"

                        echo "\n📋 CONTAINERS PARADOS (será removidos se > ${CONTAINER_RETENTION_DAYS} dias):"
                        docker ps -a --filter "status=exited" --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"

                        echo "\n🗑️  IMAGENS DANGLING (serão removidas):"
                        docker images -f "dangling=true" --format "table {{.ID}}\t{{.Repository}}\t{{.Tag}}\t{{.Size}}"

                        echo "\n🔌 VOLUMES NÃO USADOS:"
                        docker volume ls -qf "dangling=true" | wc -l
                        echo "volumes dangling encontrados"

                        echo "\n🌐 NETWORKS NÃO USADAS:"
                        docker network ls --filter "type=custom" --format "{{.Name}}"
                    '''
                }
            }
        }

        stage('Verify Protected Images') {
            steps {
                script {
                    echo "\n🛡️  VERIFICANDO IMAGENS PROTEGIDAS..."

                    sh '''
                        echo "Imagens que NUNCA serão apagadas (pattern: ${PROTECTED_IMAGES}):"
                        docker images --format "{{.Repository}}:{{.Tag}}" | grep -E "${PROTECTED_IMAGES}" || echo "⚠️  Nenhuma imagem protegida encontrada!"
                    '''
                }
            }
        }

        stage('Safe Docker Cleanup') {
            steps {
                script {
                    echo "\n🧹 INICIANDO LIMPEZA SEGURA..."

                    sh '''
                        # 1. Remover APENAS imagens dangling (sem tag)
                        echo "\n1️⃣  Removendo imagens dangling (<none>:<none>)..."
                        DANGLING_COUNT=$(docker images -qf "dangling=true" | wc -l)
                        if [ "$DANGLING_COUNT" -gt 0 ]; then
                            docker image prune -f
                            echo "✅ $DANGLING_COUNT imagens dangling removidas"
                        else
                            echo "✅ Nenhuma imagem dangling encontrada"
                        fi

                        # 2. Remover containers parados com mais de X dias
                        echo "\n2️⃣  Removendo containers parados (> ${CONTAINER_RETENTION_DAYS} dias)..."

                        # Listar containers parados antigos
                        OLD_CONTAINERS=$(docker ps -a --filter "status=exited" --format "{{.ID}} {{.Names}} {{.CreatedAt}}" | \
                            awk -v days=${CONTAINER_RETENTION_DAYS} '
                                {
                                    cmd = "date -d \"" $3 " " $4 "\" +%s 2>/dev/null || date -j -f \"%Y-%m-%d %H:%M:%S\" \"" $3 " " $4 "\" +%s 2>/dev/null"
                                    cmd | getline created
                                    close(cmd)

                                    cmd2 = "date +%s"
                                    cmd2 | getline now
                                    close(cmd2)

                                    age_days = (now - created) / 86400
                                    if (age_days > days) {
                                        print $1
                                    }
                                }
                            ')

                        if [ -n "$OLD_CONTAINERS" ]; then
                            echo "$OLD_CONTAINERS" | xargs -r docker rm -f
                            REMOVED_COUNT=$(echo "$OLD_CONTAINERS" | wc -l)
                            echo "✅ $REMOVED_COUNT containers antigos removidos"
                        else
                            echo "✅ Nenhum container antigo para remover"
                        fi

                        # 3. Remover imagens NÃO USADAS, EXCETO as protegidas
                        echo "\n3️⃣  Removendo imagens não usadas (EXCETO protegidas)..."

                        # Listar imagens não usadas que NÃO estão protegidas
                        UNUSED_IMAGES=$(docker images --format "{{.ID}} {{.Repository}}:{{.Tag}}" | \
                            grep -vE "${PROTECTED_IMAGES}" | \
                            while read id name; do
                                # Verificar se a imagem está sendo usada
                                IN_USE=$(docker ps -a --filter "ancestor=$id" --format "{{.ID}}" | wc -l)
                                if [ "$IN_USE" -eq 0 ]; then
                                    echo "$id"
                                fi
                            done)

                        if [ -n "$UNUSED_IMAGES" ]; then
                            echo "Imagens a serem removidas:"
                            echo "$UNUSED_IMAGES" | while read id; do
                                docker images --format "{{.Repository}}:{{.Tag}} ({{.Size}})" --filter "id=$id"
                            done

                            echo "$UNUSED_IMAGES" | xargs -r docker rmi -f
                            REMOVED_COUNT=$(echo "$UNUSED_IMAGES" | wc -l)
                            echo "✅ $REMOVED_COUNT imagens não usadas removidas"
                        else
                            echo "✅ Nenhuma imagem não usada para remover (ou todas são protegidas)"
                        fi

                        # 4. Remover volumes dangling antigos
                        echo "\n4️⃣  Removendo volumes dangling..."
                        VOLUME_COUNT=$(docker volume ls -qf "dangling=true" | wc -l)
                        if [ "$VOLUME_COUNT" -gt 0 ]; then
                            docker volume prune -f
                            echo "✅ Volumes dangling removidos"
                        else
                            echo "✅ Nenhum volume dangling encontrado"
                        fi

                        # 5. Remover networks não usadas (exceto default)
                        echo "\n5️⃣  Removendo networks não usadas..."
                        docker network prune -f
                        echo "✅ Networks não usadas removidas"

                        # 6. Limpar build cache (manter últimos 7 dias)
                        echo "\n6️⃣  Limpando build cache antigo..."
                        docker builder prune -f --filter "until=168h"
                        echo "✅ Build cache antigo removido"
                    '''
                }
            }
        }

        stage('Jenkins Workspace Cleanup') {
            steps {
                script {
                    echo "\n🗂️  LIMPANDO WORKSPACES ANTIGOS..."

                    sh '''
                        echo "Procurando workspaces com mais de ${WORKSPACE_RETENTION_DAYS} dias..."

                        # Listar antes de apagar
                        OLD_WORKSPACES=$(find /var/jenkins_home/workspace -maxdepth 1 -type d -mtime +${WORKSPACE_RETENTION_DAYS} 2>/dev/null || true)

                        if [ -n "$OLD_WORKSPACES" ]; then
                            echo "Workspaces a serem removidos:"
                            echo "$OLD_WORKSPACES"

                            find /var/jenkins_home/workspace -maxdepth 1 -type d -mtime +${WORKSPACE_RETENTION_DAYS} -exec rm -rf {} + 2>/dev/null || true
                            echo "✅ Workspaces antigos removidos"
                        else
                            echo "✅ Nenhum workspace antigo para remover"
                        fi
                    '''
                }
            }
        }

        stage('Post-Cleanup Report') {
            steps {
                script {
                    echo "\n═══════════════════════════════════════════════════════"
                    echo "  RELATÓRIO PÓS-CLEANUP - $(date)"
                    echo "═══════════════════════════════════════════════════════"

                    sh '''
                        echo "\n📊 ESPAÇO EM DISCO FINAL:"
                        df -h /var/lib/docker

                        echo "\n📦 RESUMO DOCKER FINAL:"
                        docker system df

                        echo "\n🖼️  IMAGENS RESTANTES:"
                        docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

                        echo "\n✅ IMAGENS PROTEGIDAS (ainda presentes):"
                        docker images --format "{{.Repository}}:{{.Tag}}" | grep -E "${PROTECTED_IMAGES}" || echo "⚠️  ALERTA: Imagens protegidas não encontradas!"
                    '''
                }
            }
        }

        stage('Health Check - Verify Agent Images') {
            steps {
                script {
                    echo "\n🏥 VERIFICAÇÃO DE SAÚDE - IMAGENS DE AGENTES"

                    def agentImagesCheck = sh(
                        script: '''
                            # Lista de imagens críticas para agentes Jenkins
                            REQUIRED_IMAGES="jenkins/agent jenkins/inbound-agent jenkins/ssh-agent"

                            MISSING=""
                            for img in $REQUIRED_IMAGES; do
                                if ! docker images --format "{{.Repository}}" | grep -q "^${img}$"; then
                                    MISSING="$MISSING $img"
                                fi
                            done

                            if [ -n "$MISSING" ]; then
                                echo "⚠️  ALERTA: Imagens de agentes em falta:$MISSING"
                                echo "Execute: docker pull <image_name>:latest para recuperar"
                                exit 1
                            else
                                echo "✅ Todas as imagens de agentes estão presentes"
                                exit 0
                            fi
                        ''',
                        returnStatus: true
                    )

                    if (agentImagesCheck != 0) {
                        unstable(message: "Algumas imagens de agentes estão em falta")
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                echo "\n📋 LIMPEZA CONCLUÍDA EM: $(date)"

                // Salvar relatório
                sh '''
                    REPORT_FILE="/var/jenkins_home/cleanup-reports/cleanup-$(date +%Y%m%d-%H%M%S).log"
                    mkdir -p /var/jenkins_home/cleanup-reports

                    echo "Relatório salvo em: $REPORT_FILE"
                    docker system df > "$REPORT_FILE"

                    # Manter apenas últimos 30 relatórios
                    find /var/jenkins_home/cleanup-reports -type f -name "cleanup-*.log" | sort -r | tail -n +31 | xargs rm -f
                '''
            }
        }

        failure {
            echo "❌ FALHA NA LIMPEZA - Verificar logs"
        }

        success {
            echo "✅ LIMPEZA CONCLUÍDA COM SUCESSO"
        }

        unstable {
            echo "⚠️  LIMPEZA CONCLUÍDA COM AVISOS - Verificar imagens de agentes"
        }
    }
}
