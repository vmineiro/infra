# Cloudflare Tunnel para Websites de Demo

Guia para expor websites e serviços da rede local de forma segura usando Cloudflare Tunnel - ideal para demos, apresentações, e testes com clientes.

## ⚠️ MÉTODO ATUALIZADO - Gestão via Dashboard

**Este documento foi atualizado para o método remotely-managed (gestão via Dashboard Web).**

Para instruções completas do novo método, consulte: **[CLOUDFLARED_REMOTELY_MANAGED.md](CLOUDFLARED_REMOTELY_MANAGED.md)**

As secções abaixo foram adaptadas mas alguns comandos antigos (como `cloudflared tunnel route dns`) já não são necessários com o novo método.

---

## Casos de Uso

- **Demos de produtos** - Mostrar protótipos a clientes sem deploy em servidor
- **Apresentações** - Acesso remoto a aplicações locais durante apresentações
- **Testes com stakeholders** - Permitir acesso temporário a ambientes de desenvolvimento
- **Ambientes de staging** - Expor staging interno para review externo
- **Múltiplos serviços** - Frontend, backend, admin panels, etc.

---

## Pré-requisitos

1. **Cloudflare Tunnel instalado e configurado**
   - Ver instruções completas em: [`jenkins/WEBHOOK_SECURITY.md`](./jenkins/WEBHOOK_SECURITY.md)
   - Fase 1: Instalação e configuração básica

2. **Website/serviço rodando localmente**
   - Exemplo: `http://localhost:3000` ou `http://192.168.1.100:8080`

3. **Domínio configurado no Cloudflare**
   - Pode usar domínio próprio ou subdomínio `.trycloudflare.com` (gratuito)

---

## Configuração Básica - Um Website

### Cenário: Website rodando em `http://192.168.1.100:3000`

### Método Novo (Recomendado): Via Dashboard

#### 1. Adicionar rota no Dashboard
1. **Cloudflare Dashboard** → **Access** → **Tunnels**
2. Clicar no seu túnel
3. Tab **Public Hostname** → **Add a public hostname**
4. Configurar:
   - **Subdomain:** `demo`
   - **Domain:** `seudominio.com`
   - **Type:** HTTP
   - **URL:** `192.168.1.100:3000`
5. **Additional application settings** → **TLS** → **No TLS Verify** ✓
6. **Save hostname**

✅ **DNS criado automaticamente** - não precisa de comandos `cloudflared tunnel route dns`

#### 2. Testar

```bash
# Verificar conectividade
curl -I https://demo.seudominio.com

# Abrir no navegador
open https://demo.seudominio.com
```

**Pronto!** O website está acessível via `https://demo.seudominio.com` com HTTPS automático.

---

### Método Antigo (Locally-Managed) - DEPRECATED

<details>
<summary>Clique para ver método antigo via config.yml</summary>

**Editar:** `~/.cloudflared/config.yml`

```yaml
tunnel: <SEU-TUNNEL-ID>
credentials-file: /Users/vitormineiro/.cloudflared/<SEU-TUNNEL-ID>.json

ingress:
  # Website de demo
  - hostname: demo.seudominio.com
    service: http://192.168.1.100:3000
    originRequest:
      noTLSVerify: true

  # Captura tudo o resto (obrigatório)
  - service: http_status:404
```

**Configurar DNS:**
```bash
cloudflared tunnel route dns <TUNNEL-NAME> demo.seudominio.com
```

**Reiniciar:**
```bash
sudo launchctl restart com.cloudflare.cloudflared
```
</details>

---

## Configuração Avançada - Múltiplos Websites

### Cenário: Frontend, Backend API, e Admin Panel

### Método Novo (Recomendado): Via Dashboard

Adicionar cada hostname individualmente no Dashboard:

1. **Frontend React/Vue**
   - Subdomain: `demo` | Domain: `seudominio.com`
   - Type: HTTP | URL: `localhost:3000`
   - TLS: No TLS Verify ✓

2. **Backend API**
   - Subdomain: `api` | Domain: `seudominio.com`
   - Type: HTTP | URL: `localhost:8000`
   - TLS: No TLS Verify ✓

3. **Admin Panel** (outro servidor)
   - Subdomain: `admin` | Domain: `seudominio.com`
   - Type: HTTP | URL: `192.168.1.150:5000`
   - TLS: No TLS Verify ✓

4. **Jenkins** (já existente)
   - Subdomain: `jenkins` | Domain: `seudominio.com`
   - Type: HTTP | URL: `192.168.1.74:8080`
   - TLS: No TLS Verify ✓

✅ **Vantagens:**
- DNS criado automaticamente para cada hostname
- Sem necessidade de reiniciar o serviço cloudflared
- Mudanças instantâneas
- Gestão visual de todas as rotas

### Testar cada endpoint

```bash
curl -I https://demo.seudominio.com
curl -I https://api.seudominio.com
curl -I https://admin.seudominio.com
curl -I https://jenkins.seudominio.com
```

---

### Método Antigo (Locally-Managed) - DEPRECATED

<details>
<summary>Clique para ver método antigo via config.yml</summary>

```yaml
tunnel: <SEU-TUNNEL-ID>
credentials-file: /Users/vitormineiro/.cloudflared/<SEU-TUNNEL-ID>.json

ingress:
  - hostname: demo.seudominio.com
    service: http://localhost:3000
    originRequest:
      noTLSVerify: true
  - hostname: api.seudominio.com
    service: http://localhost:8000
    originRequest:
      noTLSVerify: true
  - hostname: admin.seudominio.com
    service: http://192.168.1.150:5000
    originRequest:
      noTLSVerify: true
  - service: http_status:404
```

**DNS + Restart:**
```bash
cloudflared tunnel route dns <TUNNEL-NAME> demo.seudominio.com
cloudflared tunnel route dns <TUNNEL-NAME> api.seudominio.com
cloudflared tunnel route dns <TUNNEL-NAME> admin.seudominio.com
sudo launchctl restart com.cloudflare.cloudflared
```
</details>

---

## Proteger Demos com Autenticação

Para demos privadas ou apresentações restritas, adicione Cloudflare Access.

### Opção 1: PIN via Email (Mais Simples)

**No Dashboard Cloudflare:**

1. **Zero Trust → Access → Applications**
2. **Add an application → Self-hosted**
3. Configurar:
   - **Application name:** Demo Website
   - **Subdomain:** demo
   - **Domain:** seudominio.com
4. **Add policy:**
   - **Policy name:** Email PIN Access
   - **Action:** Allow
   - **Include:** Emails ending in → `@suaempresa.com` (ou emails específicos)
5. **Authentication method:** One-time PIN
6. Salvar

**Resultado:** Visitantes precisam inserir email e recebem PIN para acesso.

### Opção 2: Login Social (Google/GitHub)

Mesma configuração acima, mas em **Authentication method**:
- Selecionar **Google** ou **GitHub**
- Autorizar aplicação OAuth

**Resultado:** Login com conta Google/GitHub para aceder.

### Opção 3: Password Simples (Acesso Temporário)

Para demos rápidas com password partilhada:

1. Criar service token:
   ```bash
   # No dashboard: Access → Service Auth → Service Tokens
   # Gerar novo token e copiar Client ID + Secret
   ```

2. Partilhar credenciais com participantes da demo

---

## Configurações Úteis para Demos

### Ativar WebSocket (para apps real-time)

```yaml
- hostname: demo.seudominio.com
  service: http://localhost:3000
  originRequest:
    noTLSVerify: true
    httpHostHeader: localhost:3000
```

### Desativar Cache (para desenvolvimento ativo)

No Dashboard Cloudflare:
- **Caching → Configuration**
- **Caching Level:** No Query String
- **Browser Cache TTL:** Respect Existing Headers

Ou adicionar regra por hostname:
- **Rules → Page Rules**
- URL: `demo.seudominio.com/*`
- Settings: **Cache Level = Bypass**

### Timeout customizado (APIs lentas)

```yaml
- hostname: api.seudominio.com
  service: http://localhost:8000
  originRequest:
    noTLSVerify: true
    connectTimeout: 30s
    keepAliveTimeout: 90s
```

---

## Casos de Uso Específicos

### 1. Demo de App React/Vue com Hot Reload

```yaml
ingress:
  - hostname: demo-dev.seudominio.com
    service: http://localhost:3000
    originRequest:
      noTLSVerify: true
      httpHostHeader: localhost:3000  # Importante para Vite/webpack
```

**Nota:** Hot reload funciona automaticamente via WebSocket.

### 2. Jupyter Notebook / Streamlit

```yaml
ingress:
  - hostname: notebook.seudominio.com
    service: http://localhost:8888
    originRequest:
      noTLSVerify: true
      disableChunkedEncoding: true  # Importante para streaming
```

### 3. Docker Compose Multi-Service

```yaml
ingress:
  # Frontend
  - hostname: app.seudominio.com
    service: http://localhost:3000

  # Backend
  - hostname: api.seudominio.com
    service: http://localhost:8000

  # Database Admin (pgAdmin/Adminer)
  - hostname: db-admin.seudominio.com
    service: http://localhost:8080

  - service: http_status:404
```

---

## Workflow para Demos Temporárias

### Antes da Demo

```bash
# 1. Verificar que serviço está rodando
curl http://localhost:3000

# 2. Verificar túnel ativo
cloudflared tunnel list

# 3. Testar acesso externo
curl -I https://demo.seudominio.com

# 4. Verificar logs (se necessário)
tail -f /var/log/cloudflared.log
```

### Durante a Demo

- Partilhar URL: `https://demo.seudominio.com`
- Monitorar analytics no dashboard Cloudflare
- Logs em tempo real se necessário

### Depois da Demo

### Método Novo (Via Dashboard):

**Opção 1: Desativar temporariamente**
1. Dashboard → Access → Tunnels → (seu túnel)
2. Tab **Public Hostname**
3. Encontrar `demo.seudominio.com`
4. Toggle **Enabled** para OFF (sem apagar)

**Opção 2: Remover completamente**
1. Dashboard → Access → Tunnels → (seu túnel)
2. Tab **Public Hostname**
3. Encontrar `demo.seudominio.com` → Clicar nos três pontos → **Delete**
4. Confirmar

✅ **Vantagem:** Mudanças instantâneas, sem restart do cloudflared

---

### Método Antigo (Locally-Managed):

<details>
<summary>Clique para ver método antigo</summary>

**Desativar:**
```bash
# Comentar no config.yml
# - hostname: demo.seudominio.com
#   service: http://localhost:3000
sudo launchctl restart com.cloudflare.cloudflared
```

**Remover:**
```bash
cloudflared tunnel route dns delete demo.seudominio.com
# Remover do config.yml e reiniciar
sudo launchctl restart com.cloudflare.cloudflared
```
</details>

---

## Troubleshooting

### Problema: 502 Bad Gateway

**Causa:** Serviço local não está rodando ou IP/porta incorretos

**Solução:**
```bash
# Verificar se serviço responde localmente
curl http://localhost:3000

# Verificar se IP está correto (se usando IP da rede)
ping 192.168.1.100

# Ver logs do cloudflared
tail -f /var/log/cloudflared.log
```

### Problema: Website carrega mas assets (CSS/JS) não

**Causa:** CORS ou paths relativos incorretos

**Solução:**
```yaml
originRequest:
  httpHostHeader: localhost:3000  # Passa hostname original
  originServerName: localhost     # Para apps que verificam hostname
```

### Problema: Autenticação não funciona

**Causa:** Access policy não configurada corretamente

**Solução:**
1. Dashboard → Zero Trust → Logs → Access
2. Ver qual rule foi aplicada
3. Ajustar policy para incluir usuários corretos

### Problema: Timeout em requests longos

**Causa:** Timeout padrão muito baixo

**Solução:**
```yaml
originRequest:
  connectTimeout: 60s
  keepAliveTimeout: 120s
```

---

## Exemplos de Configuração Completa

### Exemplo 1: Projeto Full-Stack Local

```yaml
tunnel: abc123def456
credentials-file: /Users/vitormineiro/.cloudflared/abc123def456.json

ingress:
  # Frontend Next.js (localhost:3000)
  - hostname: app.exemplo.com
    service: http://localhost:3000
    originRequest:
      noTLSVerify: true
      httpHostHeader: localhost:3000

  # Backend FastAPI (localhost:8000)
  - hostname: api.exemplo.com
    service: http://localhost:8000
    originRequest:
      noTLSVerify: true
      connectTimeout: 30s

  # PostgreSQL via pgAdmin (localhost:5050)
  - hostname: db.exemplo.com
    service: http://localhost:5050
    originRequest:
      noTLSVerify: true

  - service: http_status:404
```

### Exemplo 2: Multi-tenant Demos

```yaml
ingress:
  # Cliente A
  - hostname: demo-cliente-a.exemplo.com
    service: http://192.168.1.100:3000

  # Cliente B
  - hostname: demo-cliente-b.exemplo.com
    service: http://192.168.1.101:3000

  # Cliente C
  - hostname: demo-cliente-c.exemplo.com
    service: http://192.168.1.102:3000

  - service: http_status:404
```

---

## Boas Práticas

### Segurança

✅ **Sempre use Access policies** para demos sensíveis
✅ **Remova túneis temporários** após demos
✅ **Não exponha databases** diretamente (apenas admin UIs)
✅ **Use subdomínios descritivos** (demo-*, staging-*)
✅ **Monitore logs** durante demos críticas

### Performance

✅ **Desative cache** durante desenvolvimento ativo
✅ **Use IP local** se serviço está na mesma máquina (localhost)
✅ **Configure timeouts** adequados para sua aplicação
✅ **Teste latência** antes de demos importantes

### Organização

✅ **Documente cada hostname** e seu propósito
✅ **Use naming convention** consistente (demo-*, app-*, api-*)
✅ **Agrupe serviços relacionados** no config.yml
✅ **Comente rotas temporárias** em vez de deletar

---

## Comandos Úteis Rápidos

### Comandos que funcionam (Remotely-Managed):

```bash
# Ver status do túnel
cloudflared tunnel list

# Ver logs em tempo real
tail -f /var/log/cloudflared.log

# Reiniciar serviço (se necessário - raramente usado)
sudo launchctl restart com.cloudflare.cloudflared

# Parar serviço
sudo launchctl stop com.cloudflare.cloudflared

# Verificar se túnel está ativo
ps aux | grep cloudflared

# Atualizar cloudflared
cloudflared update
```

### ⚠️ Comandos que NÃO funcionam (Remotely-Managed):

```bash
# ❌ NÃO USAR - só funciona com locally-managed
cloudflared tunnel route dns list
cloudflared tunnel route dns <TUNNEL-NAME> <hostname>
cloudflared tunnel route dns delete <hostname>
```

**Porquê?** Com remotely-managed tunnels, o DNS é gerido automaticamente pelo Dashboard. Use sempre o Dashboard Web para gerir rotas.

---

## Recursos Adicionais

### Documentação Interna
- **[CLOUDFLARED_REMOTELY_MANAGED.md](CLOUDFLARED_REMOTELY_MANAGED.md)** - Guia completo do método remotely-managed (RECOMENDADO)
- [WEBHOOK_SECURITY.md](WEBHOOK_SECURITY.md) - Segurança para GitHub webhooks
- [CLOUDFLARED_SETUP.md](CLOUDFLARED_SETUP.md) - Método antigo locally-managed (DEPRECATED)

### Documentação Oficial Cloudflare
- [Cloudflare Tunnel Documentation](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/)
- [Remotely-Managed Tunnels](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/tunnel-guide/remote/)
- [Cloudflare Access Documentation](https://developers.cloudflare.com/cloudflare-one/applications/configure-apps/)
- [Troubleshooting Guide](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/troubleshooting/)

---

## Alternativas ao Cloudflare Tunnel

Se Cloudflare não funcionar para seu caso de uso:

| Solução | Vantagens | Desvantagens |
|---------|-----------|--------------|
| **ngrok** | Setup rápido, UI web | Plano gratuito limitado, domínios aleatórios |
| **localtunnel** | Open source, gratuito | Menos estável, sem Access control |
| **Tailscale** | VPN mesh, muito seguro | Requer instalação no cliente |
| **frp (Fast Reverse Proxy)** | Auto-hospedado, flexível | Requer servidor intermediário |

**Recomendação:** Cloudflare Tunnel é a melhor opção para maioria dos casos - gratuito, estável, e com funcionalidades profissionais.
