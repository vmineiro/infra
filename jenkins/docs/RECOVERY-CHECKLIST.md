# Checklist de Recuperação - Jenkins Docker Agents

## Situação: Imagens de Agentes Apagadas Acidentalmente

### 1. Diagnóstico Inicial

#### 1.1 Verificar Sintomas
- [ ] Jobs Jenkins falham com erro "Cannot create container"
- [ ] Logs mostram "No such image" ou "image not found"
- [ ] Dashboard Jenkins mostra agentes offline ou indisponíveis

#### 1.2 Confirmar Problema
```bash
# Verificar se imagens Jenkins estão presentes
docker images | grep jenkins

# Verificar logs de jobs falhados
# Via Jenkins UI: Job > Console Output

# Verificar última execução da pipeline de cleanup
# Via Jenkins UI: docker-cleanup-pipeline > Build History
```

#### 1.3 Identificar Causa Raiz
- [ ] Verificar quando foi a última execução da pipeline de cleanup
- [ ] Verificar logs da pipeline de cleanup
- [ ] Confirmar que `docker image prune -a` foi executado
- [ ] Verificar se `docker system prune -a` foi executado

---

### 2. Recuperação Imediata

#### 2.1 Executar Script de Verificação
```bash
# Tornar script executável
chmod +x /path/to/jenkins/scripts/verify-agent-images.sh

# Executar verificação
./verify-agent-images.sh

# Ou com pull automático
./verify-agent-images.sh --auto-pull
```

#### 2.2 Executar Recuperação de Emergência
```bash
# Tornar script executável
chmod +x /path/to/jenkins/scripts/emergency-recovery.sh

# Executar recuperação
./emergency-recovery.sh
```

**Checklist de Execução:**
- [ ] Script identificou imagens em falta
- [ ] Script fez pull de todas as imagens necessárias
- [ ] Todas as imagens foram baixadas com sucesso
- [ ] Nenhum erro de network/registry
- [ ] Log de recuperação foi salvo

#### 2.3 Verificar Imagens Recuperadas
```bash
# Listar todas as imagens Jenkins
docker images | grep -E "jenkins|maven|node|python|openjdk"

# Verificar tamanho ocupado
docker system df
```

**Imagens Críticas Esperadas:**
- [ ] `jenkins/agent:latest`
- [ ] `jenkins/inbound-agent:latest`
- [ ] `jenkins/ssh-agent:latest`
- [ ] `maven:3.9-eclipse-temurin-17`
- [ ] `node:18-alpine`
- [ ] `python:3.11-slim`
- [ ] Outras imagens específicas do seu ambiente

---

### 3. Testes de Validação

#### 3.1 Testar Criação de Agente
```groovy
// Criar job de teste no Jenkins
pipeline {
    agent {
        docker {
            image 'maven:3.9-eclipse-temurin-17'
        }
    }
    stages {
        stage('Test') {
            steps {
                sh 'mvn --version'
            }
        }
    }
}
```

**Validações:**
- [ ] Job executa sem erros
- [ ] Container é criado com sucesso
- [ ] Agent conecta e executa comandos
- [ ] Container é removido após execução

#### 3.2 Testar Múltiplos Tipos de Agentes
```bash
# Executar jobs com diferentes imagens
# Node.js
# Python
# Maven
# Docker-in-Docker
```

- [ ] Todos os tipos de agentes funcionam
- [ ] Não há erros de "image not found"
- [ ] Performance está normal

#### 3.3 Verificar Logs
```bash
# Logs do Docker daemon
journalctl -u docker -n 100 --no-pager

# Logs do Jenkins
tail -f /var/jenkins_home/logs/jenkins.log
```

- [ ] Sem erros relacionados a Docker
- [ ] Sem erros de criação de containers
- [ ] Agents sendo criados e destruídos normalmente

---

### 4. Prevenir Futuras Ocorrências

#### 4.1 Substituir Pipeline de Cleanup
- [ ] Backup da pipeline antiga
- [ ] Implementar nova pipeline segura (`safe-docker-cleanup.groovy`)
- [ ] Testar nova pipeline em ambiente de teste
- [ ] Revisar variáveis de ambiente (PROTECTED_IMAGES)
- [ ] Ajustar dias de retenção conforme necessário

#### 4.2 Configurar Proteção de Imagens
```bash
# Criar job Jenkins para proteger imagens
# Executar diariamente ANTES da pipeline de cleanup
```

**Job de Proteção:**
```groovy
pipeline {
    agent any
    triggers {
        cron('0 1 * * 6')  // 1:00 AM sábado (antes do cleanup às 2:00)
    }
    stages {
        stage('Tag Protected Images') {
            steps {
                sh '''
                    # Adicionar label às imagens críticas
                    docker images --format "{{.ID}}" --filter "reference=jenkins/*" | \
                        xargs -I {} docker image inspect {} --format '{{.Id}}'
                '''
            }
        }
    }
}
```

- [ ] Job de proteção criado
- [ ] Executa ANTES do cleanup
- [ ] Testado e funcionando

#### 4.3 Implementar Monitorização

**Monitorizar:**
- [ ] Espaço em disco disponível
- [ ] Número de imagens Docker
- [ ] Tamanho total de imagens
- [ ] Imagens críticas presentes

**Script de Monitorização:**
```bash
#!/bin/bash
# /usr/local/bin/monitor-docker-health.sh

THRESHOLD_PERCENT=80
CURRENT_USAGE=$(df /var/lib/docker | tail -1 | awk '{print $5}' | sed 's/%//')

if [ "$CURRENT_USAGE" -gt "$THRESHOLD_PERCENT" ]; then
    echo "ALERTA: Disco em $CURRENT_USAGE%"
    # Enviar notificação
fi

# Verificar imagens críticas
REQUIRED="jenkins/agent jenkins/inbound-agent maven:3.9"
for img in $REQUIRED; do
    if ! docker images --format "{{.Repository}}" | grep -q "$img"; then
        echo "ALERTA: Imagem crítica em falta: $img"
        # Enviar notificação
    fi
done
```

- [ ] Script de monitorização criado
- [ ] Configurado no cron
- [ ] Alertas configurados (email, Slack, etc.)

#### 4.4 Documentação
- [ ] Documentar imagens necessárias para o ambiente
- [ ] Documentar processo de recuperação
- [ ] Criar runbook para equipe de operações
- [ ] Documentar políticas de retenção

---

### 5. Configurações Adicionais

#### 5.1 Docker Daemon Configuration
```json
// /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "data-root": "/var/lib/docker"
}
```

- [ ] Configuração de logs otimizada
- [ ] Storage driver adequado
- [ ] Reiniciar Docker daemon se alterado

#### 5.2 Jenkins Configuration

**System Configuration:**
- [ ] Docker Cloud configurado corretamente
- [ ] Templates de agentes definidos
- [ ] Imagens explicitamente especificadas (não "latest" em produção)
- [ ] Política de pull configurada (Always/IfNotPresent/Never)

**Docker Cloud Settings:**
```
Docker Host URI: unix:///var/run/docker.sock
Pull Strategy: PULL_IF_NOT_PRESENT  ← IMPORTANTE
Container Cap: 10
```

#### 5.3 Backup de Imagens Críticas

```bash
# Script para fazer backup de imagens
#!/bin/bash
# /usr/local/bin/backup-jenkins-images.sh

BACKUP_DIR="/var/backups/docker-images"
mkdir -p "$BACKUP_DIR"

IMAGES=(
    "jenkins/agent:latest"
    "jenkins/inbound-agent:latest"
    "maven:3.9-eclipse-temurin-17"
)

for img in "${IMAGES[@]}"; do
    filename=$(echo "$img" | tr '/:' '_')
    docker save "$img" | gzip > "$BACKUP_DIR/${filename}.tar.gz"
done
```

- [ ] Script de backup criado
- [ ] Executar mensalmente
- [ ] Armazenar em local seguro
- [ ] Testar restauração

---

### 6. Testes Pós-Recuperação

#### 6.1 Teste de Carga
```bash
# Criar múltiplos jobs simultaneamente
# Verificar se agentes são criados corretamente
# Monitorar recursos do sistema
```

- [ ] Sistema suporta carga normal
- [ ] Múltiplos agentes podem ser criados
- [ ] Performance aceitável
- [ ] Sem erros ou timeouts

#### 6.2 Teste de Cleanup Seguro
```bash
# Executar nova pipeline de cleanup manualmente
# Verificar logs
# Confirmar que imagens protegidas NÃO foram apagadas
```

- [ ] Cleanup executa com sucesso
- [ ] Imagens protegidas preservadas
- [ ] Relatórios gerados corretamente
- [ ] Espaço foi liberado de forma segura

---

### 7. Relatório Final

#### 7.1 Documentar Incidente
```markdown
## Incidente: Imagens Docker Apagadas

**Data:** YYYY-MM-DD
**Duração:** X horas
**Impacto:** Jobs Jenkins falharam

**Causa Raiz:**
- Pipeline de cleanup usava `docker image prune -a`
- Comando apagou imagens de agentes não em uso

**Resolução:**
1. Executado script de recuperação
2. Imagens restauradas via docker pull
3. Pipeline de cleanup substituída
4. Proteções implementadas

**Prevenção:**
- Nova pipeline com filtros de proteção
- Monitorização de imagens críticas
- Documentação atualizada
```

#### 7.2 Comunicação
- [ ] Notificar equipe da resolução
- [ ] Atualizar documentação
- [ ] Compartilhar lições aprendidas
- [ ] Revisar processos

#### 7.3 Follow-up
- [ ] Agendar revisão em 1 semana
- [ ] Verificar se problema não recorreu
- [ ] Validar que monitorização está funcionando
- [ ] Coletar feedback da equipe

---

## Contatos de Emergência

- **DevOps Lead:** [Nome] - [Email/Telefone]
- **Infraestrutura:** [Nome] - [Email/Telefone]
- **On-call:** [Número/Slack]

## Recursos Úteis

- **Documentação Docker:** https://docs.docker.com/engine/reference/commandline/image_prune/
- **Jenkins Docker Plugin:** https://plugins.jenkins.io/docker-plugin/
- **Scripts de Recuperação:** `/path/to/jenkins/scripts/`
- **Logs de Cleanup:** `/var/jenkins_home/cleanup-reports/`

---

## Status Atual

- [ ] Problema diagnosticado
- [ ] Imagens recuperadas
- [ ] Testes validados
- [ ] Prevenção implementada
- [ ] Documentação atualizada
- [ ] Equipe notificada
- [ ] Incidente encerrado

**Data de Conclusão:** __________
**Responsável:** __________
**Validado por:** __________
