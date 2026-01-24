# Cloudflare Tunnel - Gestão via Dashboard Web (Remotely-Managed)

## Índice
- [Visão Geral](#visão-geral)
- [Vantagens](#vantagens)
- [Criar Novo Túnel](#criar-novo-túnel)
- [Instalar Conector no Servidor](#instalar-conector-no-servidor)
- [Adicionar Novas Rotas](#adicionar-novas-rotas)
- [Monitorização e Logs](#monitorização-e-logs)
- [Troubleshooting](#troubleshooting)
- [Comandos a NÃO Usar](#comandos-a-não-usar)

---

## Visão Geral

**Remotely-Managed Tunnels** são túneis do Cloudflare geridos completamente via **Dashboard Web**, sem necessidade de ficheiros de configuração locais (`config.yml`) no servidor.

### Diferenças vs Método Antigo (Locally-Managed)

| Aspecto | Locally-Managed (Antigo) | Remotely-Managed (Novo) |
|---------|-------------------------|------------------------|
| **Configuração** | Ficheiro `config.yml` no servidor | Dashboard Web apenas |
| **DNS** | Comandos CLI `cloudflared tunnel route dns` | Automático via Dashboard |
| **Credentials** | Ficheiro JSON (`credentials-file`) | Token único |
| **Adicionar Rotas** | Editar ficheiro + restart serviço | Dashboard, sem restart |
| **Gestão** | Descentralizada (cada servidor) | Centralizada (Dashboard) |

---

## Vantagens

✅ **Zero Configuração Local** - Sem ficheiros `config.yml` ou credentials
✅ **DNS Automático** - Registos DNS criados automaticamente ao adicionar rotas
✅ **Gestão Centralizada** - Todas as rotas visíveis/editáveis no Dashboard
✅ **Alterações Instantâneas** - Adicionar/remover rotas sem restart
✅ **Mais Simples** - Ideal para quem tem poucas rotas a gerir

---

## Criar Novo Túnel

### Passo 1: Aceder ao Dashboard
1. Login em: https://one.dash.cloudflare.com/
2. Ir para **Access** → **Tunnels**
3. Clicar em **Create a tunnel**

### Passo 2: Configurar Túnel
1. Escolher **Cloudflared**
2. **Nome do túnel:** (ex: `cicd-managed`, `prod-tunnel`)
3. Clicar em **Save tunnel**

### Passo 3: Copiar Token
- Dashboard mostra um **token** do tipo: `eyJhIjoiXXXXXXXX...`
- **IMPORTANTE:** Copiar este token - será usado para instalar o conector no servidor
- Pode sempre voltar ao Dashboard para obter novo token

### Passo 4: Configurar Primeira Rota Pública
Antes de instalar o conector, configure pelo menos uma rota:

1. Na secção **Public Hostname**, clicar em **Add a public hostname**
2. Configurar:
   - **Subdomain:** `cicd` (ou outro)
   - **Domain:** `vitormineiro.com` (selecionar da lista)
   - **Path:** (deixar vazio, a menos que queira routing por path)
   - **Type:** HTTP (ou HTTPS se o serviço tiver TLS)
   - **URL:** `192.168.1.74:8080` (IP:porta do serviço interno)

3. **Opções Avançadas** (se necessário):
   - Expandir **TLS**
   - Ativar **No TLS Verify** ✓ (se o serviço backend não tiver certificado válido)

4. Clicar em **Save hostname**

**Resultado:**
- Hostname `cicd.vitormineiro.com` configurado
- **DNS criado automaticamente** (registo CNAME)
- Túnel ainda aparece **Disconnected** (normal - vamos instalar conector)

✅ **IMPORTANTE:** O DNS foi configurado automaticamente. **NÃO** precisa de executar comandos como `cloudflared tunnel route dns`.

---

## Instalar Conector no Servidor

Agora precisa instalar o **conector** (daemon) no servidor que vai expor os serviços.

### macOS

```bash
# Instalar serviço usando o token do Dashboard
sudo cloudflared service install eyJhIjoiXXXXXXXX...
# Substituir pelo token real copiado do Dashboard

# Iniciar serviço
sudo launchctl start com.cloudflare.cloudflared

# Verificar status
sudo launchctl list | grep cloudflare

# Ver logs em tempo real
tail -f /var/log/cloudflared.log
```

### Linux

```bash
# Instalar serviço usando o token do Dashboard
sudo cloudflared service install eyJhIjoiXXXXXXXX...
# Substituir pelo token real copiado do Dashboard

# Iniciar e ativar serviço
sudo systemctl start cloudflared
sudo systemctl enable cloudflared

# Verificar status
sudo systemctl status cloudflared

# Ver logs em tempo real
sudo journalctl -u cloudflared -f
```

### Verificar Conectividade

**Nos logs, procurar por:**
```
INF Connection <UUID> registered
INF Registered tunnel connection
```

**No Dashboard:**
- Ir para **Access** → **Tunnels**
- O túnel deve mostrar status **HEALTHY** (verde)

**Testar acesso externo:**
```bash
curl -I https://cicd.vitormineiro.com
# Deve retornar HTTP/2 200 ou 403 (dependendo do serviço)
```

---

## Adicionar Novas Rotas

Para expor novos serviços (ex: `demo.vitormineiro.com`, `api.vitormineiro.com`):

### Via Dashboard (Recomendado)

1. Cloudflare Dashboard → **Access** → **Tunnels**
2. Clicar no túnel desejado
3. Tab **Public Hostname** → **Add a public hostname**
4. Configurar:
   - **Subdomain:** `demo`
   - **Domain:** `vitormineiro.com`
   - **Type:** HTTP
   - **URL:** `localhost:3000` (ou IP:porta do serviço)
5. Guardar

**Vantagens:**
- ✅ Sem necessidade de editar ficheiros no servidor
- ✅ Sem necessidade de reiniciar o serviço cloudflared
- ✅ DNS criado automaticamente
- ✅ Mudanças aplicadas instantaneamente
- ✅ Histórico de alterações visível no Dashboard

### Exemplos de Rotas Comuns

#### Website de Demo
```
Subdomain: demo
Domain: vitormineiro.com
Type: HTTP
URL: localhost:3000
```

#### API Backend
```
Subdomain: api
Domain: vitormineiro.com
Type: HTTP
URL: localhost:8000
```

#### Portainer (Container Management)
```
Subdomain: portainer
Domain: vitormineiro.com
Type: HTTPS
URL: https://localhost:9443
TLS Options: No TLS Verify ✓
```

#### Multiple Services na Mesma Porta (Path-based routing)
```
# Main app
Subdomain: app
Domain: vitormineiro.com
Path: /
Type: HTTP
URL: localhost:8080

# Admin panel
Subdomain: app
Domain: vitormineiro.com
Path: /admin
Type: HTTP
URL: localhost:8081
```

---

## Monitorização e Logs

### Ver Status do Túnel

**Dashboard:**
- **Access** → **Tunnels** → Status (HEALTHY/UNHEALTHY)
- Métricas de tráfego e latência
- Histórico de eventos

**CLI (no servidor):**
```bash
# Listar todos os túneis
cloudflared tunnel list

# Ver informação de conectividade
cloudflared tunnel info <tunnel-name>
```

### Ver Logs

**macOS:**
```bash
# Logs em tempo real
tail -f /var/log/cloudflared.log

# Últimas 100 linhas
tail -100 /var/log/cloudflared.log

# Procurar por erros
grep ERROR /var/log/cloudflared.log
```

**Linux:**
```bash
# Logs em tempo real
sudo journalctl -u cloudflared -f

# Últimas 100 linhas
sudo journalctl -u cloudflared -n 100

# Procurar por erros
sudo journalctl -u cloudflared | grep ERROR
```

### Reiniciar Serviço (se necessário)

**macOS:**
```bash
sudo launchctl stop com.cloudflare.cloudflared
sudo launchctl start com.cloudflare.cloudflared
```

**Linux:**
```bash
sudo systemctl restart cloudflared
```

⚠️ **NOTA:** Normalmente **não é necessário** reiniciar ao adicionar rotas via Dashboard.

---

## Troubleshooting

### Túnel mostra "Disconnected" no Dashboard

**Causas possíveis:**
- Conector não está instalado no servidor
- Serviço cloudflared não está a correr
- Token inválido ou expirado
- Problemas de rede/firewall

**Soluções:**
```bash
# Verificar se serviço está a correr
# macOS:
sudo launchctl list | grep cloudflare

# Linux:
sudo systemctl status cloudflared

# Ver logs para identificar erro
tail -f /var/log/cloudflared.log  # macOS
sudo journalctl -u cloudflared -f  # Linux

# Se token expirado, obter novo no Dashboard e reinstalar:
sudo cloudflared service uninstall
sudo cloudflared service install <NOVO_TOKEN>
sudo launchctl start com.cloudflare.cloudflared  # macOS
sudo systemctl start cloudflared  # Linux
```

### 502 Bad Gateway ao aceder ao hostname

**Causas possíveis:**
- Serviço backend (Jenkins, Portainer, etc.) não está a correr
- IP/porta incorretos na configuração da rota
- Firewall bloqueando conexão entre cloudflared e serviço

**Soluções:**
```bash
# Verificar se serviço backend está acessível localmente
curl http://192.168.1.74:8080  # Substituir pelo IP:porta real

# Verificar configuração da rota no Dashboard:
# - IP correto? (192.168.1.74 vs localhost vs 127.0.0.1)
# - Porta correta? (8080 vs 8081 vs 443)
# - Tipo correto? (HTTP vs HTTPS)

# Ver logs do cloudflared para detalhes
tail -f /var/log/cloudflared.log
```

### DNS não resolve (nslookup falha)

**Causas possíveis:**
- Rota pública não configurada corretamente no Dashboard
- Domínio não está no Cloudflare
- Propagação DNS ainda em curso (raro)

**Soluções:**
```bash
# Verificar registo DNS no Dashboard
# Cloudflare Dashboard → DNS → Records
# Deve existir CNAME: cicd.vitormineiro.com → <tunnel-id>.cfargotunnel.com

# Testar resolução DNS
nslookup cicd.vitormineiro.com
dig cicd.vitormineiro.com +short

# Forçar uso de DNS do Cloudflare
nslookup cicd.vitormineiro.com 1.1.1.1
```

### "Connection refused" ou "No connection could be made"

**Causas possíveis:**
- IP incorreto (localhost vs 192.168.1.x)
- Serviço não está a escutar no IP/porta especificados
- Firewall local bloqueando cloudflared

**Soluções:**
```bash
# Se usar "localhost" na rota, certifique-se que cloudflared
# está no mesmo servidor que o serviço backend

# Se usar IP (192.168.1.74), verifique conectividade de rede:
ping 192.168.1.74
telnet 192.168.1.74 8080

# Verificar se serviço está a escutar:
# macOS:
sudo lsof -i :8080

# Linux:
sudo netstat -tulpn | grep 8080
sudo ss -tulpn | grep 8080
```

### Erro ao instalar: "cloudflared service is already installed"

**Causa:**
- Já existe um serviço cloudflared instalado (túnel antigo locally-managed)

**Solução:**
```bash
# Desinstalar serviço existente
sudo cloudflared service uninstall

# Confirmar remoção
sudo launchctl list | grep cloudflare  # macOS (não deve retornar nada)
sudo systemctl status cloudflared  # Linux (deve mostrar "not found")

# Reinstalar com novo token
sudo cloudflared service install <TOKEN>
```

---

## Comandos a NÃO Usar

### ⚠️ IMPORTANTE: Comandos CLI de DNS não funcionam

Com túneis **remotely-managed**, estes comandos **NÃO devem ser usados**:

```bash
# ❌ NÃO USAR - Só funciona com locally-managed tunnels
cloudflared tunnel route dns <tunnel> <hostname>
cloudflared tunnel route dns list
cloudflared tunnel route dns delete <hostname>
```

**Porquê?**
- O DNS é gerido **automaticamente** pelo Dashboard
- Ao adicionar uma rota pública no Dashboard, o registo DNS é criado automaticamente
- Usar estes comandos pode causar conflitos ou não ter efeito

**Use sempre o Dashboard** para gerir rotas e DNS.

### Outros comandos que não são necessários

```bash
# ❌ NÃO é necessário criar ficheiro config.yml
# (o token já contém toda a configuração)

# ❌ NÃO é necessário ficheiro credentials-file
# (o token substitui isso)

# ❌ NÃO é necessário fazer login
cloudflared tunnel login  # Não necessário com remotely-managed
```

---

## Comandos Úteis (Que Pode Usar)

```bash
# Listar todos os túneis da conta
cloudflared tunnel list

# Ver informação de um túnel específico
cloudflared tunnel info <tunnel-name-or-id>

# Verificar versão do cloudflared
cloudflared version

# Atualizar cloudflared para última versão
cloudflared update

# Testar conectividade manualmente (sem instalar como serviço)
cloudflared tunnel run --token <TOKEN>
```

---

## Segurança

### Boas Práticas

✅ **Cloudflare Access** - Adicione autenticação extra (Email PIN, Google OAuth, etc.)
✅ **IP Allowlisting** - Restrinja acesso a IPs específicos (ex: GitHub webhooks)
✅ **TLS Verify** - Sempre que possível, use certificados válidos no backend
✅ **Logs** - Monitore logs regularmente para detetar acessos suspeitos
✅ **Secrets** - Use webhooks secrets para GitHub/GitLab (ver WEBHOOK_SECURITY.md)

### Cloudflare Access (Autenticação Extra)

Para proteger endpoints sensíveis:

1. Dashboard → **Access** → **Applications** → **Add an application**
2. Escolher **Self-hosted**
3. Configurar domínio (ex: `cicd.vitormineiro.com`)
4. Definir **Policies** (ex: só permitir email `@vitormineiro.com`)
5. Escolher método de autenticação (Email PIN, Google, GitHub, etc.)

Agora qualquer acesso a `cicd.vitormineiro.com` requer autenticação via Cloudflare Access.

---

## Referências

- [Cloudflare Tunnel Docs](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/)
- [Remotely-Managed Tunnels](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/tunnel-guide/remote/)
- [Cloudflare Access](https://developers.cloudflare.com/cloudflare-one/policies/access/)

---

## Documentação Relacionada

- [WEBHOOK_SECURITY.md](WEBHOOK_SECURITY.md) - Segurança para GitHub webhooks
- [CLOUDFLARED_SETUP.md](CLOUDFLARED_SETUP.md) - Método antigo (locally-managed) - DEPRECATED
- [CLOUDFLARE_TUNNEL_DEMOS.md](CLOUDFLARE_TUNNEL_DEMOS.md) - Expor demos e websites temporários
