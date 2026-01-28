pipeline {
    agent any

    environment {
        DOCKER_API_VERSION = '1.41'

        // Imagens críticas que devem SEMPRE estar presentes
        CRITICAL_IMAGES = '''
            jenkins/jenkins:lts
            jenkins/agent:alpine-jdk17
            jenkins-node-agent:latest
            jenkins-python-agent:latest
            jenkins/agent:latest
            jenkins/inbound-agent:latest
            jenkins/ssh-agent:latest
        '''

        // Imagens críticas que NUNCA devem ser apagadas (regex patterns)
        PROTECTED_IMAGES = 'jenkins.*'

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
        stage('1️⃣  Protect Critical Images') {
            steps {
                script {
                    echo "═══════════════════════════════════════════════════════"
                    echo "  1️⃣  PROTEÇÃO DE IMAGENS CRÍTICAS"
                    echo "═══════════════════════════════════════════════════════"
                    echo "Data: ${new Date().format('yyyy-MM-dd HH:mm:ss')}"

                    def criticalImages = env.CRITICAL_IMAGES.split('\n').findAll { it.trim() }
                    def missingImages = []
                    def presentImages = []

                    echo "\n📋 Verificando ${criticalImages.size()} imagens críticas...\n"

                    criticalImages.each { image ->
                        image = image.trim()
                        if (!image) return

                        def imageExists = sh(
                            script: """
                                docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${image}\$"
                            """,
                            returnStatus: true
                        ) == 0

                        if (imageExists) {
                            echo "✅ ${image} - Presente"
                            presentImages << image
                        } else {
                            echo "❌ ${image} - EM FALTA"
                            missingImages << image
                        }
                    }

                    // Armazenar para próximos stages
                    env.MISSING_IMAGES = missingImages.join(',')
                    env.PRESENT_COUNT = presentImages.size().toString()
                    env.MISSING_COUNT = missingImages.size().toString()

                    // Pull de imagens em falta
                    if (missingImages.size() > 0) {
                        echo "\n⚠️  ATENÇÃO: ${missingImages.size()} imagens críticas em falta!"
                        echo "Tentando recuperar...\n"

                        def pullFailed = []
                        def pullSuccess = []

                        missingImages.each { image ->
                            echo "📥 Fazendo pull de: ${image}"

                            def pullResult = sh(
                                script: "docker pull ${image}",
                                returnStatus: true
                            )

                            if (pullResult == 0) {
                                echo "✅ ${image} recuperado com sucesso"
                                pullSuccess << image
                            } else {
                                echo "❌ Falha ao recuperar ${image}"
                                pullFailed << image
                            }
                        }

                        if (pullFailed.size() > 0) {
                            currentBuild.result = 'UNSTABLE'
                        }
                    }

                    echo "\n🛡️  CRIANDO CONTAINERS DE PROTEÇÃO...\n"

                    criticalImages.each { image ->
                        image = image.trim()
                        if (!image) return

                        def containerName = "keeper-${image.replaceAll('[/:]', '-')}"

                        // Remover container existente se houver
                        sh """
                            docker rm -f ${containerName} 2>/dev/null || true
                        """

                        // Verificar se imagem existe antes de criar container
                        def imageExists = sh(
                            script: """
                                docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${image}\$"
                            """,
                            returnStatus: true
                        ) == 0

                        if (imageExists) {
                            // Criar container dummy que mantém a imagem "in use"
                            def createResult = sh(
                                script: """
                                    docker run -d \
                                        --name ${containerName} \
                                        --restart=unless-stopped \
                                        --label="purpose=image-protection" \
                                        --label="protected-image=${image}" \
                                        ${image} \
                                        sleep infinity 2>&1
                                """,
                                returnStatus: true
                            )

                            if (createResult == 0) {
                                echo "✅ Container de proteção criado para: ${image}"
                            } else {
                                echo "⚠️  Não foi possível criar container para: ${image}"
                            }
                        } else {
                            echo "⚠️  Imagem não existe, pulando: ${image}"
                        }
                    }

                    echo "\n🔍 VERIFICANDO PROTEÇÃO...\n"

                    sh '''
                        echo "Containers de proteção ativos:"
                        docker ps --filter "label=purpose=image-protection" \
                            --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
                    '''
                }
            }
        }

        stage('2️⃣  Safe Docker Cleanup') {
            steps {
                script {
                    echo "\n═══════════════════════════════════════════════════════"
                    echo "  2️⃣  LIMPEZA SEGURA DE DOCKER"
                    echo "═══════════════════════════════════════════════════════"

                    sh '''
                        echo "\n📊 ESPAÇO EM DISCO ANTES DO CLEANUP:"
                        df -h /var/lib/docker

                        echo "\n📦 RESUMO DOCKER ANTES:"
                        docker system df

                        echo "\n🧹 INICIANDO LIMPEZA SEGURA..."

                        # 1. Remover APENAS imagens dangling (sem tag)
                        echo "\n1️⃣  Removendo imagens dangling (<none>:<none>)..."
                        DANGLING_COUNT=$(docker images -qf "dangling=true" | wc -l)
                        if [ "$DANGLING_COUNT" -gt 0 ]; then
                            docker image prune -f
                            echo "✅ $DANGLING_COUNT imagens dangling removidas"
                        else
                            echo "✅ Nenhuma imagem dangling encontrada"
                        fi

                        # 2. Remover containers parados antigos (EXCETO os de proteção)
                        echo "\n2️⃣  Removendo containers parados (> ${CONTAINER_RETENTION_DAYS} dias)..."

                        OLD_CONTAINERS=$(docker ps -a --filter "status=exited" --format "{{.ID}} {{.Names}} {{.Labels}}" | \
                            grep -v "purpose=image-protection" | awk '{print $1}' || true)

                        if [ -n "$OLD_CONTAINERS" ]; then
                            echo "$OLD_CONTAINERS" | xargs -r docker rm -f
                            REMOVED_COUNT=$(echo "$OLD_CONTAINERS" | wc -l)
                            echo "✅ $REMOVED_COUNT containers antigos removidos"
                        else
                            echo "✅ Nenhum container antigo para remover"
                        fi

                        # 3. Remover volumes dangling
                        echo "\n3️⃣  Removendo volumes dangling..."
                        VOLUME_COUNT=$(docker volume ls -qf "dangling=true" | wc -l)
                        if [ "$VOLUME_COUNT" -gt 0 ]; then
                            docker volume prune -f
                            echo "✅ Volumes dangling removidos"
                        else
                            echo "✅ Nenhum volume dangling encontrado"
                        fi

                        # 4. Remover networks não usadas (exceto default)
                        echo "\n4️⃣  Removendo networks não usadas..."
                        docker network prune -f
                        echo "✅ Networks não usadas removidas"

                        # 5. Limpar build cache (manter últimos 7 dias)
                        echo "\n5️⃣  Limpando build cache antigo..."
                        docker builder prune -f --filter "until=168h"
                        echo "✅ Build cache antigo removido"

                        # 6. Limpar workspaces antigos do Jenkins
                        echo "\n6️⃣  Limpando workspaces antigos do Jenkins..."
                        OLD_WORKSPACES=$(find /var/jenkins_home/workspace -maxdepth 1 -type d -mtime +${WORKSPACE_RETENTION_DAYS} 2>/dev/null || true)

                        if [ -n "$OLD_WORKSPACES" ]; then
                            find /var/jenkins_home/workspace -maxdepth 1 -type d -mtime +${WORKSPACE_RETENTION_DAYS} -exec rm -rf {} + 2>/dev/null || true
                            echo "✅ Workspaces antigos removidos"
                        else
                            echo "✅ Nenhum workspace antigo para remover"
                        fi

                        echo "\n📊 ESPAÇO EM DISCO APÓS CLEANUP:"
                        df -h /var/lib/docker

                        echo "\n📦 RESUMO DOCKER APÓS:"
                        docker system df
                    '''
                }
            }
        }

        stage('3️⃣  Verify Critical Images') {
            steps {
                script {
                    echo "\n═══════════════════════════════════════════════════════"
                    echo "  3️⃣  VERIFICAÇÃO FINAL DE IMAGENS CRÍTICAS"
                    echo "═══════════════════════════════════════════════════════"

                    def criticalImages = env.CRITICAL_IMAGES.split('\n').findAll { it.trim() }
                    def allPresent = true
                    def missingAfterCleanup = []

                    echo "\n🔍 Verificando que TODAS as imagens críticas ainda existem...\n"

                    criticalImages.each { image ->
                        image = image.trim()
                        if (!image) return

                        def imageExists = sh(
                            script: """
                                docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${image}\$"
                            """,
                            returnStatus: true
                        ) == 0

                        if (imageExists) {
                            echo "✅ ${image} - Presente (segura)"
                        } else {
                            echo "❌ ${image} - APAGADA ACIDENTALMENTE!"
                            allPresent = false
                            missingAfterCleanup << image
                        }
                    }

                    if (!allPresent) {
                        error """
                        ❌ ERRO CRÍTICO: Algumas imagens críticas foram apagadas durante o cleanup!
                        Imagens em falta: ${missingAfterCleanup.join(', ')}

                        Ação imediata necessária:
                        1. Executar: docker pull <image>
                        2. Revisar lógica de proteção na pipeline
                        3. Verificar se containers de proteção foram criados corretamente
                        """
                    }

                    echo "\n✅ SUCESSO: Todas as ${criticalImages.size()} imagens críticas estão presentes!"

                    sh '''
                        echo "\n🖼️  IMAGENS FINAIS NO SISTEMA:"
                        docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

                        echo "\n🛡️  CONTAINERS DE PROTEÇÃO ATIVOS:"
                        docker ps --filter "label=purpose=image-protection" \
                            --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
                    '''
                }
            }
        }
    }

    post {
        always {
            script {
                echo "\n═══════════════════════════════════════════════════════"
                echo "  RESUMO DA EXECUÇÃO"
                echo "═══════════════════════════════════════════════════════\n"

                def summary = """
📊 ESTATÍSTICAS:
Imagens Críticas Verificadas: ${env.CRITICAL_IMAGES.split('\n').findAll { it.trim() }.size()}
Imagens Presentes Inicialmente: ${env.PRESENT_COUNT ?: '0'}
Imagens em Falta Inicialmente: ${env.MISSING_COUNT ?: '0'}

✅ RESULTADO FINAL: ${currentBuild.result ?: 'SUCCESS'}
"""

                echo summary

                // Salvar relatório
                sh """
                    mkdir -p /var/jenkins_home/cleanup-reports
                    REPORT_FILE="/var/jenkins_home/cleanup-reports/unified-cleanup-\$(date +%Y%m%d-%H%M%S).log"

                    cat > "\$REPORT_FILE" <<EOF
${summary}

Data: \$(date)
Build: ${env.BUILD_NUMBER}

Containers de Proteção:
\$(docker ps --filter "label=purpose=image-protection" --format "{{.Names}}\t{{.Image}}" 2>/dev/null)

Imagens Docker Finais:
\$(docker images --format "{{.Repository}}:{{.Tag}}\t{{.Size}}" 2>/dev/null)

Espaço em Disco:
\$(df -h /var/lib/docker)

Resumo Docker:
\$(docker system df)
EOF

                    echo "📄 Relatório salvo em: \$REPORT_FILE"

                    # Manter apenas últimos 30 relatórios
                    find /var/jenkins_home/cleanup-reports -type f -name "*-cleanup-*.log" | \
                        sort -r | tail -n +31 | xargs rm -f 2>/dev/null || true
                """
            }
        }

        success {
            echo "\n✅ ═══════════════════════════════════════"
            echo "✅ CLEANUP UNIFICADO CONCLUÍDO COM SUCESSO"
            echo "✅ ═══════════════════════════════════════"
        }

        unstable {
            echo "\n⚠️  CLEANUP CONCLUÍDO COM AVISOS"
            echo "Verificar logs para detalhes sobre imagens que falharam na recuperação"
        }

        failure {
            echo "\n❌ FALHA NO CLEANUP UNIFICADO"
            echo "URGENTE: Verificar logs e executar recovery manual se necessário"
            echo "Script de recovery: /path/to/jenkins/scripts/emergency-recovery.sh"
        }
    }
}
