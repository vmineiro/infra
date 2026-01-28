# Docker Cleanup - Quick Reference Card

## 🚨 COMANDOS PERIGOSOS - NUNCA USAR EM PRODUÇÃO

```bash
# ❌ PERIGOSO: Apaga TODAS as imagens sem containers
docker image prune -a -f

# ❌ PERIGOSO: Apaga tudo (imagens, volumes, networks)
docker system prune -a -f --volumes

# ❌ PERIGOSO: Remove volumes (possível perda de dados)
docker volume prune -f
```

---

## ✅ COMANDOS SEGUROS

### Limpeza Básica (Sempre Seguro)
```bash
# Remove apenas imagens dangling (<none>:<none>)
docker image prune -f

# Remove containers parados há mais de 7 dias
docker container prune -f --filter "until=168h"

# Remove networks não usadas
docker network prune -f

# Remove build cache antigo
docker builder prune -f --filter "until=168h"
```

### Verificações Antes de Apagar
```bash
# Ver o que SERIA apagado (dry-run, sem -f)
docker image prune -a --filter "until=720h"
docker container prune --filter "until=168h"

# Ver espaço atual
docker system df
docker system df -v  # detalhado

# Listar imagens por tamanho
docker images --format "{{.Size}}\t{{.Repository}}:{{.Tag}}" | sort -h

# Ver containers parados
docker ps -a --filter "status=exited"
```

---

## 🛡️ VERIFICAR IMAGENS CRÍTICAS

```bash
# Listar imagens Jenkins
docker images | grep -E "jenkins|maven|node|python|openjdk"

# Verificar imagem específica
docker images jenkins/agent

# Verificar se imagem existe (script)
if docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^jenkins/agent:latest$"; then
    echo "✅ Presente"
else
    echo "❌ Em falta"
fi
```

---

## 🔧 RECUPERAÇÃO RÁPIDA

### Se Imagens foram Apagadas
```bash
# Pull de imagens críticas
docker pull jenkins/agent:latest
docker pull jenkins/inbound-agent:latest
docker pull maven:3.9-eclipse-temurin-17
docker pull node:18-alpine
docker pull python:3.11-slim

# Ou usar script de recuperação
cd /path/to/jenkins/scripts
chmod +x emergency-recovery.sh
./emergency-recovery.sh
```

### Verificar se Jenkins Funciona
```bash
# Testar criação de container
docker run --rm jenkins/agent:latest java -version

# Ver logs Jenkins
tail -f /var/jenkins_home/logs/jenkins.log

# Ver logs Docker
journalctl -u docker -n 50 --no-pager
```

---

## 📊 MONITORIZAÇÃO

### Verificar Espaço
```bash
# Espaço em disco
df -h /var/lib/docker

# Resumo Docker
docker system df

# Detalhado
docker system df -v

# Por diretório
du -sh /var/lib/docker/*
```

### Alertas Simples
```bash
# Verificar uso de disco
USAGE=$(df /var/lib/docker | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$USAGE" -gt 80 ]; then
    echo "ALERTA: Disco em ${USAGE}%"
fi

# Verificar imagens críticas
for img in jenkins/agent maven:3.9 node:18-alpine; do
    if ! docker images | grep -q "$img"; then
        echo "ALERTA: $img em falta"
    fi
done
```

---

## 🔄 SCRIPTS DISPONÍVEIS

### Localização
```
/Users/vitormineiro/Dev/VitorMineiro/ServerInfra/infra/jenkins/
├── pipelines/
│   ├── safe-docker-cleanup.groovy       # Pipeline segura
│   └── protect-critical-images.groovy   # Proteção de imagens
├── scripts/
│   ├── verify-agent-images.sh           # Verificação
│   ├── emergency-recovery.sh            # Recuperação
│   └── monitor-disk-space.sh            # Monitorização
└── docs/
    ├── RECOVERY-CHECKLIST.md            # Checklist completo
    ├── DOCKER-CLEANUP-BEST-PRACTICES.md # Guia completo
    └── QUICK-REFERENCE.md               # Este arquivo
```

### Uso Rápido
```bash
# Tornar scripts executáveis
chmod +x /path/to/jenkins/scripts/*.sh

# Verificar imagens
./verify-agent-images.sh

# Recuperar imagens (com confirmação)
./emergency-recovery.sh

# Recuperar automaticamente (sem perguntar)
./emergency-recovery.sh --auto

# Monitorar espaço
./monitor-disk-space.sh
```

---

## 📋 CHECKLIST DE EMERGÊNCIA

Se Jenkins não consegue criar agentes:

1. **Diagnosticar**
   ```bash
   docker images | grep jenkins
   docker ps -a | head -5
   tail -20 /var/jenkins_home/logs/jenkins.log
   ```

2. **Recuperar Imagens**
   ```bash
   docker pull jenkins/agent:latest
   docker pull jenkins/inbound-agent:latest
   # Adicionar outras conforme necessário
   ```

3. **Testar**
   ```bash
   docker run --rm jenkins/agent:latest java -version
   ```

4. **Verificar Jenkins**
   - Ir para Jenkins UI
   - Criar job de teste simples
   - Verificar se agent é criado

5. **Prevenir Recorrência**
   - Implementar pipeline segura
   - Configurar proteção de imagens
   - Ativar monitorização

---

## 🎯 FILTROS ÚTEIS

### Por Tempo
```bash
# Imagens com mais de 30 dias
docker image prune -a -f --filter "until=720h"

# Containers parados há mais de 7 dias
docker container prune -f --filter "until=168h"

# Build cache com mais de 14 dias
docker builder prune -f --filter "until=336h"
```

### Por Label
```bash
# Remover apenas não-protegidas
docker image prune -a -f --filter "label!=preserve=true"

# Ver imagens com label
docker images --filter "label=preserve=true"
```

### Por Pattern (Manual)
```bash
# Listar imagens NÃO protegidas
docker images --format "{{.Repository}}:{{.Tag}}" | \
  grep -vE "jenkins|maven|node|python|openjdk"

# Remover imagens específicas (cuidado!)
docker rmi $(docker images --filter "reference=*test*" -q)
```

---

## ⚙️ CONFIGURAÇÕES JENKINS

### Docker Cloud Settings
```
Jenkins > Manage Jenkins > Configure Clouds

Docker Host URI: unix:///var/run/docker.sock
Pull Strategy: PULL_IF_NOT_PRESENT  ← IMPORTANTE
Container Cap: 10
```

### Build Retention
```groovy
// No Jenkinsfile
options {
    buildDiscarder(logRotator(
        numToKeepStr: '30',
        daysToKeepStr: '90'
    ))
}
```

---

## 🔍 TROUBLESHOOTING RÁPIDO

### Problema: "No such image"
```bash
# Causa: Imagem foi apagada
# Solução:
docker pull <image_name>
```

### Problema: Disco cheio
```bash
# Verificar o que está ocupando
docker system df -v

# Limpeza segura
docker image prune -f
docker container prune -f --filter "until=168h"
docker builder prune -f
```

### Problema: Container não inicia
```bash
# Ver logs
docker logs <container_id>

# Ver eventos
docker events --since 1h

# Inspecionar
docker inspect <container_id>
```

---

## 📞 CONTATOS E RECURSOS

### Documentação
- Guia Completo: `/jenkins/docs/DOCKER-CLEANUP-BEST-PRACTICES.md`
- Checklist Recuperação: `/jenkins/docs/RECOVERY-CHECKLIST.md`
- Docker Docs: https://docs.docker.com/config/pruning/

### Scripts
- Verificação: `/jenkins/scripts/verify-agent-images.sh`
- Recuperação: `/jenkins/scripts/emergency-recovery.sh`
- Monitor: `/jenkins/scripts/monitor-disk-space.sh`

### Pipelines Jenkins
- Cleanup Seguro: `safe-docker-cleanup.groovy`
- Proteção: `protect-critical-images.groovy`

---

## 💡 DICAS RÁPIDAS

1. **Sempre fazer dry-run primeiro** (omitir `-f`)
2. **Nunca usar `-a` sem filtros de proteção**
3. **Verificar imagens críticas antes E depois do cleanup**
4. **Manter containers dummy para proteger imagens**
5. **Monitorar espaço proativamente (não reativamente)**
6. **Documentar quais imagens são necessárias**
7. **Ter plano de recuperação testado**

---

## ⏰ ROTINA RECOMENDADA

### Diário (Automático)
- ✅ Monitorar espaço em disco
- ✅ Verificar imagens críticas presentes
- ✅ Verificar containers de proteção

### Semanal (Automático)
- ✅ Limpar imagens dangling
- ✅ Limpar containers parados (> 7 dias)
- ✅ Limpar build cache
- ✅ Gerar relatório

### Mensal (Manual)
- ✅ Revisar imagens não usadas
- ✅ Backup de imagens críticas
- ✅ Atualizar base images
- ✅ Revisar políticas de retenção

---

**Última atualização:** 2026-01-28
**Versão:** 1.0
**Para emergências:** Execute `/jenkins/scripts/emergency-recovery.sh`
