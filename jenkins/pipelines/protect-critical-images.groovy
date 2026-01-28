pipeline {
    agent any

    environment {
        DOCKER_API_VERSION = '1.41'

        // Imagens críticas que devem SEMPRE estar presentes
        CRITICAL_IMAGES = '''
            jenkins/agent:latest
            jenkins/inbound-agent:latest
            jenkins/ssh-agent:latest
            maven:3.9-eclipse-temurin-17
            node:18-alpine
            python:3.11-slim
            openjdk:17-jdk
        '''
    }

    triggers {
        // Executar ANTES da pipeline de cleanup
        // Se cleanup é sábado 2:00, este deve ser sábado 1:00
        cron('0 1 * * 6')
    }

    stages {
        stage('Verify Critical Images') {
            steps {
                script {
                    echo "═══════════════════════════════════════════════════════"
                    echo "  VERIFICAÇÃO E PROTEÇÃO DE IMAGENS CRÍTICAS"
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
                }
            }
        }

        stage('Pull Missing Images') {
            when {
                expression { env.MISSING_COUNT.toInteger() > 0 }
            }
            steps {
                script {
                    echo "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    echo "⚠️  ATENÇÃO: ${env.MISSING_COUNT} imagens críticas em falta!"
                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"

                    def missingImages = env.MISSING_IMAGES.split(',')
                    def pullFailed = []
                    def pullSuccess = []

                    missingImages.each { image ->
                        image = image.trim()
                        if (!image) return

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

                    env.PULL_SUCCESS_COUNT = pullSuccess.size().toString()
                    env.PULL_FAILED_COUNT = pullFailed.size().toString()
                    env.PULL_FAILED_IMAGES = pullFailed.join(',')

                    if (pullFailed.size() > 0) {
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }

        stage('Create Dummy Containers') {
            steps {
                script {
                    echo "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    echo "🛡️  CRIANDO CONTAINERS DE PROTEÇÃO"
                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"

                    def criticalImages = env.CRITICAL_IMAGES.split('\n').findAll { it.trim() }

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
                }
            }
        }

        stage('Verify Protection') {
            steps {
                script {
                    echo "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    echo "🔍 VERIFICANDO PROTEÇÃO"
                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"

                    sh '''
                        echo "Containers de proteção ativos:"
                        docker ps --filter "label=purpose=image-protection" \
                            --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"

                        echo "\nImagens protegidas (agora em uso):"
                        docker ps --filter "label=purpose=image-protection" \
                            --format "{{.Image}}" | sort -u
                    '''
                }
            }
        }

        stage('System Report') {
            steps {
                script {
                    echo "\n═══════════════════════════════════════════════════════"
                    echo "  RELATÓRIO DO SISTEMA"
                    echo "═══════════════════════════════════════════════════════\n"

                    sh '''
                        echo "📊 Resumo Docker:"
                        docker system df

                        echo "\n🖼️  Imagens presentes:"
                        docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.ID}}"

                        echo "\n📦 Total de containers de proteção:"
                        docker ps --filter "label=purpose=image-protection" --format "{{.Names}}" | wc -l
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
Imagens Verificadas: ${env.CRITICAL_IMAGES.split('\n').findAll { it.trim() }.size()}
Imagens Presentes: ${env.PRESENT_COUNT ?: '0'}
Imagens em Falta: ${env.MISSING_COUNT ?: '0'}
"""

                if (env.MISSING_COUNT && env.MISSING_COUNT.toInteger() > 0) {
                    summary += """
Recuperadas com Sucesso: ${env.PULL_SUCCESS_COUNT ?: '0'}
Falhas na Recuperação: ${env.PULL_FAILED_COUNT ?: '0'}
"""
                }

                echo summary

                // Salvar relatório
                sh """
                    mkdir -p /var/jenkins_home/protection-reports
                    REPORT_FILE="/var/jenkins_home/protection-reports/protection-\$(date +%Y%m%d-%H%M%S).log"

                    cat > "\$REPORT_FILE" <<EOF
${summary}

Data: \$(date)
Build: ${env.BUILD_NUMBER}
Status: ${currentBuild.result ?: 'SUCCESS'}

Containers de Proteção:
\$(docker ps --filter "label=purpose=image-protection" --format "{{.Names}}\t{{.Image}}" 2>/dev/null)

Imagens Docker:
\$(docker images --format "{{.Repository}}:{{.Tag}}\t{{.Size}}" 2>/dev/null)
EOF

                    echo "Relatório salvo em: \$REPORT_FILE"

                    # Manter apenas últimos 30 relatórios
                    find /var/jenkins_home/protection-reports -type f -name "protection-*.log" | \
                        sort -r | tail -n +31 | xargs rm -f 2>/dev/null || true
                """
            }
        }

        success {
            echo "✅ PROTEÇÃO APLICADA COM SUCESSO"
        }

        unstable {
            script {
                echo "⚠️  PROTEÇÃO APLICADA COM AVISOS"

                if (env.PULL_FAILED_COUNT && env.PULL_FAILED_COUNT.toInteger() > 0) {
                    echo "\nImagens que falharam na recuperação:"
                    env.PULL_FAILED_IMAGES.split(',').each { img ->
                        echo "  - ${img}"
                    }

                    echo "\n⚠️  Estas imagens podem estar indisponíveis para Jenkins!"
                    echo "Ação recomendada: Verificar conectividade com registry"
                }
            }
        }

        failure {
            echo "❌ FALHA NA APLICAÇÃO DE PROTEÇÃO"
            echo "URGENTE: Verificar logs e executar manualmente"
            echo "Script: /path/to/jenkins/scripts/verify-agent-images.sh"
        }
    }
}
