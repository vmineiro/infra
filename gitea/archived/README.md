# Gitea Legacy Documentation (Archived)

Esta pasta contém documentação legacy sobre configurações Gitea que já não são utilizadas.

## Status: ARCHIVED

**Motivo:** Migração para Jenkins + GitHub para CI/CD

## Conteúdo

### CI_CD_SETUP.md
Guia para configurar CI/CD usando Gitea + webhooks + script de deployment.

**Substituído por:** Jenkins + GitHub (ver ../jenkins/SETUP.md)

### WEBHOOK_SETUP.md
Configuração de webhooks no Gitea para disparar deployments automáticos.

**Substituído por:** GitHub webhooks + Jenkins

### GITEA_REMOVAL.md
Guia para remover Gitea após migração para Jenkins + GitHub.

## CI/CD Atual

O sistema atual utiliza:
- **GitHub** - Hosting de código
- **Jenkins** - CI/CD automation (ver ../jenkins/)
- **GitHub Webhooks** - Triggers para Jenkins

## Gitea Ainda Disponível

O servidor Gitea ainda pode ser instalado e usado se necessário (ver ../docker-compose.yml e ../README.md), mas não é mais usado para CI/CD.

## Histórico

- **Antes:** Gitea + Gitea Actions (problemas de network isolation)
- **Depois:** Jenkins + GitHub (solução atual e funcional)
