# Configuração do Cloudflare Tunnel - Instruções de Instalação

## ⚠️ MÉTODO ANTIGO (Locally-Managed) - DEPRECATED

**Este documento descreve o método antigo de gestão de túneis via ficheiro `config.yml` local.**

### Novo Método Recomendado: Remotely-Managed Tunnels

✅ **Use o novo método gerido via Dashboard Web:**
- **Documentação:** [CLOUDFLARED_REMOTELY_MANAGED.md](CLOUDFLARED_REMOTELY_MANAGED.md)
- **Vantagens:** Sem ficheiros de configuração, DNS automático, gestão centralizada
- **Ideal para:** Quem tem poucas rotas e quer simplicidade

Este documento (método locally-managed) é mantido apenas para referência histórica e para quem precisa de configurações muito avançadas não suportadas pelo Dashboard.

---

## Ficheiros Criados

- `cloudflared-config.yml` - Configuração do túnel (copiar para servidor) - **DEPRECATED**

## Tunnel Info

```
Name: cicd
ID: 5c221661-edd8-4264-a11d-bf12c0edd821
```

## Passo-a-Passo de Instalação no Servidor

### 1. Copiar ficheiro de configuração

```bash
# No servidor onde Jenkins está rodando (192.168.1.74)
# Copiar cloudflared-config.yml para ~/.cloudflared/config.yml

mkdir -p ~/.cloudflared
cp cloudflared-config.yml ~/.cloudflared/config.yml
```

### 2. Verificar ficheiro de credenciais

O ficheiro de credenciais deve já existir:
```bash
ls -la ~/.cloudflared/5c221661-edd8-4264-a11d-bf12c0edd821.json
```

Se não existir, fazer login de novo:
```bash
cloudflared tunnel login
```

### 3. Configurar DNS

```bash
cloudflared tunnel route dns cicd cicd.vitormineiro.com
```

**Resultado esperado:**
```
Successfully created route for cicd.vitormineiro.com
```

### 4. Testar túnel manualmente

```bash
cloudflared tunnel run cicd
```

**Verificar:**
- Logs mostram "Connection registered"
- Abrir navegador: https://cicd.vitormineiro.com
- Deve ver página de login do Jenkins

**Parar teste:** `Ctrl+C`

### 5. Instalar como serviço (daemon)

#### macOS:
```bash
sudo cloudflared service install
sudo launchctl start com.cloudflare.cloudflared
```

#### Linux:
```bash
sudo cloudflared service install
sudo systemctl start cloudflared
sudo systemctl enable cloudflared
```

### 6. Verificar status

#### macOS:
```bash
sudo launchctl list | grep cloudflare
tail -f /var/log/cloudflared.log
```

#### Linux:
```bash
sudo systemctl status cloudflared
sudo journalctl -u cloudflared -f
```

### 7. Testar acesso externo

```bash
# De qualquer máquina (ou telemóvel)
curl -I https://cicd.vitormineiro.com

# Deve retornar 200 OK ou 403 (Jenkins login)
```

---

## Adicionar Mais Serviços (Demos)

Para adicionar websites de demo, editar `~/.cloudflared/config.yml`:

```yaml
tunnel: 5c221661-edd8-4264-a11d-bf12c0edd821
credentials-file: ~/.cloudflared/5c221661-edd8-4264-a11d-bf12c0edd821.json

ingress:
  # CI/CD Platform (Jenkins) - Serve múltiplos projetos
  - hostname: cicd.vitormineiro.com
    service: http://192.168.1.74:8080
    originRequest:
      noTLSVerify: true

  # Website de demo
  - hostname: demo.vitormineiro.com
    service: http://localhost:3000
    originRequest:
      noTLSVerify: true

  # API de demo
  - hostname: api.vitormineiro.com
    service: http://localhost:8000
    originRequest:
      noTLSVerify: true

  # Catch-all
  - service: http_status:404
```

Depois de editar:
```bash
# Configurar DNS para cada novo hostname
cloudflared tunnel route dns cicd demo.vitormineiro.com
cloudflared tunnel route dns cicd api.vitormineiro.com

# Reiniciar serviço
# macOS:
sudo launchctl restart com.cloudflare.cloudflared

# Linux:
sudo systemctl restart cloudflared
```

---

## Configurar Webhook do GitHub

Depois do túnel ativo:

1. **GitHub Repository:** https://github.com/VitorMineiro/base-data-etl/settings/hooks
2. **Payload URL:** `https://cicd.vitormineiro.com/github-webhook/`
3. **Content type:** `application/json`
4. **Secret:** Gerar com `openssl rand -hex 32`
5. **Events:** "Just the push event"
6. **Active:** Checked
7. Salvar

**No Jenkins:**
- Manage Jenkins → Credentials
- Adicionar o mesmo secret do GitHub
- ID: `github-webhook-secret`

Ver guia completo: [`cloud/jenkins/WEBHOOK_SECURITY.md`](cloud/jenkins/WEBHOOK_SECURITY.md)

---

## Troubleshooting

### Túnel não conecta
```bash
# Ver logs
tail -f /var/log/cloudflared.log

# Testar manualmente
cloudflared tunnel run cicd
```

### 502 Bad Gateway
```bash
# Verificar se Jenkins está rodando
curl http://192.168.1.74:8080

# Verificar IP correto no config.yml
```

### DNS não resolve
```bash
# Verificar rota DNS
cloudflared tunnel route dns list

# Verificar propagação
dig cicd.vitormineiro.com +short
```

---

## Comandos Úteis

```bash
# Listar túneis
cloudflared tunnel list

# Ver rotas DNS
cloudflared tunnel route dns list

# Parar serviço
sudo launchctl stop com.cloudflare.cloudflared      # macOS
sudo systemctl stop cloudflared                       # Linux

# Reiniciar serviço
sudo launchctl restart com.cloudflare.cloudflared    # macOS
sudo systemctl restart cloudflared                    # Linux

# Ver logs
tail -f /var/log/cloudflared.log                     # macOS
sudo journalctl -u cloudflared -f                     # Linux
```

---

## Segurança

- ✅ Túnel criptografado (TLS)
- ✅ Sem port forwarding no router
- ✅ DDoS protection da Cloudflare
- ✅ Jenkins permanece em rede privada (192.168.1.74)
- ⚠️ Configurar webhook secrets (ver WEBHOOK_SECURITY.md)
- ⚠️ Considerar Cloudflare Access para autenticação extra

---

## Migrar para o Novo Método (Recomendado)

Se já tem um túnel locally-managed (este método) e quer migrar para remotely-managed (gerido via Dashboard):

1. **Ver guia de migração:** `/Users/vitormineiro/.claude/plans/swift-puzzling-flute.md`
2. **Ou criar novo túnel:** Seguir [CLOUDFLARED_REMOTELY_MANAGED.md](CLOUDFLARED_REMOTELY_MANAGED.md)

**Vantagens da migração:**
- ✅ Sem necessidade de editar `config.yml`
- ✅ Adicionar/remover rotas via Dashboard (sem restart)
- ✅ DNS gerido automaticamente
- ✅ Gestão centralizada de todos os túneis

---

## Documentação Adicional

- **[CLOUDFLARED_REMOTELY_MANAGED.md](CLOUDFLARED_REMOTELY_MANAGED.md)** - NOVO MÉTODO (Recomendado)
- [Webhook Security Guide](WEBHOOK_SECURITY.md)
- [Cloudflare Tunnel para Demos](CLOUDFLARE_TUNNEL_DEMOS.md)
- [Cloudflare Tunnel Docs](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/)
