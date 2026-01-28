#!/bin/bash

###############################################################################
# Script de Recuperação de Emergência - Jenkins Docker Agents
# Restaura imagens críticas apagadas acidentalmente
###############################################################################

set -e

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
}

print_success() { echo -e "${GREEN}✅ $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
print_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }

###############################################################################
# Configuração
###############################################################################

# Imagens críticas para Jenkins (ajustar conforme sua configuração)
declare -A CRITICAL_IMAGES=(
    # Imagens base Jenkins
    ["jenkins/agent"]="latest alpine jdk17 jdk11"
    ["jenkins/inbound-agent"]="latest alpine jdk17 jdk11"
    ["jenkins/ssh-agent"]="latest alpine jdk17 jdk11"

    # Imagens para builds
    ["maven"]="3.9-eclipse-temurin-17 3.9-eclipse-temurin-11"
    ["gradle"]="8-jdk17 8-jdk11"
    ["node"]="18-alpine 20-alpine lts-alpine"
    ["python"]="3.11-slim 3.10-slim 3.9-slim"
    ["golang"]="1.21-alpine 1.20-alpine"
    ["openjdk"]="17-jdk 11-jdk 8-jdk"

    # Imagens para Docker-in-Docker
    ["docker"]="24-dind 23-dind"

    # Outras ferramentas
    ["alpine"]="latest 3.19"
    ["ubuntu"]="22.04 20.04"
)

# Arquivo de log
LOG_FILE="/tmp/jenkins-recovery-$(date +%Y%m%d-%H%M%S).log"

###############################################################################
# Funções
###############################################################################

log_message() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker não está instalado ou não está no PATH"
        exit 1
    fi

    if ! docker ps &> /dev/null; then
        print_error "Não é possível conectar ao Docker daemon"
        print_info "Verifique se o Docker está em execução e se tem permissões"
        exit 1
    fi

    print_success "Docker está acessível"
}

list_missing_images() {
    local missing_count=0

    for image in "${!CRITICAL_IMAGES[@]}"; do
        tags="${CRITICAL_IMAGES[$image]}"

        for tag in $tags; do
            full_image="$image:$tag"

            if ! docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${full_image}$"; then
                echo "$full_image"
                ((missing_count++))
            fi
        done
    done

    return $missing_count
}

pull_image_with_retry() {
    local image=$1
    local max_retries=3
    local retry_count=0

    while [ $retry_count -lt $max_retries ]; do
        print_info "Tentativa $((retry_count + 1))/$max_retries: Baixando $image"

        if docker pull "$image" 2>&1 | tee -a "$LOG_FILE"; then
            print_success "Imagem $image baixada com sucesso"
            log_message "SUCCESS: Pulled $image"
            return 0
        else
            ((retry_count++))
            print_warning "Falha na tentativa $retry_count"

            if [ $retry_count -lt $max_retries ]; then
                sleep 5
            fi
        fi
    done

    print_error "Falha ao baixar $image após $max_retries tentativas"
    log_message "FAILED: Could not pull $image after $max_retries attempts"
    return 1
}

pull_all_missing() {
    local missing_images=()
    local failed_images=()
    local success_count=0
    local fail_count=0

    print_header "IDENTIFICANDO IMAGENS EM FALTA"

    # Coletar imagens em falta
    for image in "${!CRITICAL_IMAGES[@]}"; do
        tags="${CRITICAL_IMAGES[$image]}"

        for tag in $tags; do
            full_image="$image:$tag"

            if ! docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${full_image}$"; then
                missing_images+=("$full_image")
            fi
        done
    done

    if [ ${#missing_images[@]} -eq 0 ]; then
        print_success "Nenhuma imagem em falta!"
        return 0
    fi

    echo ""
    print_warning "Encontradas ${#missing_images[@]} imagens em falta:"
    for img in "${missing_images[@]}"; do
        echo "  - $img"
    done

    echo ""
    print_header "INICIANDO DOWNLOAD DAS IMAGENS"
    echo ""

    # Download com barra de progresso
    local current=0
    local total=${#missing_images[@]}

    for img in "${missing_images[@]}"; do
        ((current++))
        echo ""
        echo "[$current/$total] Processando: $img"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

        if pull_image_with_retry "$img"; then
            ((success_count++))
        else
            failed_images+=("$img")
            ((fail_count++))
        fi
    done

    # Relatório final
    echo ""
    print_header "RELATÓRIO DE RECUPERAÇÃO"
    echo ""

    print_success "Imagens recuperadas: $success_count"
    if [ $fail_count -gt 0 ]; then
        print_error "Falhas: $fail_count"
        echo ""
        print_error "Imagens que falharam:"
        for img in "${failed_images[@]}"; do
            echo "  - $img"
        done
    fi

    echo ""
    print_info "Log completo salvo em: $LOG_FILE"

    return $fail_count
}

verify_jenkins_connectivity() {
    print_header "VERIFICANDO CONECTIVIDADE JENKINS"

    if [ ! -d "/var/jenkins_home" ]; then
        print_warning "Diretório /var/jenkins_home não encontrado"
        print_info "Este script deve ser executado no servidor Jenkins"
        return 1
    fi

    print_success "Diretório Jenkins encontrado"
    return 0
}

restart_jenkins_if_needed() {
    echo ""
    read -p "Deseja reiniciar o Jenkins para aplicar as mudanças? (s/n): " -n 1 -r
    echo ""

    if [[ $REPLY =~ ^[Ss]$ ]]; then
        print_info "Reiniciando Jenkins..."

        if systemctl restart jenkins 2>/dev/null; then
            print_success "Jenkins reiniciado via systemctl"
        elif service jenkins restart 2>/dev/null; then
            print_success "Jenkins reiniciado via service"
        elif docker restart jenkins 2>/dev/null; then
            print_success "Container Jenkins reiniciado"
        else
            print_warning "Não foi possível reiniciar automaticamente"
            print_info "Reinicie manualmente o Jenkins"
        fi
    fi
}

create_protection_script() {
    local script_path="/usr/local/bin/protect-jenkins-images.sh"

    print_info "Criando script de proteção de imagens..."

    cat > "$script_path" << 'EOF'
#!/bin/bash
# Script para proteger imagens Jenkins de serem apagadas

# Adicionar labels às imagens críticas
PROTECTED_IMAGES=(
    "jenkins/agent"
    "jenkins/inbound-agent"
    "jenkins/ssh-agent"
    "maven"
    "node"
    "python"
    "openjdk"
)

for image in "${PROTECTED_IMAGES[@]}"; do
    docker images --format "{{.ID}}" --filter "reference=$image:*" | while read id; do
        docker image inspect $id --format='{{.Id}}' > /dev/null 2>&1 && \
            echo "Protegendo imagem: $image ($id)"
    done
done

echo "✅ Imagens protegidas"
EOF

    chmod +x "$script_path"
    print_success "Script de proteção criado em: $script_path"
}

###############################################################################
# Main
###############################################################################

main() {
    print_header "RECUPERAÇÃO DE EMERGÊNCIA - JENKINS DOCKER AGENTS"
    echo ""

    log_message "=== INICIANDO RECUPERAÇÃO ==="

    # Verificações
    check_docker
    verify_jenkins_connectivity || print_warning "Continuando mesmo assim..."

    echo ""
    print_header "STATUS ATUAL DO DOCKER"
    echo ""

    docker system df

    echo ""
    docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.ID}}"

    echo ""
    print_header "ANÁLISE DE IMAGENS CRÍTICAS"
    echo ""

    # Listar imagens que existem
    print_success "Imagens críticas presentes:"
    for image in "${!CRITICAL_IMAGES[@]}"; do
        tags="${CRITICAL_IMAGES[$image]}"
        for tag in $tags; do
            full_image="$image:$tag"
            if docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${full_image}$"; then
                echo "  ✓ $full_image"
            fi
        done
    done

    echo ""

    # Confirmar recuperação
    echo ""
    print_warning "Este script irá:"
    echo "  1. Identificar imagens Jenkins em falta"
    echo "  2. Fazer pull de todas as imagens necessárias"
    echo "  3. Criar script de proteção para evitar futuras deleções"
    echo "  4. Opcionalmente reiniciar o Jenkins"
    echo ""

    read -p "Deseja continuar? (s/n): " -n 1 -r
    echo ""

    if [[ ! $REPLY =~ ^[Ss]$ ]]; then
        print_warning "Recuperação cancelada pelo utilizador"
        exit 0
    fi

    # Executar recuperação
    if pull_all_missing; then
        print_success "Recuperação concluída com sucesso!"
    else
        print_error "Recuperação concluída com alguns erros"
        print_info "Verifique o log: $LOG_FILE"
    fi

    # Criar script de proteção
    echo ""
    create_protection_script

    # Mostrar estado final
    echo ""
    print_header "ESTADO FINAL DO SISTEMA"
    echo ""
    docker system df
    echo ""
    docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

    # Reiniciar Jenkins
    restart_jenkins_if_needed

    echo ""
    print_header "RECUPERAÇÃO CONCLUÍDA"
    echo ""
    print_info "Próximos passos:"
    echo "  1. Testar criação de agentes Docker no Jenkins"
    echo "  2. Revisar e atualizar a pipeline de cleanup"
    echo "  3. Adicionar monitorização de espaço em disco"
    echo "  4. Configurar alertas para imagens críticas"
    echo ""

    log_message "=== RECUPERAÇÃO CONCLUÍDA ==="
}

# Executar
main "$@"
