# Jenkins + GitHub CI/CD Setup

Guia completo para configurar CI/CD automático usando Jenkins + GitHub, substituindo Gitea.

## 🎯 Objetivo Final

```
GitHub Push → Webhook → Jenkins → Build Docker → Deploy to Portainer → ✅
```

**Vantagens:**
- ✅ Sem network isolation issues (Jenkins clona de GitHub diretamente)
- ✅ GitHub webhooks gratuitos e confiáveis
- ✅ Jenkins UI completa para monitorizar builds
- ✅ Pipeline as code (Jenkinsfile)
- ✅ Totalmente grátis
- ✅ Não depende de Gitea

## 📋 Pré-requisitos

- MacBook Air com Docker instalado
- Repositório no GitHub
- 8GB RAM (Jenkins usa ~500MB-1GB)

---

## Fase 1: Instalar Jenkins no MacBook Air

### Passo 1.1: Criar Docker Compose para Jenkins

**No servidor, criar ficheiro:**

```bash
# SSH para o servidor
ssh vitormineiro@192.168.1.74

# Criar diretório para Jenkins
mkdir -p ~/Dev/jenkins
cd ~/Dev/jenkins

# Criar docker-compose.yml
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  jenkins:
    image: jenkins/jenkins:lts
    container_name: jenkins
    restart: unless-stopped

    ports:
      - "8080:8080"     # Jenkins UI
      - "50000:50000"   # Jenkins agents

    volumes:
      - jenkins_home:/var/jenkins_home
      - /var/run/docker.sock:/var/run/docker.sock

    environment:
      - JAVA_OPTS=-Dhudson.footerURL=http://192.168.1.74:8080

    user: root  # Necessário para aceder ao Docker socket

volumes:
  jenkins_home:
    name: jenkins_home
EOF
```

### Passo 1.2: Iniciar Jenkins

```bash
# No diretório ~/Dev/jenkins
docker-compose up -d

# Ver logs
docker logs -f jenkins
```

**Aguardar mensagem:**
```
Jenkins initial setup is required. An admin user has been created and a password generated.
Please use the following password to proceed to installation:

a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6

This may also be found at: /var/jenkins_home/secrets/initialAdminPassword
```

**IMPORTANTE:** Copia essa password!

### Passo 1.3: Completar Setup Inicial

1. **Abrir browser:** `http://192.168.1.74:8080`
2. **Colar password** obtida acima
3. **Install suggested plugins** (click e aguarda ~5 min)
4. **Create First Admin User:**
   - Username: `admin`
   - Password: (escolhe uma password forte)
   - Full name: `Jenkins Admin`
   - Email: `admin@baseanalysis.local`
5. **Jenkins URL:** `http://192.168.1.74:8080/` (deixar default)
6. Click **"Start using Jenkins"**

✅ Jenkins instalado!

---

## Fase 2: Configurar Acesso ao Docker

Jenkins precisa aceder ao Docker para fazer build de imagens.

```bash
# No servidor
docker exec -u root jenkins sh -c 'apt-get update && apt-get install -y docker.io'

# Verificar
docker exec jenkins docker ps
```

✅ Se mostrar lista de containers, está funcional!

---

## Fase 3: Configurar GitHub Integration

### Passo 3.1: Instalar Plugin do GitHub

1. **Jenkins UI:** `http://192.168.1.74:8080`
2. **Manage Jenkins** → **Manage Plugins**
3. **Available plugins** → Pesquisar: `GitHub`
4. Marcar: ☑️ **GitHub Integration Plugin**
5. Click **"Install without restart"**
6. Aguardar instalação

### Passo 3.2: Criar GitHub Personal Access Token

1. **GitHub:** `https://github.com/settings/tokens`
2. Click **"Generate new token (classic)"**
3. **Note:** `Jenkins CI/CD`
4. **Expiration:** 90 days (ou No expiration)
5. **Select scopes:**
   - ☑️ `repo` (Full control of private repositories)
   - ☑️ `admin:repo_hook` (Full control of repository hooks)
6. Click **"Generate token"**
7. **COPIAR TOKEN** (exemplo: `ghp_xxxxxxxxxxxxxxxxxxxx`)

### Passo 3.3: Configurar Credenciais no Jenkins

1. **Jenkins:** Manage Jenkins → Manage Credentials
2. **(global)** → Add Credentials
3. **Kind:** Secret text
4. **Secret:** Colar o GitHub token
5. **ID:** `github-token`
6. **Description:** GitHub Personal Access Token
7. Click **"Create"**

### Passo 3.4: Configurar GitHub Server

1. **Jenkins:** Manage Jenkins → Configure System
2. **GitHub** section → Add GitHub Server
3. **Name:** `GitHub`
4. **API URL:** `https://api.github.com` (default)
5. **Credentials:** Selecionar `github-token`
6. Click **"Test connection"** → Deve mostrar: "Credentials verified for user..."
7. **Save**

✅ Jenkins conectado ao GitHub!

---

## Fase 4: Criar Pipeline Job

### Passo 4.1: Criar Novo Job

1. **Jenkins:** New Item
2. **Enter an item name:** `base-data-etl-staging`
3. **Type:** Pipeline
4. Click **OK**

### Passo 4.2: Configurar Job

**General:**
- ☑️ GitHub project
- Project url: `https://github.com/SEU_USER/base-data-etl/`

**Build Triggers:**
- ☑️ GitHub hook trigger for GITScm polling

**Pipeline:**
- **Definition:** Pipeline script from SCM
- **SCM:** Git
- **Repository URL:** `https://github.com/SEU_USER/base-data-etl.git`
- **Credentials:** Selecionar `github-token`
- **Branch Specifier:** `*/main`
- **Script Path:** `Jenkinsfile`

Click **Save**

---

## Fase 5: Criar Jenkinsfile

No teu **repositório local** (laptop), criar ficheiro na raiz:

**`Jenkinsfile`:**

```groovy
pipeline {
    agent any

    environment {
        IMAGE_NAME = 'basedatafeed:staging'
        CONTAINER_NAME = 'basedatafeed-staging-app'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '📥 Cloning repository from GitHub...'
                checkout scm
            }
        }

        stage('Build Docker Image') {
            steps {
                echo '🔨 Building Docker image...'
                sh """
                    docker build -t ${IMAGE_NAME} .
                    docker tag ${IMAGE_NAME} ${IMAGE_NAME}-${BUILD_NUMBER}
                    docker tag ${IMAGE_NAME} ${IMAGE_NAME}-${GIT_COMMIT[0..7]}
                """
            }
        }

        stage('Deploy to Staging') {
            steps {
                echo '🚀 Deploying to staging...'
                sh """
                    docker restart ${CONTAINER_NAME}
                    sleep 15
                """
            }
        }

        stage('Verify Deployment') {
            steps {
                echo '✅ Verifying deployment...'
                sh """
                    CONTAINER_STATUS=\$(docker ps --filter "name=${CONTAINER_NAME}" --format "{{.Status}}" | head -1)
                    if echo "\$CONTAINER_STATUS" | grep -q "Up"; then
                        echo "✅ Deployment successful: \$CONTAINER_STATUS"
                        docker logs ${CONTAINER_NAME} --tail 20
                    else
                        echo "❌ Deployment failed: \$CONTAINER_STATUS"
                        docker logs ${CONTAINER_NAME} --tail 50
                        exit 1
                    fi
                """
            }
        }
    }

    post {
        success {
            echo '🎉 Pipeline completed successfully!'
        }
        failure {
            echo '❌ Pipeline failed!'
        }
    }
}
```

**Commit e push:**

```bash
git add Jenkinsfile
git commit -m "feat: add Jenkins pipeline for CI/CD automation"
git push origin main
```

---

## Fase 6: Configurar GitHub Webhook

### Passo 6.1: Obter Jenkins Webhook URL

**URL:** `http://192.168.1.74:8080/github-webhook/`

### Passo 6.2: Criar Webhook no GitHub

1. **GitHub repository:** Settings → Webhooks → Add webhook
2. **Payload URL:** `http://192.168.1.74:8080/github-webhook/`
3. **Content type:** `application/json`
4. **Secret:** (deixar vazio por agora)
5. **Which events:** Just the push event
6. ☑️ Active
7. Click **"Add webhook"**

**IMPORTANTE:** Se o MacBook Air não está acessível publicamente, o webhook não funciona. Alternativas:
- Usar **polling** no Jenkins (check GitHub a cada X minutos)
- Usar **ngrok** ou similar para expor Jenkins
- Push manual trigger via Jenkins UI

### Opção Alternativa: Polling (Sem Webhook)

**No Jenkins job:**
1. Edit job → Build Triggers
2. ☑️ Poll SCM
3. Schedule: `H/5 * * * *` (check a cada 5 minutos)
4. Save

---

## Fase 7: Testar Pipeline

### Teste Manual

1. **Jenkins:** Dashboard → `base-data-etl-staging`
2. Click **"Build Now"**
3. Ver progress em **Build History**
4. Click no build number → **Console Output**

**Expected:** ✅ Build SUCCESS

### Teste Automático (com Webhook ou Polling)

```bash
# No teu laptop
echo "# CI/CD Test" >> README.md
git add README.md
git commit -m "test: trigger Jenkins pipeline"
git push origin main

# Aguardar ~1-5 minutos (dependendo de webhook ou polling)
# Verificar Jenkins UI - deve aparecer novo build
```

---

## ✅ Verificação Final

**Checklist:**
- [ ] Jenkins acessível em `http://192.168.1.74:8080`
- [ ] GitHub plugin instalado
- [ ] Credenciais GitHub configuradas
- [ ] Pipeline job criado
- [ ] Jenkinsfile no repositório
- [ ] Build manual funciona
- [ ] Build automático dispara (webhook ou polling)
- [ ] Container staging atualizado após build

---

## 🎉 Resultado

Agora tens CI/CD totalmente automático:

```
Push to GitHub → (webhook ou polling) → Jenkins → Build → Deploy → ✅
```

**Próximo passo:** [Remover Gitea](GITEA_REMOVAL.md)

---

## 🐛 Troubleshooting

### Build falha: "docker: command not found"

```bash
docker exec -u root jenkins sh -c 'apt-get update && apt-get install -y docker.io'
docker restart jenkins
```

### Build falha: "permission denied" no Docker socket

```bash
# Dar permissões ao user jenkins
docker exec -u root jenkins sh -c 'usermod -aG docker jenkins'
docker restart jenkins
```

### Webhook não dispara

- Usar polling em vez de webhook: `H/5 * * * *`
- Verificar firewall não bloqueia porta 8080
- Usar ngrok se MacBook não está acessível publicamente

### Jenkins muito lento

```bash
# Aumentar memória Java
# Editar docker-compose.yml:
# JAVA_OPTS=-Xmx1024m -Dhudson.footerURL=http://192.168.1.74:8080
docker-compose down
docker-compose up -d
```

---

**Versão:** 1.0
**Data:** 2026-01-09
**Next:** Remover Gitea e cleanup
