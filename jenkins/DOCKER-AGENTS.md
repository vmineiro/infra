# Jenkins Docker Agents

Guia completo para configuração e uso de Docker agents no Jenkins.

## 📋 Índice

- [Visão Geral](#visão-geral)
- [Agents Disponíveis](#agents-disponíveis)
- [Como Usar](#como-usar)
- [Como Criar Novos Agents](#como-criar-novos-agents)
- [Troubleshooting](#troubleshooting)

---

## Visão Geral

### O Que São Docker Agents?

Docker agents são containers Docker criados dinamicamente pelo Jenkins para executar builds. Cada build roda num container isolado e limpo, que é automaticamente removido após a execução.

### Vantagens

✅ **Isolamento** - Cada build num ambiente limpo
✅ **Eficiência** - Criados on-demand, removidos automaticamente
✅ **Consistência** - Mesmo ambiente em todos os builds
✅ **Segurança** - Não poluem o Jenkins master
✅ **Escalabilidade** - Fácil adicionar novos tipos de agents

### Arquitetura

```
Jenkins Master (Container)
    ↓
Docker Socket (/var/run/docker.sock)
    ↓
Docker Agent Containers (criados dinamicamente)
    - jenkins-node-agent (Node.js + npm)
    - jenkins-python-agent (Python + pip)
    - jenkins-java-agent (JDK 17)
```

---

## Agents Disponíveis

### 1. Node.js Agent

**Label**: `docker-node`
**Imagem**: `jenkins-node-agent:latest`
**Inclui**:
- Node.js v22.x
- npm v10.x
- Docker CLI
- Alpine Linux

**Uso típico**: Projetos JavaScript/TypeScript, React, Vue, etc.

### 2. Python Agent

**Label**: `docker-python`
**Imagem**: `jenkins-python-agent:latest`
**Inclui**:
- Python 3.x
- pip
- Docker CLI
- Build tools (gcc, make)
- Alpine Linux

**Uso típico**: Projetos Python, Django, Flask, scripts, etc.

### 3. Java Agent

**Label**: `docker-java`
**Imagem**: `jenkins/agent:alpine-jdk17`
**Inclui**:
- OpenJDK 17
- Docker CLI
- Alpine Linux

**Uso típico**: Projetos Java, Gradle, Maven, Kotlin, etc.

---

## Como Usar

### No Jenkinsfile

Simplesmente especificar o label do agent desejado:

#### Node.js
```groovy
pipeline {
    agent { label 'docker-node' }

    stages {
        stage('Build') {
            steps {
                sh 'npm install'
                sh 'npm run build'
            }
        }

        stage('Test') {
            steps {
                sh 'npm test'
            }
        }
    }
}
```

#### Python
```groovy
pipeline {
    agent { label 'docker-python' }

    stages {
        stage('Setup') {
            steps {
                sh 'pip install -r requirements.txt'
            }
        }

        stage('Test') {
            steps {
                sh 'pytest'
            }
        }
    }
}
```

#### Java/Gradle
```groovy
pipeline {
    agent { label 'docker-java' }

    stages {
        stage('Build') {
            steps {
                sh './gradlew clean build'
            }
        }

        stage('Test') {
            steps {
                sh './gradlew test'
            }
        }
    }
}
```

### Agents Diferentes por Stage

```groovy
pipeline {
    agent none

    stages {
        stage('Frontend Build') {
            agent { label 'docker-node' }
            steps {
                sh 'npm run build'
            }
        }

        stage('Backend Build') {
            agent { label 'docker-java' }
            steps {
                sh './gradlew build'
            }
        }
    }
}
```

---

## Como Criar Novos Agents

### Passo 1: Criar Dockerfile

Criar ficheiro no diretório `jenkins/docker-agents/`:

**Exemplo - Agent Ruby:**

```dockerfile
FROM jenkins/agent:alpine-jdk17

USER root
RUN apk add --no-cache ruby ruby-dev docker-cli build-base
USER jenkins
```

**Template Genérico:**

```dockerfile
FROM jenkins/agent:alpine-jdk17

USER root
# Instalar ferramentas necessárias + docker-cli
RUN apk add --no-cache <suas-ferramentas> docker-cli
USER jenkins
```

**Importante**:
- Sempre incluir `docker-cli` se os pipelines precisarem de Docker
- Usar `USER jenkins` no final
- Basear em `jenkins/agent:alpine-jdk17`

### Passo 2: Build da Imagem

No servidor:

```bash
cd ~/Dev/VitorMineiro/ServerInfra/infra/jenkins/docker-agents

# Build
docker build -t jenkins-<nome>-agent:latest -f Dockerfile.<nome> .

# Verificar
docker images | grep jenkins-<nome>-agent
```

### Passo 3: Criar Template no Jenkins

1. Abrir Jenkins UI: `http://192.168.1.74:8080`
2. **Manage Jenkins → Clouds → docker**
3. Scroll até **Docker Agent templates**
4. Clicar **Add Docker Template**

**Configuração:**

| Campo | Valor |
|-------|-------|
| Labels | `docker-<nome>` |
| Name | `<nome>-agent` |
| Docker Image | `jenkins-<nome>-agent:latest` |
| Remote File System Root | `/home/jenkins/agent` |
| Instance Capacity | `2` |
| Usage | Use this node as much as possible |
| Connect method | Attach Docker container |
| Pull strategy | Never pull |
| Remove volumes | ✅ Marcado |

**Mounts** (crucial para Docker CLI):
```
type=bind,source=/var/run/docker.sock,destination=/var/run/docker.sock
```

5. Clicar **Save**

### Passo 4: Testar

Criar pipeline de teste:

```groovy
pipeline {
    agent { label 'docker-<nome>' }

    stages {
        stage('Test') {
            steps {
                sh '<comando-teste>'
                sh 'docker --version'  // Verificar Docker CLI
            }
        }
    }
}
```

---

## Dockerfiles Existentes

### Localização

```
jenkins/docker-agents/
├── Dockerfile.node      # Node.js agent
├── Dockerfile.python    # Python agent
└── README.md           # Este ficheiro
```

### Node.js Agent

```dockerfile
FROM jenkins/agent:alpine-jdk17

USER root
RUN apk add --no-cache nodejs npm docker-cli
USER jenkins
```

**Build:**
```bash
docker build -t jenkins-node-agent:latest -f Dockerfile.node .
```

### Python Agent

```dockerfile
FROM jenkins/agent:alpine-jdk17

USER root
RUN apk add --no-cache python3 py3-pip python3-dev build-base docker-cli
USER jenkins
```

**Build:**
```bash
docker build -t jenkins-python-agent:latest -f Dockerfile.python .
```

### Java Agent

Usa imagem oficial diretamente:
```
jenkins/agent:alpine-jdk17
```

Adicionar Docker CLI via Mounts no template.

---

## Troubleshooting

### Agent Não Arranca

**Sintoma**: "Waiting for next available executor"

**Verificações:**

1. **Template está Enabled?**
   - Manage Jenkins → Clouds → docker
   - Verificar que template não está "Disabled by system"

2. **Docker Cloud está conectado?**
   - Manage Jenkins → Clouds → docker
   - Clicar **Test Connection**
   - Deve mostrar: "Version = X.X.X"

3. **Label está correto?**
   - Verificar label no template: `docker-node`
   - Verificar no Jenkinsfile: `agent { label 'docker-node' }`

4. **Imagem existe?**
   ```bash
   docker images | grep jenkins-node-agent
   ```

### Erro "Container is not running"

**Causa**: Container arranca mas para imediatamente.

**Solução**:
- Verificar **Command** está vazio no template
- Verificar **Remote File System Root**: `/home/jenkins/agent`
- Verificar imagem base é `jenkins/agent:*`

### Erro "java: executable file not found"

**Causa**: Imagem não tem Java (necessário para Jenkins agent).

**Solução**: Basear sempre em `jenkins/agent:alpine-jdk17`

### Erro "docker: command not found"

**Causa**: Docker CLI não instalado ou socket não montado.

**Soluções**:

1. Adicionar `docker-cli` no Dockerfile:
   ```dockerfile
   RUN apk add --no-cache docker-cli
   ```

2. Montar socket no template (campo **Mounts**):
   ```
   type=bind,source=/var/run/docker.sock,destination=/var/run/docker.sock
   ```

3. Rebuild da imagem:
   ```bash
   docker build -t jenkins-<nome>-agent:latest -f Dockerfile.<nome> .
   ```

### Erro "Invalid mount: expected key=value"

**Causa**: Formato errado no campo Mounts.

**Solução**: Usar formato correto:
```
type=bind,source=/var/run/docker.sock,destination=/var/run/docker.sock
```

### Agent Demora Muito a Arrancar

**Causa**: Imagem sendo baixada (pull).

**Solução**:
- Mudar **Pull strategy** para **Never pull**
- Pré-baixar imagem: `docker pull jenkins-node-agent:latest`

### Builds Falham com "Out of Memory"

**Solução**: Aumentar memória no template:

- **Memory Limit in MB**: `1024` (ou mais)

---

## Manutenção

### Atualizar Imagens

Quando atualizar Dockerfiles:

```bash
cd ~/Dev/VitorMineiro/ServerInfra/infra/jenkins/docker-agents

# Rebuild
docker build -t jenkins-node-agent:latest -f Dockerfile.node .
docker build -t jenkins-python-agent:latest -f Dockerfile.python .

# Limpar imagens antigas
docker image prune -f
```

Não precisa reconfigurar templates - vão usar novas imagens automaticamente.

### Limpar Agents Antigos

Agents são removidos automaticamente após builds, mas para limpar manualmente:

1. **Manage Jenkins → Nodes**
2. Apagar nodes offline com nome `<agent>-xxxxx`

### Verificar Uso de Recursos

```bash
# Ver agents em execução
docker ps | grep jenkins

# Ver uso de recursos
docker stats --no-stream

# Ver espaço usado
docker system df
```

---

## Melhores Práticas

### 1. Nomenclatura Consistente

- Labels: `docker-<tecnologia>` (ex: `docker-node`, `docker-python`)
- Imagens: `jenkins-<tecnologia>-agent:latest`
- Dockerfiles: `Dockerfile.<tecnologia>`

### 2. Sempre Incluir Docker CLI

Se pipelines fazem deploy ou verificam containers:

```dockerfile
RUN apk add --no-cache docker-cli
```

E montar socket:
```
type=bind,source=/var/run/docker.sock,destination=/var/run/docker.sock
```

### 3. Manter Imagens Pequenas

- Usar Alpine Linux
- Instalar apenas dependências necessárias
- Limpar cache após instalações:
  ```dockerfile
  RUN apk add --no-cache nodejs npm && \
      rm -rf /var/cache/apk/*
  ```

### 4. Versões Específicas (Produção)

Para ambientes críticos, usar versões específicas:

```dockerfile
FROM jenkins/agent:alpine-jdk17

USER root
RUN apk add --no-cache \
    nodejs=20.11.1-r0 \
    npm=10.2.5-r0 \
    docker-cli=24.0.7-r0
USER jenkins
```

### 5. Limpeza Automática

Job Jenkins semanal (já configurado) limpa recursos Docker automaticamente.

---

## Configuração Atual

### Jenkins Master

- **Imagem**: `jenkins/jenkins:lts`
- **Executors**: `0` (built-in node desabilitado)
- **Docker socket**: `/var/run/docker.sock` montado

### Docker Cloud

- **Host URI**: `unix:///var/run/docker.sock`
- **Enabled**: ✅
- **Connection**: Testada e funcional

### Templates Configurados

| Label | Imagem | Ferramentas |
|-------|--------|-------------|
| `docker-node` | `jenkins-node-agent:latest` | Node.js 22, npm 10, Docker CLI |
| `docker-python` | `jenkins-python-agent:latest` | Python 3, pip, Docker CLI |
| `docker-java` | `jenkins/agent:alpine-jdk17` | JDK 17, Docker CLI |

---

## Recursos Adicionais

### Documentação Oficial

- [Jenkins Docker Plugin](https://plugins.jenkins.io/docker-plugin/)
- [Jenkins Agent Images](https://hub.docker.com/r/jenkins/agent/)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)

### Comandos Úteis

```bash
# Listar agents em execução
docker ps | grep jenkins

# Ver logs de um agent
docker logs <container-id>

# Entrar num agent (debug)
docker exec -it <container-id> sh

# Limpar recursos Docker
docker system prune -a -f

# Ver imagens Jenkins
docker images | grep jenkins
```

---

**Versão**: 1.0
**Data**: 2026-01-20
**Autor**: DevOps Team
**Próxima revisão**: Quando adicionar novos agents
