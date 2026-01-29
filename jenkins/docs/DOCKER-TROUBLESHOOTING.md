# Docker Troubleshooting Guide - Jenkins

Guia completo de troubleshooting e comandos para gerir o Docker quando há problemas com o Jenkins.

---

## Diagnóstico Rápido

### 1. Verificar Estado Geral

```bash
# Ver todos os containers (incluindo parados)
docker ps -a

# Ver apenas containers Jenkins
docker ps | grep jenkins

# Ver uso de recursos Docker
docker system df

# Ver containers em crash loop (PROBLEMA!)
docker ps -a | grep -i restart
```

### 2. Verificar Containers de Proteção (Keepers)

```bash
# Ver todos os keepers
docker ps --filter "label=purpose=image-protection"

# Ver keepers em crash loop (NÃO DEVE HAVER!)
docker ps -a --filter "label=purpose=image-protection" | grep -i restart
```

### 3. Verificar Imagens Críticas

```bash
# Verificar imagens dos agentes Jenkins
docker images | grep -E "jenkins-node-agent|jenkins-python-agent|jenkins/agent"

# Ver todas as imagens jenkins
docker images | grep jenkins
```

### 4. Verificar Logs

```bash
# Logs do container Jenkins principal
docker logs jenkins --tail 50

# Logs de um keeper específico
docker logs keeper-jenkins-node-agent-latest --tail 50

# Logs do Docker daemon (sistema)
sudo journalctl -u docker.service --since "10 minutes ago"
```

---

## Problemas Comuns e Soluções

### Problema 1: Docker Está Repetidamente a Parar

**Sintomas:**
- Perda de acesso ao Jenkins
- Containers param sozinhos
- Docker daemon reinicia constantemente

**Diagnóstico:**
```bash
# Identificar containers em crash loop
docker ps -a | grep -i restart

# Ver logs do container problemático
docker logs <nome-do-container> --tail 100
```

**Solução:**
```bash
# Remover container em crash loop
docker rm -f <nome-do-container>

# Exemplo: remover keeper problemático
docker rm -f keeper-jenkins-inbound-agent-latest

# Verificar se Docker estabilizou
docker ps | grep jenkins
```

### Problema 2: Container Keeper em Crash Loop

**Sintomas:**
- Container com status "Restarting (1)" ou similar
- Docker instável
- Jenkins perde agentes

**Identificar o problema:**
```bash
# Listar containers de proteção com status
docker ps -a --filter "label=purpose=image-protection" \
  --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
```

**Solução:**
```bash
# Remover todos os keepers problemáticos
docker ps -a --filter "label=purpose=image-protection" \
  --filter "status=restarting" \
  --format "{{.Names}}" | xargs -r docker rm -f

# Ou remover keeper específico
docker rm -f keeper-jenkins-inbound-agent-latest
```

### Problema 3: Jenkins Não Consegue Criar Agentes

**Sintomas:**
- Jobs falham com "No such image"
- Erro: "node-agent-xxxxx seems to be removed or offline"

**Diagnóstico:**
```bash
# Verificar se imagens dos agentes existem
docker images | grep jenkins-node-agent
docker images | grep jenkins-python-agent
docker images | grep jenkins/agent

# Ver se há containers em crash que afetem Docker
docker ps -a | grep -i restart
```

**Solução:**
```bash
# Se imagem em falta, fazer pull
docker pull jenkins-node-agent:latest
docker pull jenkins-python-agent:latest
docker pull jenkins/agent:alpine-jdk17

# Se Docker instável, remover containers problemáticos
docker rm -f $(docker ps -a -q --filter "status=restarting")

# Reiniciar Jenkins se necessário
docker restart jenkins
```

### Problema 4: Espaço em Disco Cheio

**Diagnóstico:**
```bash
# Ver uso de disco Docker
docker system df

# Ver uso de disco Jenkins Home
du -sh /var/jenkins_home

# Ver espaço total disponível
df -h
```

**Solução:**
```bash
# Executar cleanup manual (SEGURO)
docker image prune -f        # Remove dangling images
docker volume prune -f       # Remove volumes não usados
docker network prune -f      # Remove networks não usadas
docker builder prune -f      # Remove build cache

# Ou executar pipeline de cleanup
# Jenkins UI → docker-cleanup-safe → Build Now
```

---

## Comandos de Controlo do Docker

### Parar Docker Daemon

⚠️ **ATENÇÃO:** Isto para TODOS os containers!

```bash
# Systemd (Ubuntu/Debian/CentOS 7+)
sudo systemctl stop docker

# SysV Init (sistemas antigos)
sudo service docker stop

# Verificar que parou
sudo systemctl status docker
```

### Reiniciar Docker Daemon

```bash
# Systemd
sudo systemctl restart docker

# SysV Init
sudo service docker restart

# Verificar que iniciou
sudo systemctl status docker

# Ver se containers voltaram
docker ps
```

### Iniciar Docker Daemon

```bash
# Systemd
sudo systemctl start docker

# SysV Init
sudo service docker start
```

### Recarregar Configuração (sem parar containers)

```bash
# Recarregar daemon config sem interromper serviço
sudo systemctl reload docker
```

---

## Comandos de Gestão de Containers

### Parar Containers

```bash
# Parar container Jenkins
docker stop jenkins

# Parar todos os keepers
docker stop $(docker ps -q --filter "label=purpose=image-protection")

# Parar TODOS os containers
docker stop $(docker ps -q)
```

### Iniciar Containers

```bash
# Iniciar container Jenkins
docker start jenkins

# Iniciar todos os keepers
docker start $(docker ps -aq --filter "label=purpose=image-protection")
```

### Reiniciar Containers

```bash
# Reiniciar Jenkins
docker restart jenkins

# Reiniciar keeper específico
docker restart keeper-jenkins-node-agent-latest
```

### Remover Containers

```bash
# Remover container parado
docker rm <nome-do-container>

# Forçar remoção (mesmo se em execução)
docker rm -f <nome-do-container>

# Remover TODOS os containers parados
docker container prune -f

# Remover containers em crash loop
docker rm -f $(docker ps -aq --filter "status=restarting")
```

---

## Recovery Procedures

### Recovery Completo: Docker Não Responde

```bash
# 1. Forçar paragem de todos os containers
sudo killall -9 dockerd
sudo killall -9 docker-containerd

# 2. Reiniciar Docker daemon
sudo systemctl restart docker

# 3. Verificar que iniciou
sudo systemctl status docker

# 4. Verificar containers
docker ps -a

# 5. Iniciar Jenkins se parou
docker start jenkins

# 6. Verificar que Jenkins está UP
docker ps | grep jenkins
```

### Recovery: Jenkins Está a Falhar

```bash
# 1. Ver logs para identificar problema
docker logs jenkins --tail 100

# 2. Verificar se há keepers em crash
docker ps -a | grep -i restart

# 3. Remover keepers problemáticos
docker rm -f $(docker ps -aq --filter "status=restarting")

# 4. Reiniciar Jenkins
docker restart jenkins

# 5. Esperar 30 segundos e verificar
sleep 30
docker ps | grep jenkins
```

### Recovery: Imagens Críticas Apagadas

```bash
# 1. Executar script de emergency recovery
cd /path/to/jenkins/scripts
./emergency-recovery.sh

# 2. Ou fazer pull manual
docker pull jenkins/jenkins:lts
docker pull jenkins/agent:alpine-jdk17
docker pull jenkins-node-agent:latest
docker pull jenkins-python-agent:latest
docker pull jenkins/agent:latest

# 3. Verificar que imagens foram recuperadas
docker images | grep jenkins

# 4. Executar pipeline de proteção
# Jenkins UI → docker-cleanup-safe → Build Now
```

### Recovery: Pipeline de Cleanup Apagou Imagens

```bash
# 1. PARAR pipeline imediatamente
# Jenkins UI → docker-cleanup-safe → Desativar

# 2. Recuperar imagens críticas
docker pull jenkins-node-agent:latest
docker pull jenkins-python-agent:latest
docker pull jenkins/agent:alpine-jdk17

# 3. Verificar script verify-agent-images.sh
cd /path/to/jenkins/scripts
./verify-agent-images.sh

# 4. Revisar configuração da pipeline
# Verificar que CRITICAL_IMAGES contém as imagens corretas
# Verificar que usa "docker image prune -f" (sem -a!)

# 5. Reativar pipeline após confirmar correção
```

---

## Monitorização e Prevenção

### Verificação de Saúde (Executar Periodicamente)

```bash
#!/bin/bash
# health-check.sh

echo "=== Docker Health Check ==="
echo ""

echo "1. Docker Daemon Status:"
sudo systemctl status docker | grep Active

echo ""
echo "2. Jenkins Container Status:"
docker ps | grep jenkins

echo ""
echo "3. Containers em Crash Loop (NÃO DEVE HAVER!):"
docker ps -a | grep -i restart || echo "✅ Nenhum"

echo ""
echo "4. Keepers de Proteção:"
docker ps --filter "label=purpose=image-protection" \
  --format "table {{.Names}}\t{{.Status}}"

echo ""
echo "5. Imagens Críticas:"
docker images | grep -E "jenkins-node-agent|jenkins-python-agent|jenkins/agent" \
  --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

echo ""
echo "6. Uso de Disco Docker:"
docker system df

echo ""
echo "✅ Health check completo!"
```

### Alertas a Configurar

1. **Monitorizar uptime Jenkins:**
   ```bash
   # Deve aumentar continuamente
   docker ps | grep jenkins
   ```

2. **Alertar se containers restarting:**
   ```bash
   # Não deve retornar nada
   docker ps -a | grep -i restart
   ```

3. **Alertar se espaço < 20%:**
   ```bash
   docker system df
   ```

---

## Comandos Úteis de Diagnóstico

### Informação do Sistema

```bash
# Versão Docker
docker --version

# Informação detalhada Docker
docker info

# Verificar recursos (CPU, RAM)
docker stats --no-stream

# Ver processos Docker
ps aux | grep docker
```

### Inspecionar Containers

```bash
# Detalhes completos de um container
docker inspect jenkins

# Ver apenas IP do container
docker inspect jenkins | grep IPAddress

# Ver volumes montados
docker inspect jenkins | grep -A 20 Mounts

# Ver variáveis de ambiente
docker inspect jenkins | grep -A 50 Env
```

### Logs Avançados

```bash
# Logs em tempo real (follow)
docker logs -f jenkins

# Logs com timestamps
docker logs --timestamps jenkins

# Últimas 100 linhas
docker logs --tail 100 jenkins

# Logs desde tempo específico
docker logs --since "2024-01-29T10:00:00" jenkins
```

### Executar Comandos Dentro do Container

```bash
# Shell interativo no Jenkins
docker exec -it jenkins bash

# Executar comando único
docker exec jenkins cat /var/jenkins_home/config.xml

# Verificar espaço dentro do container
docker exec jenkins df -h

# Ver processos dentro do container
docker exec jenkins ps aux
```

---

## Referências e Documentação

### Ficheiros Importantes

| Ficheiro | Localização | Propósito |
|----------|-------------|-----------|
| Pipeline Unificada | `jenkins/pipelines/docker-cleanup-unified.groovy` | Proteção + Cleanup seguro |
| Emergency Recovery | `jenkins/scripts/emergency-recovery.sh` | Recuperar imagens apagadas |
| Verify Images | `jenkins/scripts/verify-agent-images.sh` | Verificar imagens críticas |
| Disk Monitor | `jenkins/scripts/monitor-disk-space.sh` | Alertas de espaço |

### Imagens Críticas Protegidas

- `jenkins/jenkins:lts` - Container principal Jenkins
- `jenkins/agent:alpine-jdk17` - Base dos agentes custom
- `jenkins-node-agent:latest` - Agente Node.js (custom)
- `jenkins-python-agent:latest` - Agente Python (custom)
- `jenkins/agent:latest` - Agente genérico

### Imagens que NÃO Devem Ter Keepers

⚠️ Estas imagens **crasham** quando usadas em keepers:
- `jenkins/inbound-agent:latest` - CAUSA CRASH LOOP
- `jenkins/ssh-agent:latest` - POTENCIAL PROBLEMA

### Contactos e Escalação

- **Documentação:** `jenkins/docs/`
- **GitHub Repo:** `https://github.com/vmineiro/infra`
- **Pipeline Schedule:** Sábado 2:00 AM
- **Logs Jenkins:** `/var/jenkins_home/cleanup-reports/`

---

## Quick Reference Card

```bash
# DIAGNÓSTICO RÁPIDO
docker ps -a | grep -i restart        # Ver crash loops
docker ps | grep jenkins              # Ver Jenkins uptime
docker system df                      # Ver espaço usado

# RECUPERAÇÃO RÁPIDA
docker rm -f <container-name>         # Remover container problemático
docker restart jenkins                # Reiniciar Jenkins
sudo systemctl restart docker         # Reiniciar Docker daemon

# LIMPEZA SEGURA
docker image prune -f                 # Apenas dangling images
docker container prune -f             # Containers parados
docker volume prune -f                # Volumes não usados

# EMERGENCY
./jenkins/scripts/emergency-recovery.sh   # Recuperar imagens
docker pull jenkins-node-agent:latest     # Pull imagem específica
docker stop $(docker ps -q)               # PARAR TUDO
```

---

## Changelog

| Data | Versão | Alteração |
|------|--------|-----------|
| 2026-01-29 | 1.0 | Documento inicial criado |

---

**Próxima Revisão:** Trimestral ou após incidentes críticos
