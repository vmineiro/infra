# CI/CD Setup Guide

Guia completo para configurar deploy automático usando script + webhook.

## 🎯 Objetivo

Configurar deploy automático que executa em cada push to `main`:
1. Developer faz push para Gitea
2. Gitea dispara webhook
3. Script executa: pull → build → deploy
4. Container atualizado automaticamente

## 📋 Pré-requisitos

- ✅ Gitea instalado e a correr
- ✅ Docker instalado no servidor
- ✅ Repositório clonado em `~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/`
- ✅ Remote `gitea` configurado

## 🚀 Instalação

### Passo 1: Criar Diretórios

```bash
# No servidor MacBook Air (via SSH)
mkdir -p ~/scripts
mkdir -p ~/Dev/logs/ci-cd
```

### Passo 2: Copiar Script de Deploy

```bash
# No servidor
cp ~/Dev/VitorMineiro/BaseAnalysis/base-data-etl/deployment/scripts/ci-cd-deploy.sh \
   ~/scripts/ci-cd-deploy.sh

# Dar permissões de execução
chmod +x ~/scripts/ci-cd-deploy.sh

# Verificar
ls -la ~/scripts/ci-cd-deploy.sh
```

**Expected output:**
```
-rwxr-xr-x  1 vitormineiro  staff  5234 Jan  9 23:30 /Users/vitormineiro/scripts/ci-cd-deploy.sh
```

### Passo 3: Testar Script Manualmente

```bash
# No servidor
~/scripts/ci-cd-deploy.sh
```

**Expected output:**
```
[2026-01-09 23:30:15] ℹ️  =========================================
[2026-01-09 23:30:15] ℹ️  Starting CI/CD Deployment
[2026-01-09 23:30:15] ℹ️  =========================================
[2026-01-09 23:30:15] ℹ️  Step 1: Navigating to project directory...
[2026-01-09 23:30:15] ✅ In directory: /Users/vitormineiro/Dev/VitorMineiro/BaseAnalysis/base-data-etl
[2026-01-09 23:30:15] ℹ️  Step 2: Pulling latest changes from Gitea...
...
[2026-01-09 23:30:45] ✅ =========================================
[2026-01-09 23:30:45] ✅ CI/CD Deployment Completed Successfully
[2026-01-09 23:30:45] ✅ =========================================
```

✅ Se vires "Deployment Completed Successfully", o script está a funcionar!

### Passo 4: Instalar Webhook Listener

Vamos usar `webhook` - um servidor HTTP simples que escuta webhooks e executa comandos.

**Instalar webhook:**

```bash
# No servidor (macOS)
brew install webhook

# Verificar instalação
webhook --version
```

### Passo 5: Criar Configuração do Webhook

```bash
# Criar ficheiro de configuração
cat > ~/scripts/webhook-config.json << 'EOF'
[
  {
    "id": "ci-cd-deploy",
    "execute-command": "/Users/vitormineiro/scripts/ci-cd-deploy.sh",
    "command-working-directory": "/Users/vitormineiro/Dev/VitorMineiro/BaseAnalysis/base-data-etl",
    "response-message": "CI/CD deployment triggered",
    "trigger-rule": {
      "and": [
        {
          "match": {
            "type": "payload-hash-sha256",
            "secret": "SEU_SECRET_AQUI",
            "parameter": {
              "source": "header",
              "name": "X-Gitea-Signature"
            }
          }
        },
        {
          "match": {
            "type": "value",
            "value": "refs/heads/main",
            "parameter": {
              "source": "payload",
              "name": "ref"
            }
          }
        }
      ]
    }
  }
]
EOF
```

**IMPORTANTE:** Substitui `SEU_SECRET_AQUI` por um secret forte:

```bash
# Gerar secret aleatório
openssl rand -hex 32

# Exemplo de output: 5f3e8d9a2b1c4e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f
```

Edita o ficheiro e substitui `SEU_SECRET_AQUI` pelo secret gerado:

```bash
nano ~/scripts/webhook-config.json
# Substitui SEU_SECRET_AQUI
# Ctrl+X para sair, Y para guardar
```

### Passo 6: Criar Serviço para Webhook Listener

Vamos criar um LaunchAgent para o webhook listener correr sempre.

```bash
cat > ~/Library/LaunchAgents/com.baseanalysis.webhook.plist << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.baseanalysis.webhook</string>

    <key>ProgramArguments</key>
    <array>
        <string>/opt/homebrew/bin/webhook</string>
        <string>-hooks</string>
        <string>/Users/vitormineiro/scripts/webhook-config.json</string>
        <string>-verbose</string>
        <string>-port</string>
        <string>9000</string>
    </array>

    <key>RunAtLoad</key>
    <true/>

    <key>KeepAlive</key>
    <true/>

    <key>StandardOutPath</key>
    <string>/Users/vitormineiro/Dev/logs/webhook.log</string>

    <key>StandardErrorPath</key>
    <string>/Users/vitormineiro/Dev/logs/webhook-error.log</string>

    <key>WorkingDirectory</key>
    <string>/Users/vitormineiro</string>
</dict>
</plist>
EOF
```

**Carregar o serviço:**

```bash
launchctl load ~/Library/LaunchAgents/com.baseanalysis.webhook.plist

# Verificar que está a correr
launchctl list | grep webhook
```

**Expected output:**
```
-	0	com.baseanalysis.webhook
```

**Testar webhook listener:**

```bash
curl http://localhost:9000/hooks/ci-cd-deploy
```

Deve responder (mesmo que falhe autenticação, mostra que está a escutar).

### Passo 7: Configurar Firewall (Se Necessário)

Se o Gitea e webhook listener estão no mesmo servidor, não é necessário.

Se estiverem em máquinas diferentes:

```bash
# Permitir porta 9000
sudo pfctl -e
# Configurar regras conforme necessário
```

## ✅ Verificação

### Checklist de Instalação

- [ ] Diretórios criados (`~/scripts`, `~/Dev/logs/ci-cd`)
- [ ] Script copiado e com permissões (`chmod +x`)
- [ ] Teste manual do script funciona
- [ ] Webhook instalado (`brew install webhook`)
- [ ] Configuração webhook criada com secret
- [ ] LaunchAgent configurado e carregado
- [ ] Webhook listener responde em `localhost:9000`

### Ver Logs

```bash
# Logs do webhook listener
tail -f ~/Dev/logs/webhook.log

# Logs dos deploys
ls -lt ~/Dev/logs/ci-cd/ | head -5
```

## 🔗 Próximo Passo

Seguir o guia: **[WEBHOOK_SETUP.md](WEBHOOK_SETUP.md)** para configurar o webhook no Gitea.

---

## 🐛 Troubleshooting

### Script falha: "Permission denied"

```bash
chmod +x ~/scripts/ci-cd-deploy.sh
```

### Webhook listener não inicia

```bash
# Ver logs de erro
cat ~/Dev/logs/webhook-error.log

# Verificar se porta 9000 está livre
lsof -i :9000
```

### "Command not found: webhook"

```bash
# Verificar instalação
which webhook

# Reinstalar se necessário
brew reinstall webhook
```

### LaunchAgent não carrega

```bash
# Verificar sintaxe do plist
plutil -lint ~/Library/LaunchAgents/com.baseanalysis.webhook.plist

# Verificar permissões
ls -la ~/Library/LaunchAgents/com.baseanalysis.webhook.plist
```

---

**Próximo:** [Configurar Webhook no Gitea →](WEBHOOK_SETUP.md)
