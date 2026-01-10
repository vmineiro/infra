# Jenkins CI/CD Server

Configuração para instalar e executar Jenkins no servidor.

## Descrição

Jenkins é um servidor de automação open-source usado para CI/CD. Esta pasta contém:
- **docker-compose.yml** - Configuração Docker para executar Jenkins
- **SETUP.md** - Guia completo de instalação e configuração

## Instalação Rápida

### 1. Iniciar Jenkins

```bash
cd ~/Dev/infrastructure/jenkins  # (ou onde colocar esta pasta)
docker-compose up -d
```

### 2. Obter Password Inicial

```bash
docker logs jenkins 2>&1 | grep -A 2 "Please use the following password"
```

Ou:

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

### 3. Aceder ao Jenkins

Abrir: http://192.168.1.74:8080 (ou http://localhost:8080)

### 4. Configuração Inicial

1. Colar a password obtida
2. Instalar plugins sugeridos
3. Criar utilizador admin
4. Configurar URL do Jenkins

## Configuração para Projetos

Depois de instalar o Jenkins, cada projeto configura o seu próprio pipeline:
- Ver **SETUP.md** para instruções completas de configuração
- Cada projeto tem o seu `Jenkinsfile` que define o pipeline específico

## Comandos Úteis

```bash
# Ver logs
docker logs -f jenkins

# Reiniciar
docker-compose restart

# Parar
docker-compose down

# Atualizar Jenkins
docker-compose pull
docker-compose up -d
```

## Portas

- **8080** - Interface web do Jenkins
- **50000** - Porta para agentes Jenkins (se usar distributed builds)

## Volumes

- **jenkins_home** - Dados persistentes do Jenkins (jobs, configurações, plugins)
- **/var/run/docker.sock** - Socket Docker (permite ao Jenkins construir imagens Docker)

## Requisitos

- Docker e Docker Compose instalados
- ~500MB-1GB RAM disponível
- Portas 8080 e 50000 disponíveis

## Documentação

- [Guia Completo de Setup](SETUP.md) - Instalação e configuração detalhada
- [Jenkins Official Docs](https://www.jenkins.io/doc/)
