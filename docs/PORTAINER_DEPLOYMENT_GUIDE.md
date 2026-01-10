# Guia de Deploy - BaseAnalysis Staging no Portainer CE

## Contexto

Este guia descreve o processo de deploy da stack **base-analysis-staging** no Portainer CE no MacBook Air Server.

**Stack:** base-analysis-staging
**Projeto:** BaseAnalysis
**Serviço:** base-data-etl (ETL de dados BASE.gov.pt)
**Ambiente:** Staging
**Network:** BaseAnalysis-Staging (partilhada entre serviços do projeto)

## Pré-Requisitos

- ✅ Portainer CE instalado e a correr (http://localhost:9000)
- ✅ Network `BaseAnalysis-Staging` criada
- ✅ Dockge staging stack parado (opcional)
- ✅ Ficheiros preparados:
  - `docker-compose.staging.portainer.yml`
  - `.env.staging.portainer`

## Passo 1: Parar Stack Antiga no Dockge (Opcional)

Se a stack ainda estiver a correr no Dockge:

```bash
cd ~/Dev/docker-projects/dockge/stacks/base-data-etl-staging/
docker-compose down
```

**Verificar containers parados:**
```bash
docker ps | grep staging
# Não deve mostrar nenhum container staging
```

## Passo 2: Limpar Volumes Antigos (Opcional mas Recomendado)

Visto que vamos inicializar uma base de dados limpa com nova password, é recomendado apagar os volumes antigos para evitar conflitos:

```bash
# Listar volumes existentes
docker volume ls | grep staging

# Apagar volumes antigos (ATENÇÃO: Dados serão perdidos!)
docker volume rm basedatafeed-postgres-staging 2>/dev/null || echo "Volume não existe"
docker volume rm basedatafeed-logs-staging 2>/dev/null || echo "Volume não existe"
docker volume rm basedatafeed-data-staging 2>/dev/null || echo "Volume não existe"
docker volume rm basedatafeed-pgadmin-staging 2>/dev/null || echo "Volume não existe"

# Verificar limpeza
docker volume ls | grep staging
# Não deve mostrar nenhum volume
```

**NOTA:** Se os volumes não existirem (erro: no such volume), está tudo bem - significa que não há volumes antigos para limpar.

## Passo 3: Aceder ao Portainer

1. Abrir browser: http://localhost:9000
2. Login com credenciais de admin
3. Navegar para: **Local → Stacks**

## Passo 4: Criar Nova Stack

1. **Click em "Add stack"**

2. **Configurar stack:**
   - **Name:** `base-analysis-staging`
   - **Build method:** Web editor

3. **Colar o conteúdo de `docker-compose.staging.portainer.yml`:**
   - No MacBook: `cat ~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/docker-compose.staging.portainer.yml`
   - Copiar todo o conteúdo
   - Colar no editor do Portainer

## Passo 5: Configurar Variáveis de Ambiente

**Opção A: Carregar ficheiro .env (Recomendado)**

1. Scroll down até "Environment variables"
2. Click em "Load variables from .env file"
3. Selecionar ficheiro: `.env.staging.portainer`

**Opção B: Adicionar manualmente**

Click em "Add environment variable" e adicionar:

```
DB_PASSWORD = otEzwYT7vMCf7ZMonoe/psfKBAND7oIP
PGADMIN_EMAIL = staging@basedatafeed.local
PGADMIN_PASSWORD = WNnb0aGAkdDUQasAXwG3h3EW7XY6ZY3S
DB_SSL_MODE = prefer
LOG_LEVEL = INFO
BATCH_SIZE = 50
API_TIMEOUT = 30
API_RETRY_ATTEMPTS = 3
DATE_RANGE_DAYS = 7
USE_CENTRALIZED_CONFIG = true
DRY_RUN = false
USE_SAFE_PROCESSING = true
BUILD_DATE = now
VCS_REF = staging
```

## Passo 6: Deploy da Stack

1. **Scroll down até ao fundo**
2. **Click em "Deploy the stack"**
3. **Aguardar o processo:**
   - Portainer vai fazer pull das imagens
   - Criar volumes novos
   - Fazer build da aplicação (pode demorar 2-3 minutos)
   - Iniciar containers

**O que esperar:**
- "Building app-staging..." (~1-2 minutos)
- "Creating volumes..."
- "Starting services..."
- "Stack deployed successfully" ✅

## Passo 7: Verificar Deployment

### 7.1 Verificar Containers no Portainer

**Navegar:** Portainer → Local → Containers

**Containers esperados:**
- `basedatafeed-staging-db` - Status: **healthy** (verde)
- `basedatafeed-staging-app` - Status: **healthy** ou **running** (verde)
- `basedatafeed-staging-pgadmin` - Status: **running** (verde)

Se algum container estiver **unhealthy** ou **exited**, verificar logs (ver secção Troubleshooting).

### 7.2 Verificar via CLI (No MacBook Server)

```bash
# 1. Containers running
docker ps | grep staging

# Deve mostrar 3 containers:
# basedatafeed-staging-db (healthy)
# basedatafeed-staging-app (healthy ou running)
# basedatafeed-staging-pgadmin (running)

# 2. Verificar health do database
docker inspect basedatafeed-staging-db | grep -A 5 Health

# 3. Verificar health da aplicação
docker inspect basedatafeed-staging-app | grep -A 5 Health
```

### 7.3 Testar Acesso às Aplicações

**PgAdmin (Database UI):**
```bash
# Abrir browser
open http://localhost:5051

# Login:
# Email: staging@basedatafeed.local
# Password: WNnb0aGAkdDUQasAXwG3h3EW7XY6ZY3S
```

**Application Health Check:**
```bash
# Verificar endpoint de health (se disponível)
curl http://localhost:8081/health
# ou
curl http://localhost:8081/

# Se não responder, verificar logs
docker logs basedatafeed-staging-app --tail 50
```

### 7.4 Verificar Base de Dados Inicializada

```bash
# Conectar à base de dados
docker exec -it basedatafeed-staging-db psql -U postgres -d basedata-staging

# Listar tabelas (devem estar criadas pelos scripts de inicialização)
\dt

# Verificar versão PostgreSQL
SELECT version();

# Sair
\q
```

**Tabelas esperadas:**
- `contracts`
- `contractual_executions`
- `invitations`
- `participants`
- `periodic_collection_log`
- (outras tabelas do schema)

### 7.5 Verificar Logs sem Erros Críticos

```bash
# Logs da aplicação
docker logs basedatafeed-staging-app --tail 100

# Logs do database
docker logs basedatafeed-staging-db --tail 50

# Logs do PgAdmin
docker logs basedatafeed-staging-pgadmin --tail 30
```

**Procurar por:**
- ✅ "database system is ready to accept connections"
- ✅ "Application started successfully"
- ❌ "ERROR", "FATAL", "Connection refused"

## Passo 8: Teste Funcional (Opcional)

Executar ETL manual para confirmar que tudo funciona:

```bash
# Executar ETL periodic update (modo teste)
docker exec basedatafeed-staging-app python /app/source/main.py --periodic-update

# Verificar logs
docker logs basedatafeed-staging-app --tail 50

# Verificar dados inseridos
docker exec basedatafeed-staging-db psql -U postgres -d basedata-staging -c "SELECT COUNT(*) FROM contracts;"
```

## Troubleshooting

### Container `basedatafeed-staging-app` em estado "Exited" ou "Unhealthy"

**Verificar logs:**
```bash
docker logs basedatafeed-staging-app --tail 100
```

**Possíveis problemas:**
1. **Database connection failed:**
   - Verificar que `basedatafeed-staging-db` está healthy
   - Verificar password: `echo $DB_PASSWORD` (via env vars no Portainer)

2. **Build failed:**
   - Portainer → Stacks → base-analysis-staging → Editor
   - Verificar se build context está correto (`.` no `context:`)

3. **Healthcheck failing:**
   - Verificar se `/app/scripts/healthcheck.py` existe
   - Testar manualmente: `docker exec basedatafeed-staging-app python /app/scripts/healthcheck.py`

### Container `basedatafeed-staging-db` em estado "Unhealthy"

**Verificar logs:**
```bash
docker logs basedatafeed-staging-db --tail 100
```

**Possíveis problemas:**
1. **PostgreSQL não iniciou:**
   - Verificar se PGDATA está correto: `/var/lib/postgresql/data/pgdata`
   - Verificar permissões do volume

2. **Scripts de inicialização falharam:**
   - Verificar se `./source/database/setup` existe no contexto
   - Logs devem mostrar "database system is ready"

### PgAdmin não acessível (Port 5051)

**Verificar container:**
```bash
docker ps | grep pgadmin
docker logs basedatafeed-staging-pgadmin --tail 50
```

**Verificar porta:**
```bash
lsof -i :5051
# Deve mostrar Docker
```

### Erro: "Network BaseAnalysis-Staging not found"

**Verificar network:**
```bash
docker network ls | grep BaseAnalysis

# Se não existir, criar:
docker network create \
  --driver bridge \
  --label environment=staging \
  --label project=BaseAnalysis \
  BaseAnalysis-Staging
```

### Build demora muito tempo (>5 minutos)

**Normal em primeira build:**
- Download de imagens base (Python, PostgreSQL)
- Instalação de dependências Python
- Compilação de psycopg2

**Se travar:**
1. Verificar logs de build no Portainer
2. Verificar disk space: `df -h`
3. Restart Portainer se necessário: `docker restart portainer`

## Rollback (Se Necessário)

Se houver problemas graves e precisares voltar ao Dockge:

```bash
# 1. Parar stack no Portainer
Portainer → Stacks → base-analysis-staging → Stop → Remove stack

# 2. Religar no Dockge
cd ~/Dev/docker-projects/dockge/stacks/base-data-etl-staging/
docker-compose up -d

# 3. Verificar
docker ps | grep staging
curl http://localhost:8081/health
```

## Próximos Passos Após Deploy Bem-Sucedido

1. ✅ **Fase 4 completa** - Stack migrado para Portainer
2. ⏭️ **Fase 5** - Configurar webhooks Gitea → Portainer (deploy automático)
3. ⏭️ **Fase 6** - Configurar scheduled tasks (cron ETL periódico)
4. ⏭️ **Fase 7** - Desativar Dockge após 1 semana de monitorização

## Comandos Úteis

```bash
# Ver status de todos os containers staging
docker ps | grep staging

# Restart de um container específico
docker restart basedatafeed-staging-app

# Ver logs em tempo real
docker logs -f basedatafeed-staging-app

# Entrar no container da app (debug)
docker exec -it basedatafeed-staging-app bash

# Entrar na base de dados
docker exec -it basedatafeed-staging-db psql -U postgres -d basedata-staging

# Ver volumes criados
docker volume ls | grep staging

# Inspecionar network
docker network inspect BaseAnalysis-Staging
```

## Informações da Stack

**Containers:**
- Database: `basedatafeed-staging-db` (PostgreSQL 15-alpine)
- Application: `basedatafeed-staging-app` (BaseDataFeed ETL)
- PgAdmin: `basedatafeed-staging-pgadmin` (Database UI)

**Portas:**
- App: 8081
- PgAdmin: 5051
- Database: 5433

**Volumes:**
- `basedatafeed-postgres-staging` - Database data
- `basedatafeed-logs-staging` - Application logs
- `basedatafeed-data-staging` - Application data
- `basedatafeed-pgadmin-staging` - PgAdmin config

**Network:**
- `BaseAnalysis-Staging` (bridge isolada)

**Recursos:**
- CPU: 1.5 cores (limit)
- RAM: 1.5GB (limit)
- Reservado: 0.25 CPU, 256MB RAM

---

**Versão:** 1.0
**Data:** 2026-01-07
**Stack:** base-analysis-staging
**Ambiente:** Staging (MacBook Air Server)
