#!/bin/bash

###############################################################################
# Script de Verificação de Imagens Jenkins Agent
# Verifica se todas as imagens necessárias para agentes Docker estão presentes
###############################################################################

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Lista de imagens críticas (ajustar conforme necessário)
declare -a REQUIRED_IMAGES=(
    "jenkins/agent:latest"
    "jenkins/inbound-agent:latest"
    "jenkins/ssh-agent:latest"
    "maven:3.9-eclipse-temurin-17"
    "node:18-alpine"
    "python:3.11-slim"
)

# Registries alternativos
DOCKER_REGISTRY="docker.io"

###############################################################################
# Funções
###############################################################################

print_header() {
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

check_image_exists() {
    local image=$1
    if docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${image}$"; then
        return 0
    else
        return 1
    fi
}

pull_image() {
    local image=$1
    print_info "Fazendo pull da imagem: $image"

    if docker pull "$image"; then
        print_success "Imagem $image baixada com sucesso"
        return 0
    else
        print_error "Falha ao baixar imagem $image"
        return 1
    fi
}

###############################################################################
# Main
###############################################################################

print_header "VERIFICAÇÃO DE IMAGENS JENKINS AGENT"

echo ""
print_info "Verificando imagens Docker necessárias..."
echo ""

MISSING_IMAGES=()
PRESENT_IMAGES=()

# Verificar cada imagem
for image in "${REQUIRED_IMAGES[@]}"; do
    echo -n "Verificando $image... "

    if check_image_exists "$image"; then
        echo -e "${GREEN}✓${NC}"
        PRESENT_IMAGES+=("$image")
    else
        echo -e "${RED}✗${NC}"
        MISSING_IMAGES+=("$image")
    fi
done

echo ""
print_header "RESULTADO DA VERIFICAÇÃO"
echo ""

# Imagens presentes
if [ ${#PRESENT_IMAGES[@]} -gt 0 ]; then
    print_success "Imagens presentes (${#PRESENT_IMAGES[@]}):"
    for image in "${PRESENT_IMAGES[@]}"; do
        SIZE=$(docker images --format "{{.Size}}" "$image" 2>/dev/null || echo "N/A")
        CREATED=$(docker images --format "{{.CreatedAt}}" "$image" 2>/dev/null || echo "N/A")
        echo "  - $image (Tamanho: $SIZE, Criado: $CREATED)"
    done
    echo ""
fi

# Imagens em falta
if [ ${#MISSING_IMAGES[@]} -gt 0 ]; then
    print_error "Imagens em falta (${#MISSING_IMAGES[@]}):"
    for image in "${MISSING_IMAGES[@]}"; do
        echo "  - $image"
    done
    echo ""

    # Perguntar se quer fazer pull
    if [ "$1" == "--auto-pull" ]; then
        AUTO_PULL=true
    else
        read -p "Deseja fazer pull das imagens em falta? (s/n): " -n 1 -r
        echo ""
        if [[ $REPLY =~ ^[Ss]$ ]]; then
            AUTO_PULL=true
        else
            AUTO_PULL=false
        fi
    fi

    if [ "$AUTO_PULL" == true ]; then
        echo ""
        print_info "Iniciando pull das imagens em falta..."
        echo ""

        FAILED_PULLS=()
        for image in "${MISSING_IMAGES[@]}"; do
            if ! pull_image "$image"; then
                FAILED_PULLS+=("$image")
            fi
        done

        echo ""
        if [ ${#FAILED_PULLS[@]} -eq 0 ]; then
            print_success "Todas as imagens foram baixadas com sucesso!"
        else
            print_error "Falha ao baixar as seguintes imagens:"
            for image in "${FAILED_PULLS[@]}"; do
                echo "  - $image"
            done
            exit 1
        fi
    else
        print_warning "Algumas imagens estão em falta. Jenkins pode falhar ao criar agentes."
        echo ""
        echo "Para fazer pull manualmente, execute:"
        for image in "${MISSING_IMAGES[@]}"; do
            echo "  docker pull $image"
        done
        exit 1
    fi
else
    print_success "Todas as imagens necessárias estão presentes!"
fi

echo ""
print_header "ESPAÇO UTILIZADO PELAS IMAGENS"
echo ""
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}" | \
    grep -E "$(echo "${REQUIRED_IMAGES[@]}" | tr ' ' '|' | sed 's/:latest//g')"

echo ""
print_header "RESUMO DO SISTEMA DOCKER"
echo ""
docker system df

echo ""
print_success "Verificação concluída!"
exit 0
