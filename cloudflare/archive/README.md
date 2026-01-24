# Arquivos - Configurações Antigas do Cloudflare Tunnel

Este diretório contém ficheiros de configuração antigos do método **locally-managed** de túneis Cloudflare, que foram substituídos pelo método **remotely-managed** (gerido via Dashboard Web).

## Ficheiros Arquivados

### `cloudflared-config.yml.old`
Configuração do túnel `cicd` (ID: `5c221661-edd8-4264-a11d-bf12c0edd821`) usando o método locally-managed.

**Motivo do arquivo:**
- Migração para remotely-managed tunnels (gestão via Dashboard)
- Já não é necessário manter ficheiros de configuração locais
- Mantido apenas para referência histórica

**Conteúdo original:**
```yaml
tunnel: 5c221661-edd8-4264-a11d-bf12c0edd821
credentials-file: ~/.cloudflared/5c221661-edd8-4264-a11d-bf12c0edd821.json

ingress:
  - hostname: cicd.vitormineiro.com
    service: http://192.168.1.74:8080
    originRequest:
      noTLSVerify: true
  - service: http_status:404
```

---

## Novo Método (Atual)

Consulte: [CLOUDFLARED_REMOTELY_MANAGED.md](../CLOUDFLARED_REMOTELY_MANAGED.md)

**Vantagens:**
- ✅ Sem ficheiros de configuração
- ✅ DNS automático
- ✅ Gestão via Dashboard Web
- ✅ Alterações sem restart do serviço
