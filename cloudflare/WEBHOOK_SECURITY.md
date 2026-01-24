# Plano: Implementar Webhooks Seguros do GitHub para Jenkins Local

## ⚠️ MÉTODO DE CONFIGURAÇÃO DO TÚNEL ATUALIZADO

**Este documento usa instruções do método antigo (locally-managed) para configurar o Cloudflare Tunnel.**

Para configuração mais simples (recomendado), consulte: **[CLOUDFLARED_REMOTELY_MANAGED.md](CLOUDFLARED_REMOTELY_MANAGED.md)**

Os conceitos de segurança de webhooks neste documento continuam válidos independentemente do método de túnel usado.

---

## Contexto

**Situação Atual:**
- Jenkins rodando localmente em `http://192.168.1.74:8080`
- Webhooks do GitHub configurados para disparar builds
- Pipeline automatizado em `cloud/jenkins/Jenkinsfile`
- Trigger: push para branch main

**Problema:**
- Webhooks do GitHub requerem endpoint público acessível
- Expor Jenkins diretamente na internet é um risco de segurança significativo

**Requisito:**
- Implementar solução segura que permita webhooks sem expor Jenkins publicamente

---

## Solução Recomendada: Cloudflare Tunnel (Zero Trust)

### Por que Cloudflare Tunnel?

**Vantagens:**
- ✅ **Gratuito** para uso pessoal/pequenos projetos
- ✅ **Zero port forwarding** - não abre portas no firewall
- ✅ **Túnel criptografado** - comunicação segura entre Cloudflare e Jenkins
- ✅ **DDoS protection** incluída
- ✅ **Access policies** - controle granular de acesso
- ✅ **Logs e analytics** via dashboard Cloudflare
- ✅ **Fácil de configurar** - daemon leve (cloudflared)
- ✅ **Alta disponibilidade** - múltiplos pontos de presença da Cloudflare

**Alternativas consideradas mas não recomendadas:**
- ngrok: Requer plano pago para domínios customizados e túneis persistentes
- GitHub Actions como intermediário: Adiciona latência e complexidade
- SSH Reverse Tunnel: Requer servidor intermediário e manutenção manual
- Expor Jenkins com proxy reverso: Ainda expõe serviço publicamente

---

## Arquitetura da Solução

```
GitHub Webhook
    ↓
Cloudflare Edge (DDoS Protection)
    ↓
Cloudflare Tunnel (Encrypted)
    ↓
cloudflared daemon (local)
    ↓
Jenkins (192.168.1.74:8080) - Permanece privado
```

---

## Plano de Implementação

### Fase 1: Configuração do Cloudflare Tunnel

#### 1.1 Instalar cloudflared
```bash
# macOS (via Homebrew)
brew install cloudflare/cloudflare/cloudflared

# Verificar instalação
cloudflared --version
```

#### 1.2 Autenticar com Cloudflare
```bash
cloudflared tunnel login
```
- Abrirá navegador para autorizar
- Selecionar domínio (se tiver) ou criar conta gratuita

#### 1.3 Criar túnel
```bash
cloudflared tunnel create jenkins-base-data-etl
```
- Anotar o **Tunnel ID** que será gerado
- Credenciais salvas em `~/.cloudflared/<TUNNEL-ID>.json`

#### 1.4 Configurar rota do túnel

**Método Antigo (Locally-Managed):**
```bash
cloudflared tunnel route dns jenkins-base-data-etl jenkins.seudominio.com
```
- Substituir `seudominio.com` pelo seu domínio
- Se não tiver domínio, Cloudflare pode fornecer subdomínio `.trycloudflare.com`

**Método Novo (Remotely-Managed) - RECOMENDADO:**
- ✅ DNS configurado **automaticamente** ao adicionar rota pública no Dashboard
- ✅ Sem necessidade de comandos CLI
- Ver [CLOUDFLARED_REMOTELY_MANAGED.md](CLOUDFLARED_REMOTELY_MANAGED.md) para instruções completas

#### 1.5 Criar arquivo de configuração
**Arquivo:** `~/.cloudflared/config.yml`
```yaml
tunnel: <TUNNEL-ID>
credentials-file: /Users/vitormineiro/.cloudflared/<TUNNEL-ID>.json

ingress:
  - hostname: jenkins.seudominio.com
    service: http://192.168.1.74:8080
    originRequest:
      noTLSVerify: true
  - service: http_status:404
```

#### 1.6 Testar túnel manualmente
```bash
cloudflared tunnel run jenkins-base-data-etl
```
- Acessar `https://jenkins.seudominio.com` para verificar
- Se funcionar, parar com Ctrl+C

#### 1.7 Instalar como serviço (daemon)
```bash
sudo cloudflared service install
sudo launchctl start com.cloudflare.cloudflared
```

---

### Fase 2: Adicionar Segurança com Webhook Secrets

#### 2.1 Gerar webhook secret
```bash
openssl rand -hex 32
```
- Anotar o token gerado

#### 2.2 Configurar secret no GitHub
1. Ir para: `https://github.com/<SEU-REPO>/settings/hooks`
2. Editar webhook existente (ou criar novo)
3. **Payload URL:** `https://jenkins.seudominio.com/github-webhook/`
4. **Content type:** `application/json`
5. **Secret:** Colar o token gerado em 2.1
6. **SSL verification:** Enabled
7. **Events:** "Just the push event"
8. **Active:** Checked
9. Salvar

#### 2.3 Configurar secret no Jenkins
1. Acessar Jenkins: `https://jenkins.seudominio.com`
2. Ir para: **Manage Jenkins → Manage Credentials**
3. Clicar em **(global)** domain
4. **Add Credentials:**
   - Kind: Secret text
   - Scope: Global
   - Secret: Colar o mesmo token do GitHub
   - ID: `github-webhook-secret`
   - Description: "GitHub Webhook Secret for base-data-etl"
5. Salvar

#### 2.4 Atualizar configuração do job no Jenkins
1. Ir para job: `base-data-etl-staging`
2. **Configure → Build Triggers**
3. Marcar: ☑ **GitHub hook trigger for GITScm polling**
4. Em **Build Environment**, adicionar validação de webhook (se plugin disponível)
5. Salvar

---

### Fase 3: Adicionar Cloudflare Access (Opcional mas Recomendado)

#### 3.1 Configurar Cloudflare Access Policy
No dashboard do Cloudflare:
1. **Zero Trust → Access → Applications**
2. **Add an application → Self-hosted**
3. Configurar:
   - **Application name:** Jenkins Base Data ETL
   - **Subdomain:** jenkins
   - **Domain:** seudominio.com
   - **Accept all available identity providers** (ou específico)
4. **Add policy:**
   - **Policy name:** GitHub Webhooks Allow
   - **Action:** Allow
   - **Include:** Everyone (temporário, refinar depois)
5. Salvar

#### 3.2 Criar bypass rule para webhooks
1. **Add policy:**
   - **Policy name:** GitHub Webhook Bypass
   - **Action:** Bypass
   - **Include → Selector:** IP ranges
   - **Value:** Adicionar IPs do GitHub (ver Meta API)
2. Salvar

**IPs do GitHub (atualizar via API):**
```bash
curl https://api.github.com/meta | jq '.hooks'
```

---

### Fase 4: Documentação e Testes

#### 4.1 Testes
1. **Teste manual do túnel:**
   ```bash
   curl -I https://jenkins.seudominio.com
   ```

2. **Teste de webhook do GitHub:**
   - Fazer push para branch main
   - Verificar se build dispara automaticamente
   - Verificar logs do Jenkins
   - Verificar logs do cloudflared

3. **Teste de segurança:**
   - Tentar acessar `http://192.168.1.74:8080` diretamente (deve funcionar localmente)
   - Verificar que Jenkins NÃO está acessível via IP público
   - Verificar logs de webhook no GitHub (deve mostrar 200 OK)

#### 4.2 Monitoramento
- **Logs do cloudflared:**
  ```bash
  sudo launchctl list | grep cloudflare
  tail -f /var/log/cloudflared.log
  ```

- **Dashboard Cloudflare:** Analytics do túnel

- **Jenkins:** Webhook delivery history no GitHub

---

## Segurança Adicional (Bônus)

### 1. Rate Limiting no Cloudflare
- Configurar rate limiting para endpoint `/github-webhook/`
- Limite: 10 requests/minuto por IP

### 2. Webhook Signature Validation
- Validar assinatura HMAC-SHA256 do GitHub
- Implementar no Jenkins via plugin ou script Groovy

### 3. IP Allowlist
- Restringir acesso apenas aos IPs do GitHub
- Atualizar via API Meta do GitHub periodicamente

### 4. Audit Logging
- Habilitar audit log no Jenkins
- Logs de acesso via Cloudflare
- Alertas para builds falhados/suspeitos

### 5. Backup das Configurações
```bash
# Backup do túnel Cloudflare
cp ~/.cloudflared/config.yml ~/backups/cloudflared-config-$(date +%Y%m%d).yml
cp ~/.cloudflared/<TUNNEL-ID>.json ~/backups/
```

---

## Estimativa de Tempo

- Fase 1 (Cloudflare Tunnel): 30-45 minutos
- Fase 2 (Webhook Secrets): 15 minutos
- Fase 3 (Access Policies): 20 minutos (opcional)
- Fase 4 (Testes): 15 minutos
- **Total: ~1.5-2 horas**

---

## Rollback Plan

Se algo der errado:

1. **Parar cloudflared:**
   ```bash
   sudo launchctl stop com.cloudflare.cloudflared
   ```

2. **Reverter webhook do GitHub:**
   - Mudar URL para endpoint antigo (se existir)
   - Ou desabilitar temporariamente

3. **Usar polling alternativo:**
   - No Jenkins job, habilitar "Poll SCM"
   - Schedule: `H/5 * * * *` (a cada 5 minutos)

---

## Próximos Passos (Após Implementação)

1. Monitorar por 1 semana
2. Configurar alertas (Cloudflare + Jenkins)
3. Documentar procedimentos operacionais
4. Considerar migrar outros serviços para túneis similares
5. Implementar rotação automática de secrets (trimestral)

---

## Troubleshooting

### Problema: Túnel não conecta
```bash
# Verificar status do serviço
sudo launchctl list | grep cloudflare

# Ver logs em tempo real
tail -f /var/log/cloudflared.log

# Testar manualmente
cloudflared tunnel run jenkins-base-data-etl
```

### Problema: Webhook não dispara build
1. Verificar delivery no GitHub (Settings → Webhooks → Recent Deliveries)
2. Verificar logs do Jenkins
3. Confirmar que job está configurado para "GitHub hook trigger"
4. Testar webhook manualmente via GitHub UI

### Problema: Erro 502 Bad Gateway
- Verificar se Jenkins está rodando: `curl http://192.168.1.74:8080`
- Verificar configuração do túnel em `~/.cloudflared/config.yml`
- Reiniciar cloudflared: `sudo launchctl restart com.cloudflare.cloudflared`

---

## Outros Casos de Uso do Cloudflare Tunnel

Depois de configurar o túnel para o Jenkins, pode reutilizá-lo para outros serviços:

**[Ver guia completo: CLOUDFLARE_TUNNEL_DEMOS.md](../CLOUDFLARE_TUNNEL_DEMOS.md)**

- Expor websites de demo para clientes
- Apresentações remotas de aplicações locais
- Múltiplos serviços (frontend, backend, admin panels)
- Proteger demos com autenticação

---

## Referências

- [Cloudflare Tunnel Documentation](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/)
- [GitHub Webhooks Documentation](https://docs.github.com/en/webhooks)
- [GitHub Meta API (IPs)](https://api.github.com/meta)
- [Jenkins Webhook Security](https://www.jenkins.io/doc/book/security/webhook/)
- [Cloudflare Zero Trust](https://developers.cloudflare.com/cloudflare-one/)
- [Cloudflare Tunnel para Demos](../CLOUDFLARE_TUNNEL_DEMOS.md) - Guia interno
