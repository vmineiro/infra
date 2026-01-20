# Jenkins Docker Agents - Dockerfiles

Este diretório contém os Dockerfiles para construir imagens de agents Jenkins customizadas.

## Imagens Disponíveis

### Node.js Agent
**Ficheiro**: `Dockerfile.node`
**Imagem**: `jenkins-node-agent:latest`

```bash
docker build -t jenkins-node-agent:latest -f Dockerfile.node .
```

### Python Agent
**Ficheiro**: `Dockerfile.python`
**Imagem**: `jenkins-python-agent:latest`

```bash
docker build -t jenkins-python-agent:latest -f Dockerfile.python .
```

### Java Agent
Usa imagem oficial diretamente: `jenkins/agent:alpine-jdk17`

Não precisa de Dockerfile custom.

## Build de Todas as Imagens

```bash
# No servidor
cd ~/Dev/VitorMineiro/ServerInfra/infra/jenkins/docker-agents

# Node
docker build -t jenkins-node-agent:latest -f Dockerfile.node .

# Python
docker build -t jenkins-python-agent:latest -f Dockerfile.python .
```

## Documentação Completa

Ver [DOCKER-AGENTS.md](../DOCKER-AGENTS.md) para documentação completa sobre:
- Como usar os agents
- Como criar novos agents
- Troubleshooting
- Melhores práticas
