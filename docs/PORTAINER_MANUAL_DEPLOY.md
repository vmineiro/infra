# Deploy Manual - Portainer Staging

## 🎯 Quando Usar Este Guia

Use este processo para fazer deploy manual de updates quando os Gitea Actions workflows não estiverem funcionais.

---

## 📋 Processo de Deploy Manual

### 1. Build da Imagem Docker (No MacBook Air Server)

```bash
# SSH para o servidor
ssh vitormineiro@192.168.1.74

# Ir para o diretório do projeto
cd ~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/

# Pull das últimas mudanças do Gitea
git pull gitea main

# Build da nova imagem
docker build -t basedatafeed:staging .

# Verificar imagem criada
docker images | grep basedatafeed
```

**Tempo estimado:** 2-3 minutos

---

### 2. Restart do Container (Aplicar Nova Imagem)

```bash
# Restart do container da aplicação
docker restart basedatafeed-staging-app

# Aguardar health check
sleep 15

# Verificar status
docker ps --format "table {{.Names}}\t{{.Status}}" | grep staging
```

**Resultado esperado:**
```
basedatafeed-staging-app       Up X seconds (healthy)
basedatafeed-staging-db        Up X minutes (healthy)
basedatafeed-staging-pgadmin   Up X minutes
```

---

### 3. Verificar Logs

```bash
# Ver logs da aplicação
docker logs basedatafeed-staging-app --tail 50

# Procurar por:
# ✅ "Health check passed"
# ✅ "Container initialization completed"
# ✅ "Starting application..."
# ❌ "ERROR", "FATAL", "failed"
```

---

### 4. Testar Aplicação

**No browser (do teu laptop):**

**PgAdmin:**
```
http://192.168.1.74:5051
```
- Verificar que consegues aceder
- Conectar à base de dados (se ainda não adicionaste):
  - Host: `db-staging`
  - Port: `5432`
  - Database: `basedata-staging`
  - Username: `postgres`
  - Password: `otEzwYT7vMCf7ZMonoe/psfKBAND7oIP`

**App (se tiver endpoint web):**
```
http://192.168.1.74:8081
```

---

## 🔄 Deploy Completo com Rebuild (Opcional)

Se houver mudanças no docker-compose ou variáveis de ambiente:

```bash
# No servidor (MacBook Air)
cd ~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/

# Build da imagem
docker build -t basedatafeed:staging .

# Depois no Portainer UI:
# http://localhost:9000 → Local → Stacks → base-analysis-staging
# Click "Pull and redeploy"
# OU
# Stop → Start (se mudaste env vars)
```

---

## 🐛 Troubleshooting

### Container não inicia (Exit code)

```bash
# Ver logs completos
docker logs basedatafeed-staging-app

# Ver últimos 100 linhas
docker logs basedatafeed-staging-app --tail 100

# Logs em tempo real
docker logs -f basedatafeed-staging-app
```

**Problemas comuns:**
- Database connection failed → Verificar DB_PASSWORD
- File not found → Verificar build incluiu todos os ficheiros
- Permission denied → Verificar volumes e permissions

---

### Base de Dados não responde

```bash
# Verificar DB está healthy
docker ps | grep staging-db

# Testar conexão
docker exec basedatafeed-staging-db psql -U postgres -d basedata-staging -c "SELECT version();"

# Ver logs da DB
docker logs basedatafeed-staging-db --tail 50
```

---

### PgAdmin em crash loop

```bash
# Ver logs
docker logs basedatafeed-staging-pgadmin --tail 30

# Verificar email é válido (não .local)
docker inspect basedatafeed-staging-pgadmin | grep PGADMIN_DEFAULT_EMAIL

# Se necessário, atualizar no Portainer:
# Stacks → base-analysis-staging → Editor → Environment variables
# Mudar PGADMIN_EMAIL para domínio válido
```

---

## 📊 Comandos Úteis

```bash
# Ver todos os containers staging
docker ps | grep staging

# Ver recursos usados
docker stats --no-stream | grep staging

# Ver volumes
docker volume ls | grep staging

# Inspecionar network
docker network inspect BaseAnalysis-Staging

# Entrar no container (debug)
docker exec -it basedatafeed-staging-app bash

# Executar comando na app
docker exec basedatafeed-staging-app python /app/source/main.py --help
```

---

## 🔐 Credenciais

**Base de Dados:**
- Host: `db-staging` (interno) ou `192.168.1.74:5433` (externo)
- Database: `basedata-staging`
- Username: `postgres`
- Password: `otEzwYT7vMCf7ZMonoe/psfKBAND7oIP`

**PgAdmin:**
- URL: `http://192.168.1.74:5051`
- Email: `admin@baseanalysis.staging`
- Password: `WNnb0aGAkdDUQasAXwG3h3EW7XY6ZY3S`

**Portainer:**
- URL: `http://192.168.1.74:9000`
- Username: `admin` (configurado no setup)

---

## 📝 Checklist de Deploy

- [ ] Git pull das últimas mudanças
- [ ] Build da imagem Docker
- [ ] Verificar imagem criada
- [ ] Restart do container
- [ ] Aguardar health check (15s)
- [ ] Verificar logs sem erros
- [ ] Testar acesso PgAdmin
- [ ] Testar acesso à aplicação
- [ ] Verificar todos os containers healthy

---

## 🚀 Deploy Rápido (One-liner)

```bash
# No servidor (MacBook Air)
cd ~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/ && \
git pull gitea main && \
docker build -t basedatafeed:staging . && \
docker restart basedatafeed-staging-app && \
sleep 15 && \
docker ps | grep staging
```

---

## 🔄 Futuro: Workflows Automáticos

Quando os Gitea Actions workflows forem corrigidos (network isolation issues):

**Pipeline automático será:**
```
Push to main → Tests → Build Image → Deploy → Health Check
```

**Por agora, usar este processo manual.**

---

**Versão:** 1.0
**Data:** 2026-01-07
**Stack:** base-analysis-staging
**Ambiente:** Staging (MacBook Air - Portainer CE)
