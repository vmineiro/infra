# Webhook Setup Guide - Gitea

Guia para configurar webhook no Gitea que dispara deploy automático.

## 🎯 Objetivo

Configurar Gitea para enviar webhook ao servidor quando há push to `main`, disparando o script de deploy automático.

## 📋 Pré-requisitos

- ✅ [CI/CD Setup](CI_CD_SETUP.md) completado
- ✅ Webhook listener a correr no servidor (porta 9000)
- ✅ Secret configurado em `~/scripts/webhook-config.json`

## 🔧 Configuração no Gitea

### Passo 1: Obter o Secret

```bash
# No servidor, ver o secret configurado
grep '"secret"' ~/scripts/webhook-config.json
```

Copia o valor do secret (exemplo: `5f3e8d9a2b1c4e6f7a8b9c0d1e2f3a4b...`)

### Passo 2: Aceder às Configurações do Repositório

1. **Abrir browser:** `http://192.168.1.74:3000/VitorMineiroLda/base-data-etl`
2. **Clicar em "Settings"** (tab no topo)
3. **No menu lateral, clicar em "Webhooks"**
4. **Clicar em "Add Webhook" → "Gitea"**

### Passo 3: Configurar Webhook

**Target URL:**
```
http://192.168.1.74:9000/hooks/ci-cd-deploy
```

**HTTP Method:**
- Selecionar: `POST`

**POST Content Type:**
- Selecionar: `application/json`

**Secret:**
- Colar o secret obtido no Passo 1

**Trigger On:**
- Selecionar apenas: ☑️ **Push events**
- Desmarcar: ☐ Create events, Delete events, Fork events, etc

**Branch filter (opcional):**
- Deixar vazio OU colocar: `main`
- Isto garante que apenas push to main dispara o webhook

**Active:**
- ☑️ **Active** (marcar)

**Clicar em "Add Webhook"**

### Passo 4: Testar Webhook

**Na página de webhooks:**
1. Encontrar o webhook recém-criado
2. Clicar no webhook para ver detalhes
3. Scroll para baixo até "Recent Deliveries"
4. Clicar em **"Test Delivery"**

**Expected output:**
- Status: `200 OK`
- Response: `CI/CD deployment triggered`

**Ver logs no servidor:**

```bash
# Ver logs do webhook listener
tail -20 ~/Dev/logs/webhook.log

# Ver se deploy foi disparado
ls -lt ~/Dev/logs/ci-cd/ | head -3
```

## ✅ Verificação Completa

### Teste End-to-End

Fazer um pequeno commit e push:

```bash
# No teu laptop
cd ~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/

# Fazer pequena mudança (exemplo)
echo "# CI/CD Test" >> deployment/TEST.md

git add deployment/TEST.md
git commit -m "test: verify CI/CD webhook"
git push gitea main
```

**Verificar:**

1. **No Gitea (browser):**
   - Ir a Settings → Webhooks
   - Clicar no webhook
   - Ver "Recent Deliveries" - deve aparecer nova entrega
   - Status deve ser `200 OK`

2. **No servidor (SSH):**
   ```bash
   # Ver logs do webhook
   tail -20 ~/Dev/logs/webhook.log

   # Ver último deploy
   tail -50 ~/Dev/logs/ci-cd/deploy-*.log | tail -50

   # Verificar container foi atualizado
   docker ps | grep basedatafeed-staging-app
   docker logs basedatafeed-staging-app --tail 20
   ```

Se tudo correr bem, deves ver:
- ✅ Webhook entregue com sucesso no Gitea
- ✅ Script de deploy executado
- ✅ Container reiniciado
- ✅ Logs mostram "Deployment Completed Successfully"

## 📊 Monitorização

### Ver Histórico de Webhooks

**No Gitea:**
1. Settings → Webhooks → Click no webhook
2. "Recent Deliveries" mostra últimas 10 entregas
3. Click em cada entrega para ver request/response completo

### Ver Histórico de Deploys

```bash
# No servidor

# Listar todos os deploys
ls -lt ~/Dev/logs/ci-cd/

# Ver último deploy
tail -100 $(ls -t ~/Dev/logs/ci-cd/deploy-*.log | head -1)

# Ver deploys de hoje
ls -lt ~/Dev/logs/ci-cd/deploy-$(date +%Y%m%d)-*.log
```

### Ver Logs em Tempo Real

```bash
# Webhook listener
tail -f ~/Dev/logs/webhook.log

# Em outra janela, fazer push e ver webhook a disparar!
```

## 🔐 Segurança

### Secret Token

O secret token garante que apenas o Gitea pode disparar o webhook:

```
Gitea → Assina request com secret → Webhook listener → Valida signature → Executa script
```

Sem secret válido, request é rejeitado.

### IP Whitelist (Opcional)

Se quiseres restringir ainda mais:

```bash
# Editar webhook-config.json
nano ~/scripts/webhook-config.json
```

Adicionar regra:

```json
{
  "match": {
    "type": "ip-whitelist",
    "ip-range": "192.168.1.0/24"
  }
}
```

## 🐛 Troubleshooting

### Webhook falha: "Connection refused"

**Verificar webhook listener está a correr:**

```bash
launchctl list | grep webhook
curl http://localhost:9000/hooks/ci-cd-deploy
```

**Se não estiver:**

```bash
launchctl load ~/Library/LaunchAgents/com.baseanalysis.webhook.plist
```

### Webhook entregue mas deploy não executa

**Ver logs:**

```bash
# Webhook recebeu o request?
tail -50 ~/Dev/logs/webhook.log

# Deploy foi disparado?
ls -lt ~/Dev/logs/ci-cd/ | head -3
```

**Causas comuns:**
- Secret incorreto (webhook listener rejeita)
- Branch filter bloqueou (push não era para main)
- Script tem erro (ver logs do deploy)

### Webhook retorna erro 500

**Ver logs de erro:**

```bash
cat ~/Dev/logs/webhook-error.log
```

**Causas comuns:**
- Script não tem permissões de execução (`chmod +x`)
- Path do script incorreto no webhook-config.json
- Script tem erro de sintaxe

## 📝 Configuração Final

Após configuração completa:

```
┌─────────────────┐
│ Push to main    │  ← Developer
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Gitea           │  ← Webhook dispara
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Webhook         │  ← Valida secret
│ Listener :9000  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ ci-cd-deploy.sh │  ← Executa deploy
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Container       │  ← Atualizado!
│ Updated         │
└─────────────────┘
```

## ✅ Checklist Final

- [ ] Webhook criado no Gitea
- [ ] Secret configurado corretamente
- [ ] Trigger: Push events only
- [ ] Branch filter: main (opcional)
- [ ] Active: marcado
- [ ] Test Delivery: 200 OK
- [ ] Push real dispara deploy
- [ ] Logs mostram sucesso
- [ ] Container atualizado

---

**🎉 Parabéns! CI/CD automático configurado com sucesso!**

Agora cada `git push gitea main` dispara deploy automático. 🚀

---

## 📚 Recursos Adicionais

- [Gitea Webhooks Documentation](https://docs.gitea.io/en-us/webhooks/)
- [Webhook Tool Documentation](https://github.com/adnanh/webhook)
- [Back to deployment/](../README.md)
