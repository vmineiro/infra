# Plano: Deploy e Configuração no Dockge + Gitea CI/CD

**Data**: 2026-01-05 (Updated)
**Contexto**: Recolha periódica de dados implementada + Gitea CI/CD configurado
**Objetivo**: Setup completo no Dockge + CI/CD automático com Gitea Actions

---

## 🎯 Arquitetura Completa

```
┌────────────────────────────────────────────────────────────────┐
│ MacBook Air Server                                             │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐ │
│  │   Gitea         │  │   Dockge        │  │  BaseAnalysis  │ │
│  │   (Git+CI/CD)   │  │   (Mgmt UI)     │  │   (Staging)    │ │
│  │   Port: 3000    │  │   Port: 5001    │  │   Port: 8081   │ │
│  └─────────────────┘  └─────────────────┘  └────────────────┘ │
│         │                                            ▲          │
│         └─── Push triggers CI/CD ──────────────────┘          │
└────────────────────────────────────────────────────────────────┘
                          ▲
                          │ git push gitea main
                  ┌───────┴────────┐
                  │ Dev Laptop     │
                  └────────────────┘
```

**Workflow:**
1. Developer pushes code to Gitea
2. Gitea Actions runs tests automatically
3. If tests pass → Auto-deploy to Dockge staging stack
4. Weekly ETL runs automatically (Sundays 10:00)

---

## Estado Atual

### ✅ O que está implementado (local)

**Phases 0-3 concluídas**:
- ✅ Database migration `periodic_collection_log` table
- ✅ Date utilities (`get_previous_week_range()`)
- ✅ CLI argument `--periodic-update`
- ✅ Script `scripts/periodic_update.sh` com lock file
- ✅ Database logging (`log_periodic_execution()`)
- ✅ API timeout 300s configurado
- ✅ 21 unit tests (todos passam)

### ❌ O que falta

- ❌ Deploy para servidor (Dockge)
- ❌ Configuração da scheduled task
- ❌ Testes em produção

---

## Infraestrutura Atual

**Servidor**:
- Dockge 1.5 a correr
- Pasta stacks: `~/Dev/docker-projects/dockge/stacks`
- Acesso SSH disponível
- PostgreSQL (pode ser container ou externo)

**Projeto Local**:
- Path: `/Users/vitormineiro/Dev/VitorMineiro/BaseAnalysis/base-data-etl`
- Git repo: `git@github.com:vmineiro/base-data-etl.git`
- Docker files: `Dockerfile`, `docker-compose.yml`, `docker-compose.staging.yml` ← **Vamos usar este**

**Ambiente**: Staging

**Estrutura Final no Servidor**:
```
~/Dev/docker-projects/dockge/stacks/
└── BaseAnalysis/                          ← Stack/Network (nome visível no Dockge)
    ├── compose.yaml                       ← Dockge usa este (cópia do staging)
    ├── .env                               ← Environment variables
    └── base-data-etl/                     ← Repositório clonado
        ├── Dockerfile                     ← Build context aqui
        ├── docker-compose.staging.yml     ← Template original
        ├── source/
        ├── scripts/
        └── ...
```

**IMPORTANTE**: O `compose.yaml` deve ter `context: ./base-data-etl` para apontar para o Dockerfile dentro do repo clonado.

---

## Estratégia Multi-Ambiente (Staging + Production)

Para correr múltiplos ambientes no mesmo servidor, use **stacks separadas**:

```
~/Dev/docker-projects/dockge/stacks/
├── BaseAnalysis-Staging/              ← Stack Staging
│   ├── compose.yaml                   (cópia de docker-compose.staging.yml)
│   ├── .env                           (variáveis staging)
│   └── base-data-etl/                 (código clonado)
│
└── BaseAnalysis-Production/           ← Stack Production (futuro)
    ├── compose.yaml                   (cópia de docker-compose.prod.yml)
    ├── .env                           (variáveis production)
    └── base-data-etl/                 (código clonado ou partilhado)
```

**Vantagens**:
- Isolamento completo entre ambientes
- Portas diferentes (staging: 8081, prod: 8080)
- Service names iguais (`base-data-etl`) mas containers diferentes
- Gestão independente via Dockge UI

**Como distinguir nos comandos**:

```bash
# STAGING
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec base-data-etl python /app/source/main.py --periodic-update

# PRODUCTION
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Production
docker-compose exec base-data-etl python /app/source/main.py --periodic-update

# OU usar container names (quando não está no diretório):
docker exec basedatafeed-staging-app python /app/source/main.py --periodic-update
docker exec basedatafeed-prod-app python /app/source/main.py --periodic-update
```

---

## Quick Start (TL;DR)

Para deployment rápido no servidor:

```bash
# 1. Setup inicial (STAGING)
cd ~/Dev/docker-projects/dockge/stacks
mkdir -p BaseAnalysis-Staging && cd BaseAnalysis-Staging
git clone git@github.com:vmineiro/base-data-etl.git
cp base-data-etl/docker-compose.staging.yml compose.yaml
sed -i 's|context: \.|context: ./base-data-etl|g' compose.yaml

# 2. Criar .env file (copiar do exemplo abaixo, secção 1.3)
nano .env

# 3. No Dockge UI (http://server:5001):
#    - Stack "BaseAnalysis-Staging" deve aparecer automaticamente
#    - Click "Build" → "Start"
#    - Criar scheduled task com cron: 0 10 * * 0
#    - Command: docker-compose exec -T base-data-etl python /app/source/main.py --periodic-update
#    - Working Directory: ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
```

---

## Plano de Deployment

### Fase 1: Preparação e Transfer (15-20 min)

#### 1.1 Verificar Estado Local

```bash
# No Mac (local)
cd /Users/vitormineiro/Dev/VitorMineiro/BaseAnalysis/base-data-etl

# Verificar que está tudo committed
git status

# Verificar testes passam
python -m pytest tests/test_date_utils.py -v

# Commit e push se houver mudanças
git add .
git commit -m "feat: add periodic data collection (Phases 0-3)"
git push origin main
```

#### 1.2 Preparar Servidor

```bash
# SSH para o servidor
ssh user@your-server.com

# Navegar para diretório do Dockge
cd ~/Dev/docker-projects/dockge/stacks

# Criar diretório para a stack BaseAnalysis-Staging
mkdir -p BaseAnalysis-Staging
cd BaseAnalysis-Staging

# Clonar repositório ETL
git clone git@github.com:vmineiro/base-data-etl.git
# OU se usar HTTPS:
# git clone https://github.com/vmineiro/base-data-etl.git

# Criar compose.yaml a partir do docker-compose.staging.yml
cp base-data-etl/docker-compose.staging.yml compose.yaml

# IMPORTANTE: Editar compose.yaml para ajustar o build context
# Mudar de:
#   context: .
# Para:
#   context: ./base-data-etl
sed -i 's|context: \.|context: ./base-data-etl|g' compose.yaml

# IMPORTANTE: Alterar service name para base-data-etl
# Mudar de:
#   app-staging:
# Para:
#   base-data-etl:
sed -i 's|app-staging:|base-data-etl:|g' compose.yaml

# Estrutura resultante:
# ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging/
# ├── compose.yaml               ← Dockge usa este (context ajustado, service name correto!)
# ├── .env                       ← Criar no próximo passo
# └── base-data-etl/             ← Código fonte
#     ├── Dockerfile            ← Build context aqui
#     ├── docker-compose.staging.yml  ← Template original
#     ├── source/
#     └── ...
```

**Nota importante**: O Dockge 1.5 usa `compose.yaml` na raiz da stack. Copiamos o conteúdo do `docker-compose.staging.yml` para `compose.yaml` e ajustamos:
1. Build context para `./base-data-etl`
2. Service name para `base-data-etl` (consistente em todos os ambientes)

**Nota Multi-Ambiente**: Use `BaseAnalysis-Staging` para staging, `BaseAnalysis-Production` para produção. Isto permite correr ambos os ambientes simultaneamente no mesmo servidor.

**Alternativa (se não usar Git)**:
```bash
# No Mac, fazer tar do projeto
cd /Users/vitormineiro/Dev/VitorMineiro/BaseAnalysis
tar -czf base-data-etl.tar.gz base-data-etl/

# Transfer via scp
scp base-data-etl.tar.gz user@server:~/Dev/docker-projects/dockge/stacks/

# No servidor, extrair
cd ~/Dev/docker-projects/dockge/stacks
mkdir -p BaseAnalysis
cd BaseAnalysis
tar -xzf ../base-data-etl.tar.gz
```

#### 1.3 Configurar Environment Variables

```bash
# No servidor, criar .env file na raiz da stack
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging

cat > .env << 'EOF'
# Database Configuration
DATABASE_HOST=base-data-service-base-data.j.aivencloud.com
DATABASE_PORT=10091
DATABASE_NAME=basedata
DATABASE_USER=dev_api
DATABASE_PASSWORD=<REDACTED_AIVEN_PASSWORD>
DATABASE_SSL_MODE=prefer
DATABASE_CONNECT_TIMEOUT=30
DATABASE_COMMAND_TIMEOUT=300

# Application Configuration
BASEDATAFEED_BATCH_SIZE=50
BASEDATAFEED_ENABLE_VALIDATION=true
BASEDATAFEED_MAX_CONCURRENT_REQUESTS=5
DATE_RANGE_DAYS=7

# Logging
BASEDATAFEED_LOG_LEVEL=INFO
EOF

# Proteger o ficheiro (contém passwords)
chmod 600 .env
```

**⚠️ IMPORTANTE**: Se a database for container no Dockge, usar nome do service em vez de host externo.

---

### Fase 2: Configurar Stack no Dockge (10 min)

#### 2.1 Adicionar Stack via Dockge UI

**Dockge 1.5 - Auto-Discovery**:

1. Abrir Dockge UI no browser: `http://your-server:5001`

2. A stack **BaseAnalysis** deve aparecer automaticamente na lista de stacks
   - Dockge 1.5 detecta automaticamente diretórios com `compose.yaml` em `~/Dev/docker-projects/dockge/stacks/`
   - O nome da stack é o nome do diretório (`BaseAnalysis`)

3. Se não aparecer:
   - Verificar que `compose.yaml` existe em `~/Dev/docker-projects/dockge/stacks/BaseAnalysis/`
   - Refresh da página do Dockge
   - Verificar logs do Dockge: `docker logs dockge`

4. Click na stack `BaseAnalysis` para ver os detalhes

#### 2.2 Configuração do Compose File

O ficheiro `compose.yaml` (copiado de `docker-compose.staging.yml`) já deve conter a configuração correta para o ambiente de staging. Exemplo de estrutura esperada:

```yaml
# compose.yaml (exemplo de estrutura)
services:
  app:
    build:
      context: ./base-data-etl    # ← Importante: aponta para o repo clonado
      dockerfile: Dockerfile
    container_name: base-data-etl
    restart: always
    env_file: .env
    volumes:
      - ./logs:/var/log/base-etl
    depends_on:
      - db  # Se database for container
    networks:
      - base-network

  # Se PostgreSQL for container (opcional)
  db:
    image: postgres:15-alpine
    container_name: base-postgres
    restart: always
    environment:
      POSTGRES_DB: basedata
      POSTGRES_USER: dev_api
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - base-network

networks:
  base-network:
    driver: bridge

volumes:
  postgres-data:
```

#### 2.3 Build e Start via Dockge

**Na UI do Dockge**:
1. Selecionar stack `BaseAnalysis`
2. Click "Build" (constrói a imagem)
3. Aguardar build completar (pode demorar 2-5 min)
4. Click "Start" ou "Up"
5. Verificar logs na UI

**Via CLI (alternativa)**:
```bash
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis
docker-compose up --build -d
```

---

### Fase 3: Verificar Deployment (10 min)

#### 3.1 Health Checks

```bash
# Verificar containers estão a correr
docker ps | grep base

# Ver logs (via docker-compose)
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose logs -f base-data-etl

# OU via container name
docker logs basedatafeed-staging-app --tail 50

# Verificar entrypoint executou corretamente
docker logs basedatafeed-staging-app | grep "✓"
# Deve mostrar:
# ✓ Database connection successful
# ✓ Health check passed
```

#### 3.2 Verificar Database Connection

```bash
# Executar dentro do container (via docker-compose)
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec base-data-etl python -c "
from source.database.database_manager import DatabaseManager
db = DatabaseManager()
print('✓ Database connection OK')
db.close()
"

# OU via container name
docker exec -it basedatafeed-staging-app python -c "
from source.database.database_manager import DatabaseManager
db = DatabaseManager()
print('✓ Database connection OK')
db.close()
"
```

#### 3.3 Verificar Migrations

```bash
# Verificar que periodic_collection_log table existe (via docker-compose)
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec base-data-etl psql -U dev_api -d basedata -c "\d dbo.periodic_collection_log"

# OU via container name
docker exec -it basedatafeed-staging-db psql -U dev_api -d basedata -c "\d dbo.periodic_collection_log"

# Deve mostrar estrutura da tabela:
# id, week_start, week_end, execution_date, status, contracts_processed, contracts_failed, error_message
```

---

### Fase 4: Configurar Scheduled Task no Dockge (5 min)

#### 4.1 Testar Comando Manual Primeiro

```bash
# No servidor, testar comando (via docker-compose - RECOMENDADO)
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec base-data-etl python /app/source/main.py --periodic-update

# OU via container name direto
docker exec basedatafeed-staging-app python /app/source/main.py --periodic-update
```

**Saída esperada**:
```
📅 Periodic Update Mode: Processing previous week (2025-11-17 to 2025-11-23)
Processing date range: 2025-11-17 to 2025-11-23
...
✓ Completed processing date range
```

#### 4.2 Verificar Database Logging

```bash
# Via docker-compose
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec -T base-data-etl psql -U dev_api -d basedata -c "
SELECT
    week_start,
    week_end,
    status,
    contracts_processed,
    execution_date
FROM dbo.periodic_collection_log
ORDER BY execution_date DESC
LIMIT 5;
"

# OU via container name
docker exec -it basedatafeed-staging-db psql -U dev_api -d basedata -c "
SELECT
    week_start,
    week_end,
    status,
    contracts_processed,
    execution_date
FROM dbo.periodic_collection_log
ORDER BY execution_date DESC
LIMIT 5;
"
```

**Deve mostrar entrada recente**.

#### 4.3 Criar Scheduled Task no Dockge

**Na UI do Dockge**:

1. **Navegar para a Stack**:
   - Abrir stack `BaseAnalysis`

2. **Criar Task/Pipeline**:
   - Procurar secção "Tasks", "Pipelines", ou "Cron Jobs" (varia conforme versão Dockge)
   - Click "Add Task" ou "New Pipeline"

3. **Configurar Task**:
   ```yaml
   Name: Periodic Data Collection
   Description: Weekly data collection (Sunday-Saturday) from BASE.gov.pt

   Schedule: 0 10 * * 0
   # Formato cron: minuto hora dia mês dia-da-semana
   # 0 10 * * 0 = Domingo às 10:00

   Command: docker-compose exec -T base-data-etl python /app/source/main.py --periodic-update

   # Alternativa usando container name:
   # Command: docker exec basedatafeed-staging-app python /app/source/main.py --periodic-update

   Working Directory: ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging

   Enabled: ✓ (checked)
   ```

4. **Flags Importantes**:
   - `-T` flag: Desativa TTY allocation (necessário para cron/scheduled execution)
   - Sem este flag, pode dar erro em execução agendada

#### 4.4 Testar Trigger Manual

1. Na UI do Dockge, na task criada
2. Click "Run Now" ou "Trigger" ou ▶️ (varia conforme versão)
3. Monitorizar logs na UI
4. Verificar database:

```bash
# Via docker-compose
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec -T base-data-etl psql -U dev_api -d basedata -c "
SELECT * FROM dbo.periodic_collection_log ORDER BY execution_date DESC LIMIT 3;
"

# OU via container name
docker exec -it basedatafeed-staging-db psql -U dev_api -d basedata -c "
SELECT * FROM dbo.periodic_collection_log ORDER BY execution_date DESC LIMIT 3;
"
```

---

### Fase 4.4: CI/CD com Gitea + Gitea Actions (✅ IMPLEMENTADO)

#### Por que Gitea em vez de Cron Manual?

O Dockge 1.5 **não tem funcionalidade nativa de scheduled tasks**. As alternativas eram:

1. **Cron manual no servidor** (Opção básica)
2. **Gitea + Gitea Actions** (✅ **IMPLEMENTADO** - leve e eficiente)
3. **GitLab CE Self-Hosted** (Muito pesado: 4-8GB RAM - ❌ FALHOU)

**Por que Gitea foi escolhido**:

✅ **Leve**: ~450MB RAM total (vs 4-8GB do GitLab)
✅ **GitHub Actions compatible**: Usa mesma sintaxe de workflows
✅ **Multi-projeto**: Cada projeto tem repositório e pipelines próprios
✅ **Scheduling visual**: Configure schedules na UI web
✅ **Pipeline history**: Logs completos de todas as execuções
✅ **CI/CD integrado**: Testes automáticos antes de deploy
✅ **Git hosting self-hosted**: Sem dependências externas
✅ **Rápido**: Execução local, sem latência de SSH

**Recursos** (medidos):
- Gitea Server: ~300MB RAM
- Gitea Actions Runner: ~150MB RAM
- Total: ~450MB RAM (aceitável em MacBook Air 8GB)

#### Setup Gitea + Actions no Servidor

**1. Instalar Gitea via Docker Compose**:

Ver guia completo em: **`gitea-setup/README.md`**

```bash
# No servidor MacBook Air
mkdir -p ~/Dev/gitea
cd ~/Dev/gitea

# Copiar docker-compose.yml do gitea-setup/docker-compose.yml
# (veja o ficheiro para configuração completa)

# Iniciar Gitea
docker-compose up -d gitea

# Aguardar ~30s para Gitea inicializar
# Acessar: http://localhost:3000 (ou http://[server-ip]:3000)
# Completar setup wizard (criar admin user)
```

**2. Registar Gitea Actions Runner**:

```bash
# No Gitea UI:
# Site Administration → Actions → Runners → Create new Runner
# Copiar registration token

# Iniciar runner
cd ~/Dev/gitea
docker-compose up -d gitea-runner

# Registar runner manualmente
docker exec -it gitea-runner act_runner register \
  --instance http://gitea:3000 \
  --token <PASTE_YOUR_TOKEN_HERE> \
  --name macbook-air-runner

# Restart runner
docker-compose restart gitea-runner

# Verificar status: Gitea UI → Actions → Runners
# Deve aparecer "macbook-air-runner" com status "Idle" (verde)
```

**3. Migrar base-data-etl para Gitea**:

```bash
# No laptop de desenvolvimento
cd /Users/vitormineiro/Dev/VitorMineiro/BaseAnalysis/base-data-etl

# Adicionar remote do Gitea (mantém GitHub/origin como backup)
git remote add gitea http://[macbook-air-ip]:3000/<username>/base-data-etl.git

# Push para Gitea
git push gitea main

# Verificar no Gitea UI - código deve aparecer
```

**4. Workflows CI/CD (já criados no projeto)**:

Os workflows já estão implementados em `.github/workflows/`:

**a) `ci-cd.yml`** - Pipeline principal (automático em push):
- ✅ Run pytest tests
- ✅ Auto-deploy to staging (se tests pass)
- ✅ Health check verification

**b) `scheduled-etl.yml`** - ETL agendado:
- ✅ Cron: Domingos 10:00 AM
- ✅ Executa periodic update
- ✅ Verifica database update
- ✅ Manual trigger disponível

**c) `scripts/deploy-staging.sh`** - Script de deployment:
- ✅ Pull latest code
- ✅ Rebuild containers
- ✅ Verify health status
- ✅ Rollback on failure

**5. Como funciona o workflow automático**:

```
Developer Laptop                MacBook Air Server
      │                                │
      │  git push gitea main           │
      ├────────────────────────────────>
      │                                │
      │                         ┌──────▼──────┐
      │                         │ Gitea       │
      │                         │ detects push│
      │                         └──────┬──────┘
      │                                │
      │                         ┌──────▼──────┐
      │                         │Gitea Runner │
      │                         │runs workflow│
      │                         └──────┬──────┘
      │                                │
      │                         ┌──────▼──────┐
      │                         │1. Run Tests │
      │                         └──────┬──────┘
      │                                │
      │                         ┌──────▼──────┐
      │                         │2. Deploy    │
      │                         │   Staging   │
      │                         └──────┬──────┘
      │                                │
      │  View results in Gitea UI      │
      <────────────────────────────────┤
```

**6. Configurar Schedule (já configurado no workflow)**:

O schedule já está definido em `scheduled-etl.yml`:
```yaml
on:
  schedule:
    - cron: '0 10 * * 0'  # Domingos às 10:00 UTC
```

Para trigger manual:
1. Gitea UI → Repository → Actions
2. Select "Scheduled ETL" workflow
3. Click "Run workflow"

Isto **substitui completamente a necessidade de cron jobs no servidor**!

#### Benefícios para Múltiplos Projetos

Quando adicionar mais projetos:
1. Criar novo repositório no Gitea
2. Adicionar workflows `.github/workflows/` ao projeto
3. Configurar schedule no workflow YAML
4. Gitea Actions executa tudo automaticamente

**Exemplo**: Se tiver 5 projetos ETL, cada um tem seus próprios workflows, schedules, e logs centralizados no Gitea.

#### Recursos do Gitea

- **Web UI**: `http://[server-ip]:3000`
- **Recursos**: ~450MB RAM (muito mais leve que GitLab)
- **Custo**: Gitea é gratuito e open-source
- **Documentação**: https://docs.gitea.com/

---

### Fase 5: Monitorização e Validação (10 min)

#### 5.1 Schedule de Testes

Para não esperar até Domingo, testar com schedule mais frequente:

```
# Teste: A cada 5 minutos (temporário)
*/5 * * * *

# Teste: Todos os dias às 14:00
0 14 * * *

# Produção: Domingo às 10:00
0 10 * * 0
```

**Depois de validar, voltar a schedule de produção!**

#### 5.2 Verificar Execução Agendada

**Aguardar próxima execução** (baseado no schedule de teste), depois:

```bash
# Verificar logs da task no Dockge UI
# OU via CLI:
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose logs base-data-etl | grep "Periodic Update Mode"

# Ou via container name:
docker logs basedatafeed-staging-app | grep "Periodic Update Mode"

# Verificar database
docker-compose exec -T base-data-etl psql -U dev_api -d basedata -c "
SELECT
    week_start,
    week_end,
    status,
    contracts_processed,
    contracts_failed,
    execution_date
FROM dbo.periodic_collection_log
ORDER BY execution_date DESC
LIMIT 10;
"
```

#### 5.3 Verificar Gap Detection

```bash
# Via docker-compose
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec -T base-data-etl psql -U dev_api -d basedata -c "
WITH expected_weeks AS (
    SELECT generate_series(
        date_trunc('week', CURRENT_DATE - INTERVAL '3 months'),
        date_trunc('week', CURRENT_DATE - INTERVAL '1 week'),
        '1 week'::interval
    )::date AS week_start
)
SELECT
    ew.week_start AS missing_week_start,
    ew.week_start + 6 AS missing_week_end,
    '7 days'::text AS duration
FROM expected_weeks ew
LEFT JOIN dbo.periodic_collection_log pcl ON ew.week_start = pcl.week_start
WHERE pcl.id IS NULL
ORDER BY ew.week_start DESC;
"
```

Se houver gaps (semanas em falta), processar manualmente:

```bash
# Processar semana específica (via docker-compose)
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec base-data-etl python /app/source/main.py \
  --start-date 2025-11-17 \
  --end-date 2025-11-23 \
  --data-type both

# OU via container name
docker exec basedatafeed-staging-app python /app/source/main.py \
  --start-date 2025-11-17 \
  --end-date 2025-11-23 \
  --data-type both
```

---

## Setup Produção (Quando Chegar a Altura)

### 1. Criar Stack de Produção

```bash
# No servidor
cd ~/Dev/docker-projects/dockge/stacks
mkdir -p BaseAnalysis-Production
cd BaseAnalysis-Production

# Clonar código (ou criar symlink para staging)
git clone git@github.com:vmineiro/base-data-etl.git

# Copiar docker-compose de produção
cp base-data-etl/docker-compose.prod.yml compose.yaml

# Ajustar build context
sed -i '' 's|context: \.|context: ./base-data-etl|g' compose.yaml

# Ajustar service name se necessário
sed -i '' 's|app-prod:|base-data-etl:|g' compose.yaml

# Criar .env para produção
nano .env
```

### 2. Configurar .env de Produção

```env
# Database Configuration (PRODUÇÃO)
BASEDATAFEED_DB_HOST=prod-db-host  # ← Diferente de staging
BASEDATAFEED_DB_PORT=5432
BASEDATAFEED_DB_NAME=basedata-prod
BASEDATAFEED_DB_USER=prod_user
BASEDATAFEED_DB_PASSWORD=prod_password_secure

# Application Configuration
BASEDATAFEED_ENVIRONMENT=production  # ← production
BASEDATAFEED_LOG_LEVEL=WARNING       # ← menos verboso que staging
BASEDATAFEED_DEBUG="false"

# Processing Configuration
BASEDATAFEED_BATCH_SIZE=100          # ← pode ser maior em prod
BASEDATAFEED_MAX_CONCURRENT_REQUESTS=20
```

### 3. Deploy no Dockge

1. Dockge UI detecta automaticamente `BaseAnalysis-Production`
2. Build → Start
3. Verificar logs
4. Configurar scheduled task separado para prod (ou usar GitLab schedule)

### 4. Isolamento e Segurança

**Portas diferentes**:
- Staging: 8081, 5433, 5051
- Production: 8080, 5432, 5050

**Networks diferentes** (opcional):
```yaml
networks:
  base-analytics-prod:
    name: base-analytics-prod
```

**Recursos diferentes**:
- Staging: 1.5 CPU, 1.5GB RAM
- Production: 2 CPU, 3GB RAM

**Backup apenas prod**:
```bash
# Cron para backup automático apenas da produção
0 3 * * * docker exec basedatafeed-prod-db pg_dump -U prod_user basedata-prod > /backups/basedata-$(date +\%Y\%m\%d).sql
```

### 5. GitLab CI/CD para Produção

Se usar GitLab, adicionar job de deploy para produção no `.gitlab-ci.yml`:

```yaml
# Deploy para production (manual approval)
deploy-production:
  stage: deploy
  script:
    - cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Production
    - docker-compose pull
    - docker-compose up -d --build
  only:
    - main
  environment:
    name: production
  when: manual  # Requer aprovação manual
  tags:
    - shell

# Scheduled ETL para production
etl-periodic-prod:
  stage: deploy
  script:
    - cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Production
    - docker-compose exec -T base-data-etl python /app/source/main.py --periodic-update
  only:
    - schedules
  environment:
    name: production
  tags:
    - shell
```

Criar schedule separado no GitLab para produção (mesmo horário ou diferente).

---

## Troubleshooting

### Problema 1: Container não inicia

**Sintomas**: Container fica em estado "restarting" ou "exited"

**Debug**:
```bash
# Ver logs completos (via docker-compose)
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose logs base-data-etl

# OU via container name
docker logs basedatafeed-staging-app

# Ver último erro
docker logs basedatafeed-staging-app --tail 100 | grep -i error

# Verificar entrypoint
docker run --rm base-data-etl cat /app/entrypoint.sh
```

**Soluções comuns**:
- Database credentials erradas → verificar `.env`
- Database não acessível → verificar network/firewall
- Migrations falharam → verificar SQL syntax

### Problema 2: Scheduled task não executa

**Debug**:
```bash
# Verificar schedule no Dockge UI
# Verificar logs do Dockge (podem estar em /var/log/dockge ou similar)

# Testar comando manualmente (via docker-compose)
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec base-data-etl python /app/source/main.py --periodic-update

# OU via container name
docker exec basedatafeed-staging-app python /app/source/main.py --periodic-update
```

**Soluções comuns**:
- Cron syntax errado → validar em https://crontab.guru
- Flag `-T` em falta → adicionar ao comando
- Working directory errado → especificar path completo

### Problema 3: API timeout (>300s)

**Sintomas**: Logs mostram "timeout after 300s"

**Soluções**:
- Já configurado para 300s no `config.ini`
- Se ainda assim timeout, dividir em ranges mais pequenos
- Verificar network latency para API BASE.gov.pt

### Problema 4: Lock file persiste

**Sintomas**: Execução falha com "Another instance is already running"

**Solução**:
```bash
# Remover lock file manualmente (via docker-compose)
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec base-data-etl rm -f /tmp/periodic_update.lock

# OU via container name
docker exec basedatafeed-staging-app rm -f /tmp/periodic_update.lock

# Verificar se processo realmente está a correr
docker-compose exec base-data-etl ps aux | grep python
```

---

## Operações do Dia-a-Dia

### Update de Código

**Staging**:
```bash
# No servidor
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging/base-data-etl
git pull origin main

# No Dockge UI:
# 1. Stop stack BaseAnalysis-Staging
# 2. Build (reconstrói imagem)
# 3. Start stack
```

**Production**:
```bash
# No servidor
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Production/base-data-etl
git pull origin main

# No Dockge UI:
# 1. Stop stack BaseAnalysis-Production
# 2. Build (reconstrói imagem)
# 3. Start stack
```

### Ver Histórico de Execuções

**Via Database**:
```sql
SELECT
    week_start,
    week_end,
    status,
    contracts_processed,
    contracts_failed,
    ROUND(
        (contracts_processed::numeric / NULLIF(contracts_processed + contracts_failed, 0) * 100),
        2
    ) AS success_rate_pct,
    execution_date
FROM dbo.periodic_collection_log
ORDER BY execution_date DESC
LIMIT 20;
```

**Via Dockge UI**:
- Ver histórico da task em "Execution History" ou "Logs"

### Trigger Manual

**Via Dockge UI**:
- Click no botão "Run Now" ou "Trigger" na task

**Via CLI - Staging**:
```bash
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose exec base-data-etl python /app/source/main.py --periodic-update

# OU via container name
docker exec basedatafeed-staging-app python /app/source/main.py --periodic-update
```

**Via CLI - Production**:
```bash
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Production
docker-compose exec base-data-etl python /app/source/main.py --periodic-update

# OU via container name
docker exec basedatafeed-prod-app python /app/source/main.py --periodic-update
```

### Alterar Schedule

**Via Dockge UI**:
1. Editar task
2. Alterar campo "Schedule"
3. Guardar

**Schedules úteis**:
- `0 10 * * 0` - Domingo 10:00 (produção)
- `0 10 * * 1` - Segunda 10:00
- `0 2 * * *` - Todos os dias 02:00
- `0 10 * * 0,3` - Domingo e Quarta 10:00

---

## Checklist Final

### Deployment
- [ ] Código transferido para servidor
- [ ] `.env` criado com credentials corretos
- [ ] Stack adicionada no Dockge
- [ ] Build concluído com sucesso
- [ ] Containers a correr (`docker ps`)
- [ ] Database connection OK
- [ ] Migrations aplicadas

### Scheduled Task
- [ ] Comando testado manualmente
- [ ] Database logging funciona
- [ ] Task criada no Dockge
- [ ] Schedule configurado (`0 10 * * 0`)
- [ ] Trigger manual testado
- [ ] Primeira execução agendada OK

### Validação
- [ ] Logs acessíveis no Dockge UI
- [ ] Database tem registos de execução
- [ ] Gap detection query funciona
- [ ] Lock file mechanism funciona

### Produção
- [ ] Schedule de teste alterado para produção
- [ ] Monitorização configurada (opcional)
- [ ] Alertas configurados (opcional)
- [ ] README.md atualizado
- [ ] CURRENT_FOCUS.md atualizado

---

## Próximos Passos (Opcional)

### Monitorização
- Setup Uptime Kuma (ou similar) para monitorizar stack
- Alertas se última execução > 8 dias
- Slack/Email notifications em failures

### Logs
- Configurar log rotation para `/var/log/base-etl/periodic.log`
- Integrar com sistema de logging centralizado (Loki, etc)

### Backups
- Backup automático da database
- Snapshot antes de updates

---

## Estimativas de Tempo

| Fase | Descrição | Tempo |
|------|-----------|-------|
| 1 | Preparação e transfer | 15-20 min |
| 2 | Configurar stack no Dockge | 10 min |
| 3 | Verificar deployment | 10 min |
| 4 | Configurar scheduled task | 5 min |
| 5 | Monitorização e validação | 10 min |
| **TOTAL** | **Setup completo** | **50-55 min** |

**Tempo adicional para troubleshooting**: +10-20 min

---

## Recursos

### Comandos Úteis

**Staging**:
```bash
# Ver logs em tempo real (via docker-compose)
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Staging
docker-compose logs -f base-data-etl

# OU via container name
docker logs -f basedatafeed-staging-app

# Entrar no container
docker-compose exec base-data-etl bash
# OU: docker exec -it basedatafeed-staging-app bash

# Verificar status
docker ps -a | grep basedatafeed-staging

# Reiniciar container
docker-compose restart base-data-etl
# OU: docker restart basedatafeed-staging-app

# Ver uso de recursos
docker stats basedatafeed-staging-app
```

**Production**:
```bash
# Ver logs em tempo real
cd ~/Dev/docker-projects/dockge/stacks/BaseAnalysis-Production
docker-compose logs -f base-data-etl

# OU via container name
docker logs -f basedatafeed-prod-app

# Entrar no container
docker-compose exec base-data-etl bash
# OU: docker exec -it basedatafeed-prod-app bash

# Verificar status
docker ps -a | grep basedatafeed-prod

# Reiniciar container
docker-compose restart base-data-etl
# OU: docker restart basedatafeed-prod-app

# Ver uso de recursos
docker stats basedatafeed-prod-app
```

### SQL Queries Úteis

```sql
-- Estatísticas gerais
SELECT
    COUNT(*) AS total_executions,
    COUNT(*) FILTER (WHERE status = 'completed') AS successful,
    COUNT(*) FILTER (WHERE status = 'failed') AS failed,
    SUM(contracts_processed) AS total_contracts,
    ROUND(AVG(contracts_processed), 2) AS avg_per_week
FROM dbo.periodic_collection_log;

-- Última execução
SELECT * FROM dbo.periodic_collection_log
ORDER BY execution_date DESC LIMIT 1;
```

---

**Fim do Plano** ✓
