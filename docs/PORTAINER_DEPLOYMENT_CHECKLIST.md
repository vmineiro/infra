# Checklist de Deploy - Portainer Staging

## Pré-Deploy

- [ ] Portainer a correr: http://localhost:9000
- [ ] Network `BaseAnalysis-Staging` criada
- [ ] Ficheiros prontos:
  - [ ] `docker-compose.staging.portainer.yml`
  - [ ] `.env.staging.portainer`

## Deploy

### 1. Parar Dockge (Opcional)
```bash
cd ~/Dev/docker-projects/dockge/stacks/base-data-etl-staging/
docker-compose down
docker ps | grep staging  # Deve estar vazio
```
- [ ] Containers Dockge parados

### 2. Limpar Volumes Antigos (Recomendado)
```bash
docker volume rm basedatafeed-postgres-staging 2>/dev/null || true
docker volume rm basedatafeed-logs-staging 2>/dev/null || true
docker volume rm basedatafeed-data-staging 2>/dev/null || true
docker volume rm basedatafeed-pgadmin-staging 2>/dev/null || true
docker volume ls | grep staging  # Deve estar vazio
```
- [ ] Volumes antigos removidos

### 3. Deploy no Portainer via Git Repository

⚠️ **IMPORTANTE:** Não usar "Web editor" - usar **"Repository"** method!

**Ver guia detalhado:** [PORTAINER_GIT_DEPLOY.md](PORTAINER_GIT_DEPLOY.md)

#### Opção A: Repo Público (Rápida) ⭐
- [ ] Tornar repo público: Gitea → Settings → "Make Public"
- [ ] Portainer → Local → Stacks → Add stack
- [ ] Stack name: `base-analysis-staging`
- [ ] Build method: ✅ **Repository** (tab do meio)
- [ ] Repository URL: `http://192.168.1.74:3000/VitorMineiroLda/base-data-etl`
- [ ] Repository reference: `refs/heads/main`
- [ ] Compose path: `docker-compose.staging.portainer.yml`
- [ ] Environment variables configuradas (ver PORTAINER_GIT_DEPLOY.md)
- [ ] Click "Deploy the stack"
- [ ] Aguardar clone + build (3-5 min)

#### Opção B: Repo Privado com Token (Segura)
- [ ] Criar token: Gitea → User Settings → Applications → Generate Token
- [ ] Copiar token gerado
- [ ] Portainer → Stacks → Add stack (Repository method)
- [ ] Enable "Use authentication"
- [ ] Username: `<teu-username>`
- [ ] Personal Access Token: `<token-copiado>`
- [ ] (resto igual à Opção A)

## Verificação

### 4. Verificar Containers (Portainer UI)

**Portainer → Local → Containers**

- [ ] `basedatafeed-staging-db` - Status: **healthy** ✅
- [ ] `basedatafeed-staging-app` - Status: **healthy** ou **running** ✅
- [ ] `basedatafeed-staging-pgadmin` - Status: **running** ✅

### 5. Verificar via CLI
```bash
docker ps | grep staging
```
- [ ] 3 containers a correr

### 6. Testar PgAdmin
```bash
open http://localhost:5051
```
- [ ] Login com `staging@basedatafeed.local` / `WNnb0aGAkdDUQasAXwG3h3EW7XY6ZY3S`
- [ ] UI acessível

### 7. Testar Application
```bash
curl http://localhost:8081/ || docker logs basedatafeed-staging-app --tail 20
```
- [ ] App responde ou logs sem erros críticos

### 8. Verificar Database
```bash
docker exec -it basedatafeed-staging-db psql -U postgres -d basedata-staging -c "\dt"
```
- [ ] Tabelas criadas (contracts, contractual_executions, etc.)

### 9. Verificar Logs
```bash
docker logs basedatafeed-staging-app --tail 50
docker logs basedatafeed-staging-db --tail 30
```
- [ ] Sem erros "ERROR", "FATAL", "Connection refused"

### 10. Teste Funcional (Opcional)
```bash
docker exec basedatafeed-staging-app python /app/source/main.py --periodic-update
docker logs basedatafeed-staging-app --tail 50
```
- [ ] ETL executa sem erros

## Pós-Deploy

- [ ] Stack funcional por 24h
- [ ] Sem crashes ou restarts inesperados
- [ ] Logs limpos

## Rollback (Se Necessário)

Se houver problemas:
```bash
# 1. Parar no Portainer
Portainer → Stacks → base-analysis-staging → Stop → Remove stack

# 2. Religar Dockge
cd ~/Dev/docker-projects/dockge/stacks/base-data-etl-staging/
docker-compose up -d
```

## Próximas Fases

- [ ] **Fase 5:** Configurar webhooks Gitea → Portainer
- [ ] **Fase 6:** Configurar scheduled tasks (cron ETL)
- [ ] **Fase 7:** Desativar Dockge (após 1 semana)

---

**Status:** ⏳ Pronto para deploy
**Stack:** base-analysis-staging
**Ambiente:** Staging
**Data:** 2026-01-07
