#!/bin/bash

###############################################################################
# Script de Monitorização de Espaço em Disco - Jenkins/Docker
# Verifica espaço e alerta quando thresholds são atingidos
###############################################################################

set -e

# Configuração
DOCKER_PATH="/var/lib/docker"
JENKINS_PATH="/var/jenkins_home"

# Thresholds (percentual)
WARN_THRESHOLD=70
CRITICAL_THRESHOLD=85

# Notificações (configurar conforme necessário)
ENABLE_EMAIL=false
EMAIL_TO="devops@example.com"
ENABLE_SLACK=false
SLACK_WEBHOOK=""

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

###############################################################################
# Funções
###############################################################################

print_header() {
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
}

print_success() { echo -e "${GREEN}✅ $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
print_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }

get_disk_usage() {
    local path=$1
    df "$path" | tail -1 | awk '{print $5}' | sed 's/%//'
}

get_disk_available() {
    local path=$1
    df -h "$path" | tail -1 | awk '{print $4}'
}

send_email_alert() {
    local subject=$1
    local body=$2

    if [ "$ENABLE_EMAIL" = true ]; then
        echo "$body" | mail -s "$subject" "$EMAIL_TO"
        print_info "Email enviado para $EMAIL_TO"
    fi
}

send_slack_alert() {
    local message=$1
    local color=$2

    if [ "$ENABLE_SLACK" = true ] && [ -n "$SLACK_WEBHOOK" ]; then
        curl -X POST "$SLACK_WEBHOOK" \
            -H 'Content-Type: application/json' \
            -d "{
                \"attachments\": [{
                    \"color\": \"$color\",
                    \"text\": \"$message\",
                    \"footer\": \"Jenkins Monitoring\",
                    \"ts\": $(date +%s)
                }]
            }" 2>/dev/null

        print_info "Alerta enviado para Slack"
    fi
}

check_disk_space() {
    local path=$1
    local name=$2
    local usage=$(get_disk_usage "$path")
    local available=$(get_disk_available "$path")

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "📁 $name"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    if [ "$usage" -ge "$CRITICAL_THRESHOLD" ]; then
        print_error "CRÍTICO: Uso em ${usage}% (disponível: ${available})"
        send_email_alert "CRÍTICO: Espaço em disco $name" \
            "Uso: ${usage}%\nDisponível: ${available}\nPath: $path"
        send_slack_alert "🚨 CRÍTICO: $name em ${usage}% (disponível: ${available})" "danger"
        return 2
    elif [ "$usage" -ge "$WARN_THRESHOLD" ]; then
        print_warning "AVISO: Uso em ${usage}% (disponível: ${available})"
        send_email_alert "AVISO: Espaço em disco $name" \
            "Uso: ${usage}%\nDisponível: ${available}\nPath: $path"
        send_slack_alert "⚠️  AVISO: $name em ${usage}% (disponível: ${available})" "warning"
        return 1
    else
        print_success "OK: Uso em ${usage}% (disponível: ${available})"
        return 0
    fi
}

analyze_docker_usage() {
    echo ""
    print_header "ANÁLISE DE USO DOCKER"

    if ! command -v docker &> /dev/null; then
        print_warning "Docker não disponível"
        return
    fi

    echo ""
    echo "📊 Resumo do Sistema Docker:"
    docker system df

    echo ""
    echo "🖼️  Top 10 Imagens Maiores:"
    docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | head -11

    echo ""
    echo "📦 Containers em Execução:"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Size}}"

    echo ""
    echo "💾 Volumes:"
    docker volume ls --format "table {{.Name}}\t{{.Driver}}"

    # Calcular espaço recuperável
    echo ""
    echo "🧹 Espaço Potencialmente Recuperável:"

    local dangling_images=$(docker images -qf "dangling=true" | wc -l)
    local stopped_containers=$(docker ps -aqf "status=exited" | wc -l)
    local dangling_volumes=$(docker volume ls -qf "dangling=true" | wc -l)

    echo "  - Imagens dangling: $dangling_images"
    echo "  - Containers parados: $stopped_containers"
    echo "  - Volumes dangling: $dangling_volumes"

    if [ "$dangling_images" -gt 10 ] || [ "$stopped_containers" -gt 20 ]; then
        print_warning "Considere executar a pipeline de cleanup"
    fi
}

analyze_jenkins_usage() {
    echo ""
    print_header "ANÁLISE DE USO JENKINS"

    if [ ! -d "$JENKINS_PATH" ]; then
        print_warning "Diretório Jenkins não encontrado"
        return
    fi

    echo ""
    echo "📂 Top 10 Diretórios Maiores no Jenkins:"
    du -sh "$JENKINS_PATH"/* 2>/dev/null | sort -rh | head -10

    echo ""
    echo "🗂️  Workspaces:"
    if [ -d "$JENKINS_PATH/workspace" ]; then
        local workspace_count=$(find "$JENKINS_PATH/workspace" -maxdepth 1 -type d | wc -l)
        local workspace_size=$(du -sh "$JENKINS_PATH/workspace" 2>/dev/null | cut -f1)
        echo "  - Total de workspaces: $workspace_count"
        echo "  - Tamanho total: $workspace_size"

        echo ""
        echo "  Top 5 workspaces maiores:"
        du -sh "$JENKINS_PATH/workspace"/* 2>/dev/null | sort -rh | head -5
    fi

    echo ""
    echo "📋 Jobs:"
    if [ -d "$JENKINS_PATH/jobs" ]; then
        local jobs_count=$(find "$JENKINS_PATH/jobs" -maxdepth 1 -type d | wc -l)
        local jobs_size=$(du -sh "$JENKINS_PATH/jobs" 2>/dev/null | cut -f1)
        echo "  - Total de jobs: $jobs_count"
        echo "  - Tamanho total: $jobs_size"
    fi

    echo ""
    echo "📝 Logs antigos:"
    if [ -d "$JENKINS_PATH/logs" ]; then
        local old_logs=$(find "$JENKINS_PATH/logs" -type f -mtime +30 | wc -l)
        echo "  - Logs com mais de 30 dias: $old_logs"
    fi
}

verify_critical_images() {
    echo ""
    print_header "VERIFICAÇÃO DE IMAGENS CRÍTICAS"

    if ! command -v docker &> /dev/null; then
        print_warning "Docker não disponível"
        return
    fi

    local required_images=(
        "jenkins/agent"
        "jenkins/inbound-agent"
        "jenkins/ssh-agent"
    )

    local missing=0

    for img in "${required_images[@]}"; do
        if docker images --format "{{.Repository}}" | grep -q "^${img}$"; then
            print_success "$img presente"
        else
            print_error "$img EM FALTA!"
            ((missing++))
        fi
    done

    if [ $missing -gt 0 ]; then
        print_error "$missing imagens críticas em falta"
        send_email_alert "ALERTA: Imagens Jenkins em falta" \
            "Encontradas $missing imagens críticas em falta.\nExecute o script de recuperação imediatamente."
        send_slack_alert "🚨 ALERTA: $missing imagens Jenkins críticas em falta!" "danger"
    fi
}

generate_recommendations() {
    echo ""
    print_header "RECOMENDAÇÕES"

    local docker_usage=$(get_disk_usage "$DOCKER_PATH")
    local jenkins_usage=$(get_disk_usage "$JENKINS_PATH")

    if [ "$docker_usage" -ge "$WARN_THRESHOLD" ]; then
        echo ""
        print_warning "Ações recomendadas para Docker:"
        echo "  1. Executar pipeline de cleanup seguro"
        echo "  2. Remover imagens não usadas manualmente:"
        echo "     docker image prune (apenas dangling)"
        echo "  3. Remover containers antigos:"
        echo "     docker container prune -f --filter 'until=168h'"
        echo "  4. Verificar volumes não usados:"
        echo "     docker volume prune -f"
    fi

    if [ "$jenkins_usage" -ge "$WARN_THRESHOLD" ]; then
        echo ""
        print_warning "Ações recomendadas para Jenkins:"
        echo "  1. Limpar workspaces antigos"
        echo "  2. Arquivar builds antigos"
        echo "  3. Limpar logs antigos"
        echo "  4. Revisar política de retenção de builds"
    fi

    echo ""
    print_info "Scripts disponíveis:"
    echo "  - Cleanup seguro: /path/to/jenkins/pipelines/safe-docker-cleanup.groovy"
    echo "  - Verificação: /path/to/jenkins/scripts/verify-agent-images.sh"
    echo "  - Recuperação: /path/to/jenkins/scripts/emergency-recovery.sh"
}

###############################################################################
# Main
###############################################################################

main() {
    print_header "MONITORIZAÇÃO DE ESPAÇO - JENKINS/DOCKER"
    echo "Data: $(date '+%Y-%m-%d %H:%M:%S')"

    local status=0

    # Verificar espaço em disco
    echo ""
    print_header "VERIFICAÇÃO DE ESPAÇO EM DISCO"

    if ! check_disk_space "$DOCKER_PATH" "Docker Storage"; then
        status=$?
    fi

    if ! check_disk_space "$JENKINS_PATH" "Jenkins Home"; then
        status=$?
    fi

    # Análises detalhadas
    analyze_docker_usage
    analyze_jenkins_usage
    verify_critical_images
    generate_recommendations

    # Resumo final
    echo ""
    print_header "RESUMO"

    if [ $status -eq 0 ]; then
        print_success "Sistema em estado saudável"
    elif [ $status -eq 1 ]; then
        print_warning "Sistema necessita atenção"
    else
        print_error "Sistema em estado crítico - ação imediata necessária"
    fi

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Próxima execução recomendada: $(date -d '+1 day' '+%Y-%m-%d %H:%M')"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    exit $status
}

# Executar
main "$@"
