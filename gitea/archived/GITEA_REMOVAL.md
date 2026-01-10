# Gitea Removal Guide

Guia para remover Gitea após migração para Jenkins + GitHub.

## 🎯 Contexto

Após configurar CI/CD com Jenkins + GitHub, o Gitea não é mais necessário para:
- CI/CD pipelines (agora Jenkins)
- Git hosting (agora GitHub)
- Webhooks (agora GitHub → Jenkins)

**Pode ser removido completamente.**

---

## ⚠️ Antes de Remover

### Verificar que Jenkins Funciona

```bash
# 1. Verificar Jenkins está a correr
docker ps | grep jenkins

# 2. Verificar último build teve sucesso
# Abrir: http://192.168.1.74:8080/job/base-data-etl-staging/

# 3. Testar push to GitHub dispara build
echo "# Test" >> README.md
git add README.md
git commit -m "test: verify Jenkins CI/CD"
git push origin main

# Aguardar 1-5 minutos, verificar build no Jenkins
```

✅ **Apenas continua se Jenkins está funcional!**

---

## Fase 1: Backup (Opcional)

Se quiseres manter backup dos repositórios Gitea:

```bash
# No servidor
cd ~/Dev/gitea

# Backup volumes
docker run --rm \
  -v gitea_gitea_data:/data \
  -v $(pwd):/backup \
  alpine tar czf /backup/gitea-backup-$(date +%Y%m%d).tar.gz /data

# Verificar
ls -lh gitea-backup-*.tar.gz
```

**Guardar este ficheiro** num local seguro (pendrive, cloud, etc.)

---

## Fase 2: Parar Gitea

```bash
# No servidor
cd ~/Dev/gitea

# Parar containers
docker-compose down

# Verificar pararam
docker ps | grep gitea  # Não deve mostrar nada
```

---

## Fase 3: Remover Containers e Volumes

```bash
# Remover containers
docker rm -f gitea gitea-runner 2>/dev/null || true

# Listar volumes Gitea
docker volume ls | grep gitea

# Remover volumes (CUIDADO: dados serão perdidos!)
docker volume rm gitea_gitea_data
docker volume rm gitea_gitea_runner_data

# Verificar foram removidos
docker volume ls | grep gitea  # Não deve mostrar nada
```

---

## Fase 4: Remover Networks

```bash
# Remover network gitea
docker network rm gitea_gitea 2>/dev/null || true

# Verificar
docker network ls | grep gitea  # Não deve mostrar nada
```

---

## Fase 5: Remover Ficheiros e Diretórios

```bash
# No servidor
rm -rf ~/Dev/gitea

# Verificar
ls ~/Dev/gitea  # Deve dar erro "No such file or directory"
```

---

## Fase 6: Remover Remote Gitea do Repositório Local

**No teu laptop:**

```bash
cd ~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/

# Listar remotes
git remote -v

# Remover remote gitea
git remote remove gitea

# Verificar
git remote -v
# Deve mostrar apenas:
# origin  git@github.com:SEU_USER/base-data-etl.git
```

---

## Fase 7: Limpar Workflows do Gitea

**No repositório, remover workflows Gitea Actions:**

```bash
# No laptop
cd ~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/

# Remover workflows Gitea (não são usados com Jenkins)
rm -rf .github/workflows/

# Verificar
ls -la .github/  # Diretório não deve existir

# Commit
git add .
git commit -m "chore: remove Gitea workflows (migrated to Jenkins)"
git push origin main
```

**Nota:** Jenkins usa `Jenkinsfile` na raiz, não `.github/workflows/`

---

## Fase 8: Atualizar Git Remote para GitHub

Se ainda tens `origin` a apontar para outro sítio:

```bash
# No laptop
cd ~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/

# Ver remotes atuais
git remote -v

# Se origin não é GitHub, atualizar
git remote set-url origin https://github.com/SEU_USER/base-data-etl.git

# Ou usar SSH (recomendado)
git remote set-url origin git@github.com:SEU_USER/base-data-etl.git

# Verificar
git remote -v
# Deve mostrar apenas GitHub
```

---

## Fase 9: Atualizar Documentação

**Remover referências ao Gitea na documentação:**

**Ficheiros a atualizar:**
- `README.md` - Remover secção Gitea Setup
- `gitea-setup/` - Remover diretório completo (se existir)
- Qualquer doc que mencione Gitea

**No README.md, atualizar secção de deployment:**

```markdown
## 🚀 Deployment & Automation

Automated CI/CD using Jenkins + GitHub.

### Development Workflow

```
GitHub Push → Jenkins Build → Docker Image → Deploy to Staging → ✅
```

### Setup

See: [deployment/docs/JENKINS_GITHUB_SETUP.md](deployment/docs/JENKINS_GITHUB_SETUP.md)
```

---

## ✅ Verificação Final

**Checklist:**
- [ ] Jenkins funciona e faz builds com sucesso
- [ ] GitHub push dispara build Jenkins
- [ ] Gitea containers parados e removidos
- [ ] Gitea volumes removidos
- [ ] Gitea networks removidas
- [ ] Diretório ~/Dev/gitea removido
- [ ] Remote `gitea` removido do repositório local
- [ ] Workflows `.github/workflows/` removidos
- [ ] `origin` remote aponta para GitHub
- [ ] Documentação atualizada

---

## 🎉 Resultado

**Antes:**
```
Gitea (local) ← Push ← Developer
  ↓
Gitea Actions (não funcionava)
  ↓
Manual deploy
```

**Depois:**
```
GitHub ← Push ← Developer
  ↓
Jenkins (webhook/polling)
  ↓
Automatic build + deploy ✅
```

**Benefícios:**
- ✅ CI/CD totalmente funcional
- ✅ Sem network isolation issues
- ✅ UI completa (Jenkins)
- ✅ GitHub como fonte única
- ✅ Menos infraestrutura para manter
- ✅ Mais RAM livre no MacBook Air (~300MB)

---

## 🔄 Rollback (se necessário)

Se precisares voltar atrás:

```bash
# Restaurar backup
cd ~/Dev/gitea
tar xzf gitea-backup-YYYYMMDD.tar.gz

# Iniciar Gitea novamente
docker-compose up -d
```

---

**Versão:** 1.0
**Data:** 2026-01-09
**Próximo:** Enjoy your automated CI/CD! 🚀
