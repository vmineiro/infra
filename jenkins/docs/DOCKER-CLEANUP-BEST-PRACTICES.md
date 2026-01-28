# Docker Cleanup - Boas Práticas e Guia de Segurança

## Problema Identificado

### Comandos PERIGOSOS na Pipeline Original

```bash
# ❌ NUNCA USAR EM PRODUÇÃO
docker image prune -a -f         # Apaga TODAS as imagens sem containers
docker system prune -a -f --volumes  # Apaga tudo incluindo imagens e volumes
```

### Por que são perigosos?

1. **`docker image prune -a`**
   - Remove TODAS as imagens não associadas a containers em execução
   - Inclui imagens necessárias para Jenkins agents
   - Jenkins cria containers dinamicamente, então entre jobs as imagens ficam "unused"

2. **`docker system prune -a`**
   - Remove tudo: containers, networks, imagens, volumes
   - Impossível recuperar sem re-download
   - Pode causar downtime significativo

---

## Entendendo Docker Prune

### Tipos de Recursos "Unused"

| Tipo | Definição | Seguro Remover? |
|------|-----------|-----------------|
| **Dangling Images** | Imagens `<none>:<none>`, layers intermediários | ✅ SIM |
| **Unused Images** | Imagens com tag mas sem containers | ⚠️ CUIDADO |
| **Stopped Containers** | Containers em estado exited | ⚠️ CUIDADO |
| **Dangling Volumes** | Volumes não montados | ⚠️ CUIDADO |
| **Unused Networks** | Networks sem containers | ✅ GERALMENTE |

### Comandos e Seus Efeitos

#### 1. Image Prune

```bash
# SEGURO: Remove apenas dangling (<none>:<none>)
docker image prune -f

# PERIGOSO: Remove todas as imagens sem containers
docker image prune -a -f

# SEGURO COM FILTRO: Remove imagens antigas
docker image prune -a -f --filter "until=720h"  # 30 dias
```

#### 2. Container Prune

```bash
# Remove containers parados
docker container prune -f

# Com filtro de tempo
docker container prune -f --filter "until=168h"  # 7 dias
```

#### 3. Volume Prune

```bash
# Remove volumes dangling
docker volume prune -f

# ⚠️ Pode apagar dados! Verificar antes
```

#### 4. Network Prune

```bash
# Remove networks não usadas (geralmente seguro)
docker network prune -f
```

#### 5. System Prune

```bash
# SEGURO: Remove apenas dangling
docker system prune -f

# PERIGOSO: Remove tudo
docker system prune -a -f --volumes
```

---

## Filtros de Proteção

### 1. Filtros por Tempo

```bash
# Remover apenas recursos antigos
docker image prune -a -f --filter "until=720h"    # 30 dias
docker container prune -f --filter "until=168h"   # 7 dias
docker volume prune -f --filter "until=2160h"     # 90 dias
```

### 2. Filtros por Label

```bash
# Proteger imagens com label específico
docker image prune -a -f --filter "label!=preserve=true"

# Adicionar label a imagens protegidas
docker image tag jenkins/agent:latest jenkins/agent:latest-protected
```

### 3. Exclusão por Pattern

```bash
# Listar imagens NÃO protegidas
docker images --format "{{.Repository}}:{{.Tag}}" | \
  grep -vE "jenkins/.*|maven:.*|node:.*|python:.*"
```

### 4. Verificação Antes de Apagar

```bash
# DRY RUN: Ver o que SERIA apagado
docker image prune -a --filter "until=720h"  # Sem -f, mostra lista

# Confirmar manualmente
read -p "Apagar estas imagens? (s/n): " confirm
if [[ $confirm == [sS] ]]; then
    docker image prune -a -f --filter "until=720h"
fi
```

---

## Estratégia de Limpeza Segura

### 1. Níveis de Limpeza

#### Nível 1: Limpeza Conservadora (Semanal)
```bash
# Apenas recursos claramente não usados
docker image prune -f                    # Dangling images
docker container prune -f --filter "until=168h"  # Containers > 7 dias
docker network prune -f                  # Networks não usadas
```

#### Nível 2: Limpeza Moderada (Quinzenal)
```bash
# Incluir imagens antigas, protegendo críticas
docker container prune -f --filter "until=336h"  # 14 dias
docker volume prune -f
docker builder prune -f --filter "until=336h"

# Imagens não usadas, exceto protegidas
PROTECTED="jenkins|maven|node|python|openjdk"
docker images --format "{{.ID}} {{.Repository}}" | \
  grep -vE "$PROTECTED" | \
  awk '{print $1}' | \
  xargs -r docker rmi 2>/dev/null || true
```

#### Nível 3: Limpeza Agressiva (Mensal, com supervisão)
```bash
# Apenas em emergência, com backup
docker image prune -a -f --filter "until=720h"  # 30 dias
docker system prune -f --filter "until=720h"
```

### 2. Ordem de Execução

```bash
# 1. Verificar espaço ANTES
df -h /var/lib/docker
docker system df

# 2. Listar o que SERÁ apagado (dry-run)
docker image prune -a --filter "until=720h"

# 3. Executar limpeza por tipo
docker container prune -f --filter "until=168h"
docker image prune -f  # Apenas dangling
docker volume prune -f
docker network prune -f

# 4. Verificar espaço DEPOIS
docker system df

# 5. Verificar imagens críticas
docker images | grep -E "jenkins|maven|node"
```

---

## Proteção de Imagens Críticas

### 1. Identificar Imagens Críticas

```bash
# Imagens necessárias para Jenkins
CRITICAL_IMAGES=(
    "jenkins/agent"
    "jenkins/inbound-agent"
    "jenkins/ssh-agent"
    "maven:3.9-eclipse-temurin-17"
    "node:18-alpine"
    "python:3.11-slim"
    "openjdk:17-jdk"
)
```

### 2. Métodos de Proteção

#### Método 1: Manter Container Dummy
```bash
# Criar container que mantém imagem "in use"
docker run -d --name jenkins-agent-keeper \
  --restart=unless-stopped \
  jenkins/agent:latest \
  sleep infinity
```

#### Método 2: Labels de Proteção
```bash
# Adicionar label às imagens
for img in "${CRITICAL_IMAGES[@]}"; do
    docker image inspect "$img" >/dev/null 2>&1 && \
        echo "Protegendo: $img"
done

# Filtrar por label no prune
docker image prune -a -f --filter "label!=critical=true"
```

#### Método 3: Exclusão Manual
```bash
# Listar imagens não protegidas e remover seletivamente
docker images --format "{{.ID}} {{.Repository}}:{{.Tag}}" | \
  grep -vE "jenkins|maven|node|python|openjdk" | \
  while read id name; do
    # Verificar se está em uso
    if [ $(docker ps -a --filter "ancestor=$id" -q | wc -l) -eq 0 ]; then
      echo "Removendo: $name ($id)"
      docker rmi "$id" 2>/dev/null || true
    fi
  done
```

#### Método 4: Pull Regular
```bash
# Garantir que imagens existem (executar antes do cleanup)
for img in "${CRITICAL_IMAGES[@]}"; do
    if ! docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^$img$"; then
        echo "Recuperando: $img"
        docker pull "$img"
    fi
done
```

---

## Políticas de Retenção

### Recomendações por Tipo

| Recurso | Retenção Recomendada | Razão |
|---------|----------------------|-------|
| **Containers parados** | 7 dias | Debugging de falhas recentes |
| **Workspaces Jenkins** | 7-14 dias | Re-executar builds |
| **Imagens de build** | 30 dias | Rollback de releases |
| **Imagens base** | Permanente | Necessárias para agents |
| **Volumes dangling** | 30 dias | Possível perda de dados |
| **Build cache** | 14 dias | Acelera builds |
| **Logs** | 30-90 dias | Compliance/debugging |

### Configuração no Jenkins

```groovy
// Jenkinsfile - Configuração de retenção
options {
    buildDiscarder(logRotator(
        numToKeepStr: '30',         // Manter últimos 30 builds
        daysToKeepStr: '90',        // Manter por 90 dias
        artifactNumToKeepStr: '10', // Últimos 10 artifacts
        artifactDaysToKeepStr: '30' // Artifacts por 30 dias
    ))
}
```

---

## Monitorização e Alertas

### 1. Métricas para Monitorizar

```bash
# Espaço total usado por Docker
docker system df

# Espaço por tipo
docker system df -v

# Crescimento ao longo do tempo
du -sh /var/lib/docker
```

### 2. Thresholds Recomendados

| Métrica | Warning | Critical | Ação |
|---------|---------|----------|------|
| **Uso de disco** | 70% | 85% | Cleanup |
| **Imagens total** | 50 | 100 | Review |
| **Containers parados** | 20 | 50 | Prune |
| **Volumes dangling** | 10 | 25 | Prune |

### 3. Script de Monitorização

```bash
#!/bin/bash
# Executar diariamente

USAGE=$(df /var/lib/docker | tail -1 | awk '{print $5}' | sed 's/%//')

if [ "$USAGE" -gt 85 ]; then
    echo "CRÍTICO: Disco em ${USAGE}%"
    # Executar cleanup automático
    docker image prune -f
    docker container prune -f --filter "until=168h"
elif [ "$USAGE" -gt 70 ]; then
    echo "AVISO: Disco em ${USAGE}%"
    # Notificar equipe
fi
```

### 4. Alertas Proativos

```bash
# Verificar imagens críticas diariamente
REQUIRED="jenkins/agent jenkins/inbound-agent maven:3.9"

for img in $REQUIRED; do
    if ! docker images | grep -q "$img"; then
        echo "ALERTA: Imagem crítica em falta: $img"
        # Enviar notificação (email, Slack, PagerDuty)
        docker pull "$img"  # Recuperar automaticamente
    fi
done
```

---

## Checklist de Execução de Cleanup

### Antes do Cleanup

- [ ] Verificar espaço atual: `docker system df`
- [ ] Listar imagens críticas: `docker images | grep jenkins`
- [ ] Verificar jobs em execução no Jenkins
- [ ] Fazer backup de volumes críticos
- [ ] Notificar equipe se cleanup agressivo

### Durante o Cleanup

- [ ] Executar com dry-run primeiro (sem `-f`)
- [ ] Revisar lista de recursos a serem apagados
- [ ] Aplicar filtros de proteção
- [ ] Executar por tipo (não usar `system prune -a`)
- [ ] Monitorar logs e erros

### Depois do Cleanup

- [ ] Verificar espaço liberado: `docker system df`
- [ ] Confirmar imagens críticas presentes
- [ ] Testar criação de agent no Jenkins
- [ ] Documentar espaço liberado
- [ ] Atualizar relatório de cleanup

---

## Recuperação de Imagens Apagadas

### 1. Identificar Imagens em Falta

```bash
# Listar imagens esperadas vs presentes
for img in jenkins/agent jenkins/inbound-agent maven:3.9; do
    if docker images | grep -q "$img"; then
        echo "✓ $img"
    else
        echo "✗ $img - EM FALTA"
    fi
done
```

### 2. Recuperar Imagens

```bash
# Pull de imagens em falta
MISSING=(
    "jenkins/agent:latest"
    "jenkins/inbound-agent:latest"
    "maven:3.9-eclipse-temurin-17"
)

for img in "${MISSING[@]}"; do
    echo "Recuperando: $img"
    docker pull "$img" || echo "ERRO ao recuperar $img"
done
```

### 3. Verificar Funcionalidade

```bash
# Testar criação de container
docker run --rm jenkins/agent:latest java -version
docker run --rm maven:3.9-eclipse-temurin-17 mvn --version
```

### 4. Restaurar de Backup (se disponível)

```bash
# Se tiver backup das imagens
gunzip < /backup/jenkins-agent.tar.gz | docker load
docker images | grep jenkins
```

---

## Automação e CI/CD

### 1. Pipeline de Cleanup (Recomendada)

```groovy
// Ver: safe-docker-cleanup.groovy
pipeline {
    triggers {
        cron('0 2 * * 6')  // Sábado 2AM
    }
    stages {
        stage('Pre-Check') {
            // Verificar imagens críticas
        }
        stage('Cleanup') {
            // Limpeza segura com filtros
        }
        stage('Post-Check') {
            // Validar que imagens críticas ainda existem
        }
    }
}
```

### 2. Job de Verificação (Diário)

```groovy
pipeline {
    triggers {
        cron('0 1 * * *')  // Diário 1AM
    }
    stages {
        stage('Verify') {
            steps {
                sh '/path/to/verify-agent-images.sh'
            }
        }
    }
    post {
        failure {
            // Notificar equipe se imagens em falta
        }
    }
}
```

### 3. Monitorização (A cada hora)

```groovy
pipeline {
    triggers {
        cron('0 * * * *')  // A cada hora
    }
    stages {
        stage('Monitor') {
            steps {
                sh '/path/to/monitor-disk-space.sh'
            }
        }
    }
}
```

---

## Troubleshooting

### Problema: Jobs falham com "image not found"

**Sintomas:**
```
Error response from daemon: pull access denied for jenkins/agent
Cannot connect to the Docker daemon
```

**Solução:**
```bash
# 1. Verificar se imagem existe
docker images | grep jenkins

# 2. Se não existir, fazer pull
docker pull jenkins/agent:latest

# 3. Verificar conectividade Docker
docker ps

# 4. Testar criação de container
docker run --rm jenkins/agent:latest java -version
```

### Problema: Cleanup apagou tudo

**Solução:**
```bash
# 1. Executar script de recuperação
/path/to/emergency-recovery.sh

# 2. Verificar imagens recuperadas
docker images

# 3. Testar Jenkins
# Criar job de teste simples

# 4. Prevenir recorrência
# Implementar pipeline segura
```

### Problema: Disco continua cheio após cleanup

**Investigação:**
```bash
# Verificar o que está ocupando espaço
docker system df -v

# Verificar diretório Docker
du -sh /var/lib/docker/*

# Verificar logs
du -sh /var/lib/docker/containers/*/

# Limpar build cache
docker builder prune -a -f
```

---

## Recursos e Referências

### Documentação Oficial
- [Docker Prune Documentation](https://docs.docker.com/config/pruning/)
- [Jenkins Docker Plugin](https://plugins.jenkins.io/docker-plugin/)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)

### Scripts Fornecidos
- **Pipeline Segura:** `/jenkins/pipelines/safe-docker-cleanup.groovy`
- **Verificação:** `/jenkins/scripts/verify-agent-images.sh`
- **Recuperação:** `/jenkins/scripts/emergency-recovery.sh`
- **Monitorização:** `/jenkins/scripts/monitor-disk-space.sh`

### Comandos Úteis

```bash
# Ver espaço usado
docker system df

# Ver detalhes
docker system df -v

# Listar imagens por tamanho
docker images --format "{{.Size}}\t{{.Repository}}:{{.Tag}}" | sort -h

# Containers por tamanho
docker ps -as

# Volumes por tamanho
docker volume ls

# Ver histórico de layers
docker history <image>

# Inspecionar imagem
docker inspect <image>
```

---

## Conclusão

### Princípios-chave

1. **Nunca usar `docker image prune -a` em produção sem filtros**
2. **Sempre proteger imagens críticas**
3. **Fazer dry-run antes de executar cleanup**
4. **Monitorizar espaço proativamente**
5. **Ter plano de recuperação documentado**
6. **Testar após cada cleanup**
7. **Manter logs de todas as operações**

### Implementação Recomendada

1. Substituir pipeline atual pela pipeline segura
2. Configurar monitorização diária
3. Executar verificação de imagens antes de cada cleanup
4. Manter backup de imagens críticas
5. Documentar todas as imagens necessárias
6. Treinar equipe em procedimentos de recuperação

---

**Última atualização:** 2026-01-28
**Versão:** 1.0
**Autor:** DevOps Team
