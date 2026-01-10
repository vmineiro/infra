# Deploy Portainer via Git Repository

## Problema: Build Failed

O Portainer não consegue fazer build usando "Web editor" porque não tem acesso ao código-fonte (Dockerfile, contexto da aplicação).

**Erro:**
```
failed to solve: rpc error: code = Unknown desc = failed to solve with frontend dockerfile.v0:
failed to read dockerfile: open /var/lib/docker/tmp/buildkit-mount217554617/Dockerfile:
no such file or directory
```

**Causa:** Método "Web editor" apenas aceita o conteúdo do docker-compose, não tem acesso aos ficheiros do projeto.

**Solução:** Usar método **"Repository"** para o Portainer clonar o código do Gitea.

---

## 🚀 Solução A: Repo Público (Mais Rápida)

### Passo 1: Tornar Repositório Público no Gitea

1. Aceder Gitea: http://192.168.1.74:3000/VitorMineiroLda/base-data-etl
2. Click **Settings** (engrenagem)
3. Scroll down → **Danger Zone**
4. Click **"Make Public"**
5. Confirmar

### Passo 2: Deploy no Portainer via Repository

1. **Portainer → Local → Stacks → Add stack**

2. **Configuração:**
   - **Name:** `base-analysis-staging`
   - **Build method:** ✅ **Repository** (tab do meio)

3. **Repository settings:**
   ```
   Repository URL: http://192.168.1.74:3000/VitorMineiroLda/base-data-etl
   Repository reference: refs/heads/main
   Compose path: docker-compose.staging.portainer.yml
   ```

4. **Environment variables** (adicionar manualmente):
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

5. **Deploy the stack**

6. **Aguardar:**
   - Clone do repositório
   - Build da aplicação (~2-3 minutos)
   - Start dos containers

### Passo 3: Tornar Privado Novamente (Opcional)

Depois do deploy bem-sucedido:

1. Gitea → Settings → **"Make Private"**
2. Na Fase 5, configurar webhook com authentication

**Vantagens:**
- ✅ Deploy rápido para testar
- ✅ Pode tornar privado depois
- ✅ Webhooks funcionam com token

---

## 🔐 Solução B: Repo Privado com Token (Mais Segura)

### Passo 1: Criar Personal Access Token no Gitea

1. **Gitea → User Settings (canto superior direito) → Applications**

2. **Generate New Token:**
   - Token Name: `portainer-deploy`
   - Select scopes:
     - ✅ `read:repository` (READ access to repositories)
     - ✅ `read:organization` (READ access to organizations)
   - Click **"Generate Token"**

3. **COPIAR TOKEN IMEDIATAMENTE** (só aparece uma vez)
   - Exemplo: `a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0`

### Passo 2: Deploy no Portainer com Autenticação

1. **Portainer → Local → Stacks → Add stack**

2. **Configuração:**
   - **Name:** `base-analysis-staging`
   - **Build method:** ✅ **Repository**

3. **Repository settings:**
   ```
   Repository URL: http://192.168.1.74:3000/VitorMineiroLda/base-data-etl
   Repository reference: refs/heads/main
   Compose path: docker-compose.staging.portainer.yml
   ```

4. **Authentication:**
   - ✅ Enable **"Use authentication"**
   - Username: `<teu-username-gitea>` (ex: VitorMineiroLda)
   - Personal Access Token: `<token-copiado-passo1>`

5. **Environment variables:** (adicionar as mesmas do Solução A)

6. **Deploy the stack**

**Vantagens:**
- ✅ Repositório mantém-se privado desde início
- ✅ Mais seguro
- ✅ Token pode ser revogado se necessário

---

## 🆚 Comparação

| Aspeto | Solução A (Público) | Solução B (Token) |
|--------|-------------------|------------------|
| **Setup** | Rápido (2 min) | Médio (5 min) |
| **Segurança** | Repo público temporariamente | Repo privado sempre |
| **Webhooks** | Configurar depois com token | Token já configurado |
| **Recomendado para** | Testes rápidos, dev local | Production, deploy final |

---

## ✅ Após Deploy Bem-Sucedido

Independentemente da solução escolhida:

1. **Verificar containers:**
   ```bash
   docker ps | grep staging
   ```

2. **Verificar logs:**
   ```bash
   docker logs basedatafeed-staging-app --tail 50
   ```

3. **Testar PgAdmin:**
   ```bash
   open http://localhost:5051
   ```

4. **Verificar database:**
   ```bash
   docker exec -it basedatafeed-staging-db psql -U postgres -d basedata-staging -c "\dt"
   ```

---

## 🐛 Troubleshooting

### Erro: "Repository authentication required"

- Repositório é privado → Usar **Solução B** com token
- Ou tornar público → **Solução A**

### Erro: "Clone failed"

- Verificar URL está correto: `http://192.168.1.74:3000/VitorMineiroLda/base-data-etl`
- Verificar Gitea está acessível: `curl http://192.168.1.74:3000`
- Se usar token, verificar username e token estão corretos

### Erro: "Compose path not found"

- Verificar ficheiro existe no repo: `docker-compose.staging.portainer.yml`
- Path é relativo à raiz do repo

### Build demora muito (>10 minutos)

- Normal na primeira vez (download de imagens base)
- Verificar logs de build no Portainer (Stacks → base-analysis-staging → Editor → Logs)

---

## 📌 Recomendação

**Para primeira vez:** Usar **Solução A** (repo público) para testar rapidamente se tudo funciona.

**Após confirmar que funciona:** Tornar privado e configurar webhooks com token na Fase 5.

**Para production (futuro):** Usar **Solução B** desde o início.

---

**Versão:** 1.0
**Data:** 2026-01-07
**Stack:** base-analysis-staging
