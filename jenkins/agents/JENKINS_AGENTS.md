# Jenkins Docker Agents - Configuração

Configuração de agents Docker para Jenkins com Node.js, Python e Docker CLI.

---

## 📦 Imagens Disponíveis

| Imagem | Base | Ferramentas | Uso |
|--------|------|-------------|-----|
| `jenkins-node-agent:latest` | Alpine + JDK17 | Node.js, npm, Docker CLI | Frontend builds, JS tests |
| `jenkins-python-agent:latest` | Alpine + JDK17 | Python 3, pip, Docker CLI | Python builds, testes |

---

## 🔨 Build das Imagens

### No MacBook Server:

```bash
cd ~/BaseAnalysis/base-data-etl/cloud/jenkins/agents

# Build ambas as imagens
./build-agents.sh all

# Ou build individual
./build-agents.sh node
./build-agents.sh python
```

### Verificar imagens criadas:

```bash
docker images | grep jenkins-
```

**Output esperado:**
```
jenkins-node-agent    latest    abc123...   2 minutes ago   XXX MB
jenkins-python-agent  latest    def456...   1 minute ago    XXX MB
```

---

## ⚙️ Configuração Jenkins Cloud

### 1. Aceder à Configuração

**Jenkins → Manage Jenkins → Clouds → Docker**

Ou criar novo: **New cloud → Docker**

---

### 2. Configuração Geral do Docker Cloud

**Docker Cloud details:**

| Campo | Valor |
|-------|-------|
| **Docker Host URI** | `unix:///var/run/docker.sock` |
| **Enabled** | ✅ Yes |
| **Container Cap** | `10` (máximo de containers simultâneos) |

**Test Connection:** Clica e verifica ✅ Version: Docker xxx

---

### 3. Configurar Docker Agent Template - Node.js

Clica em **"Docker Agent templates"** → **Add Docker Template**

#### Container settings:

| Campo | Valor |
|-------|-------|
| **Labels** | `node nodejs docker` |
| **Name** | `node-agent` |
| **Docker Image** | `jenkins-node-agent:latest` |
| **Remote File System Root** | `/home/jenkins/agent` |
| **Usage** | `Use this node as much as possible` |
| **Connect method** | `Attach Docker container` |

#### Advanced Settings:

| Campo | Valor |
|-------|-------|
| **Pull strategy** | `Never pull` (imagem é local) |
| **Remove volumes** | ✅ Yes |
| **Pull timeout** | `300` |

#### **CRÍTICO - Volumes:**

Clica em **"Add Volume"** → **Host path / Container path**

| Host path | Container path | Read only |
|-----------|----------------|-----------|
| `/var/run/docker.sock` | `/var/run/docker.sock` | ❌ No |

**Isto é ESSENCIAL** para que o agent consiga executar comandos Docker!

---

### 4. Configurar Docker Agent Template - Python

Repete o processo acima com estes valores:

| Campo | Valor |
|-------|-------|
| **Labels** | `python python3 docker` |
| **Name** | `python-agent` |
| **Docker Image** | `jenkins-python-agent:latest` |
| **Remote File System Root** | `/home/jenkins/agent` |
| **Usage** | `Use this node as much as possible` |

**Volumes:** Igual ao Node.js agent (montar Docker socket)

---

## 🎯 Usar Agents nos Jenkinsfiles

### Opção 1: Por Label

```groovy
pipeline {
    agent {
        label 'python'  // Usa python agent
    }

    stages {
        stage('Build') {
            steps {
                sh 'python --version'
                sh 'docker --version'
            }
        }
    }
}
```

### Opção 2: Agent por Stage

```groovy
pipeline {
    agent none  // Não usa agent default

    stages {
        stage('Python Tests') {
            agent { label 'python' }
            steps {
                sh 'pytest tests/'
            }
        }

        stage('Node Build') {
            agent { label 'node' }
            steps {
                sh 'npm install'
                sh 'npm run build'
            }
        }

        stage('Docker Build') {
            agent { label 'docker' }
            steps {
                sh 'docker build -t myapp .'
            }
        }
    }
}
```

### Opção 3: Usar Built-in (Atual)

```groovy
pipeline {
    agent any  // Usa qualquer agent disponível (incluindo built-in)

    environment {
        DOCKER_API_VERSION = '1.41'
    }

    stages {
        stage('Build') {
            steps {
                sh 'docker build -t image .'
            }
        }
    }
}
```

---

## 🔍 Verificar Configuração

### 1. Testar Conexão Docker

**Jenkins → Manage Jenkins → Clouds → Docker**

Clica em **"Test Connection"**

**Deve aparecer:** ✅ `Version: 20.10.x, API Version: 1.41`

---

### 2. Verificar Agents Disponíveis

**Jenkins → Build Executor Status**

Deve mostrar:
- `Built-in Node` (se executors > 0)
- Agents aparecem dinamicamente quando necessários

---

### 3. Test Job

Cria um test job:

```groovy
pipeline {
    agent { label 'python' }

    stages {
        stage('Test') {
            steps {
                sh '''
                    echo "=== Agent Info ==="
                    echo "Hostname: $(hostname)"
                    echo "User: $(whoami)"

                    echo ""
                    echo "=== Installed Tools ==="
                    java -version
                    python --version
                    docker --version

                    echo ""
                    echo "=== Docker Test ==="
                    docker ps

                    echo ""
                    echo "✅ Agent working correctly!"
                '''
            }
        }
    }
}
```

**Se falhar:** Verifica se montaste o Docker socket nos volumes!

---

## 🐛 Troubleshooting

### Erro: "permission denied while trying to connect to docker API"

**Causa:** Docker socket não foi montado ou permissões incorretas

**Solução:**

1. Verifica template tem volume configurado:
   - Host: `/var/run/docker.sock`
   - Container: `/var/run/docker.sock`

2. Verifica grupo docker no host:
   ```bash
   getent group docker
   ```

3. Adiciona ao Dockerfile se necessário:
   ```dockerfile
   USER root
   RUN addgroup -g $(stat -c %g /var/run/docker.sock) docker && \
       adduser jenkins docker
   USER jenkins
   ```

---

### Erro: "Cannot connect to the Docker daemon"

**Causa:** Docker daemon não está acessível

**Solução:**
```bash
# Verificar Docker está running
sudo systemctl status docker

# Verificar socket existe
ls -la /var/run/docker.sock

# Permissões do socket
sudo chmod 666 /var/run/docker.sock  # Temporário para teste
```

---

### Agent não aparece

**Causa:** Imagem não existe localmente

**Solução:**
```bash
# Rebuild imagem
cd ~/BaseAnalysis/base-data-etl/cloud/jenkins/agents
./build-agents.sh all

# Verificar
docker images | grep jenkins-
```

---

### Container fica stuck

**Causa:** Agent não consegue conectar ao Jenkins master

**Verificar logs:**
```bash
docker logs <container-id>
```

**Solução:**
- Verifica Jenkins master está acessível
- Verifica firewall não bloqueia comunicação
- Verifica "Connect method" está correto (Attach Docker container)

---

## 📊 Comparação: Built-in vs Docker Agents

| Aspeto | Built-in | Docker Agents |
|--------|----------|---------------|
| **Setup** | ✅ Zero config | ⚠️ Requer config |
| **Performance** | ✅ Mais rápido | ⚠️ Overhead containers |
| **Isolamento** | ❌ Partilhado | ✅ Containers isolados |
| **Limpeza** | ❌ Manual | ✅ Automática |
| **Recursos** | ⚠️ Sempre alocado | ✅ On-demand |
| **Docker builds** | ✅ Direto | ✅ Via socket mount |

---

## 🎯 Recomendações

### Para Jobs de CI/CD (Build de Imagens):

**Opção 1 - Built-in (ATUAL):** ✅ RECOMENDADO
```groovy
pipeline {
    agent any  // Usa built-in
    environment {
        DOCKER_API_VERSION = '1.41'
    }
}
```

**Vantagens:**
- ✅ Mais rápido (sem overhead)
- ✅ Acesso direto ao Docker
- ✅ Zero config

---

### Para Jobs de Testes (Python, Node):

**Opção 2 - Docker Agents:** ✅ RECOMENDADO
```groovy
pipeline {
    agent { label 'python' }
}
```

**Vantagens:**
- ✅ Ambiente limpo para cada build
- ✅ Isolamento de dependências
- ✅ Cleanup automático

---

## 📝 Manutenção

### Rebuild Imagens (Quando Atualizar Dependências):

```bash
cd ~/BaseAnalysis/base-data-etl/cloud/jenkins/agents
./build-agents.sh all
```

### Cleanup de Agents Velhos:

```bash
# Listar containers parados de agents
docker ps -a | grep jenkins-agent

# Remover agents antigos
docker container prune -f

# Remover imagens antigas
docker image prune -f
```

### Verificar Uso de Recursos:

```bash
# Agents ativos
docker ps --filter "ancestor=jenkins-node-agent"
docker ps --filter "ancestor=jenkins-python-agent"

# Uso de CPU/Memória
docker stats --no-stream
```

---

## 🔗 Recursos

- [Jenkins Docker Plugin](https://plugins.jenkins.io/docker-plugin/)
- [Jenkins Agent Docker Images](https://hub.docker.com/r/jenkins/agent)
- [Docker-in-Docker Security](https://jpetazzo.github.io/2015/09/03/do-not-use-docker-in-docker-for-ci/)

---

**Última atualização:** 2026-01-23
