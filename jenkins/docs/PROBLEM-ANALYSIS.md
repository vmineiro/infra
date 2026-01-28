# Análise do Problema - Docker Image Cleanup

## Problema Original

### Pipeline Atual (PERIGOSA)

```groovy
stage('Docker Cleanup') {
    steps {
        sh '''
            docker image prune -a -f          // ❌ PERIGOSO
            docker container prune -f
            docker volume prune -f
            docker network prune -f
            docker system prune -a -f --volumes  // ❌ MUITO PERIGOSO
        '''
    }
}
```

---

## Análise Detalhada do Problema

### Comando 1: `docker image prune -a -f`

#### O que faz:
Remove **TODAS** as imagens que não estão sendo usadas por containers **atualmente**.

#### Por que é perigoso para Jenkins:

```
┌─────────────────────────────────────────────────────────────┐
│  CICLO DE VIDA DE UM JOB JENKINS COM DOCKER AGENT          │
└─────────────────────────────────────────────────────────────┘

1. Job inicia
   └─> Jenkins verifica se imagem existe localmente
       └─> Imagem existe: jenkins/agent:latest ✓

2. Jenkins cria container
   └─> docker run jenkins/agent:latest
       └─> Container ID: abc123
           └─> Estado: Running

3. Job executa
   └─> Comandos executam dentro do container
       └─> Build, testes, deploy, etc.

4. Job termina
   └─> Jenkins para o container
       └─> Container ID: abc123
           └─> Estado: Exited (0)

5. Jenkins remove container
   └─> docker rm abc123
       └─> Container deletado
           └─> Imagem fica SOZINHA ← PROBLEMA AQUI!

6. Próximo job precisa da imagem
   └─> Jenkins verifica se imagem existe
       └─> ❌ Imagem NÃO EXISTE (foi apagada pelo cleanup)
           └─> ❌ Job FALHA: "No such image: jenkins/agent"
```

#### Estado das Imagens Entre Jobs:

```
+------------------+------------------+-------------------------+
|   Estado         |  Containers      |  Docker Considera       |
+------------------+------------------+-------------------------+
| Durante Job      | 1 Running        | Imagem "in use" ✓       |
| Após Job         | 0 (removido)     | Imagem "unused" ❌      |
| Cleanup executa  | 0                | ❌ APAGA A IMAGEM       |
| Próximo Job      | Precisa imagem   | ❌ FALHA (not found)    |
+------------------+------------------+-------------------------+
```

---

### Comando 2: `docker system prune -a -f --volumes`

#### O que faz:
Remove **TUDO** que não está em uso:
- Todas as imagens unused
- Todos os containers parados
- Todos os volumes dangling
- Todas as networks unused
- Todo o build cache

#### Impacto:

```
ANTES DO CLEANUP:
┌─────────────────────────────────────────────┐
│ Docker Images                               │
├─────────────────────────────────────────────┤
│ jenkins/agent:latest          450MB   ✓    │
│ jenkins/inbound-agent:latest  420MB   ✓    │
│ maven:3.9-eclipse-temurin-17  650MB   ✓    │
│ node:18-alpine                180MB   ✓    │
│ python:3.11-slim              150MB   ✓    │
│ openjdk:17-jdk                470MB   ✓    │
│ <none>:<none>                  85MB   ✓    │
│ old-project:v1.0               500MB  ✓    │
└─────────────────────────────────────────────┘
Total: 2.9GB

DEPOIS DO CLEANUP (docker system prune -a -f):
┌─────────────────────────────────────────────┐
│ Docker Images                               │
├─────────────────────────────────────────────┤
│ (vazio - todas removidas)                   │
└─────────────────────────────────────────────┘
Total: 0GB

IMPACTO:
❌ Jenkins não pode mais criar agentes
❌ Todos os jobs falham
❌ Necessário re-download de todas as imagens (~3GB)
❌ Downtime até recuperação completa
```

---

## Diferenças Críticas: Dangling vs Unused

### 1. Dangling Images

```
O QUE SÃO:
- Imagens sem tag (<none>:<none>)
- Layers intermediários de builds
- Imagens substituídas por re-tag

EXEMPLO:
docker build -t myapp:latest .
docker build -t myapp:latest .  # Build novamente

Resultado:
myapp:latest         → Nova imagem (ID: xyz789)
<none>:<none>        → Imagem antiga (ID: abc123) ← DANGLING

SEGURO REMOVER? ✅ SIM
- Não são usadas por nada
- Ocupam espaço desnecessariamente
- Não quebram nada ao remover
```

### 2. Unused Images

```
O QUE SÃO:
- Imagens com tag válido
- NÃO têm containers em execução
- NÃO têm containers parados

EXEMPLO:
jenkins/agent:latest  ← Tem tag, mas nenhum container

SEGURO REMOVER? ❌ NÃO!
- Pode ser necessária para próximo job
- Re-download leva tempo
- Pode quebrar pipelines Jenkins
```

---

## Comparação de Comandos

### docker image prune (sem -a)

```bash
docker image prune -f
```

**Remove:**
- Apenas imagens dangling (<none>:<none>)

**Preserva:**
- TODAS as imagens com tag

**Segurança:** ✅ SEGURO para uso em produção

**Exemplo:**
```
ANTES:
jenkins/agent:latest   450MB  ✓ (preservada)
maven:3.9              650MB  ✓ (preservada)
<none>:<none>          85MB   ✗ (removida)

DEPOIS:
jenkins/agent:latest   450MB  ✓
maven:3.9              650MB  ✓
```

---

### docker image prune -a (com -a)

```bash
docker image prune -a -f
```

**Remove:**
- Imagens dangling
- TODAS as imagens sem containers

**Preserva:**
- Apenas imagens com containers em execução ou parados

**Segurança:** ❌ PERIGOSO para Jenkins

**Exemplo:**
```
ANTES:
jenkins/agent:latest   450MB  (0 containers)
maven:3.9              650MB  (0 containers)
nginx:latest           150MB  (1 container running)

DEPOIS:
nginx:latest           150MB  ✓ (preservada - tem container)
jenkins/agent:latest          ✗ (REMOVIDA - sem containers)
maven:3.9                     ✗ (REMOVIDA - sem containers)
```

---

## Fluxo do Problema

```
┌──────────────────────────────────────────────────────────────┐
│ SEXTA-FEIRA 17:00 - Último job do dia                       │
├──────────────────────────────────────────────────────────────┤
│ 1. Job executa com jenkins/agent:latest                     │
│ 2. Container criado, job executa, container removido        │
│ 3. Imagem jenkins/agent fica sem containers                 │
│ 4. Estado: IMAGEM PRESENTE, SEM CONTAINERS                  │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│ SÁBADO 02:00 - Pipeline de Cleanup                          │
├──────────────────────────────────────────────────────────────┤
│ docker image prune -a -f                                     │
│                                                              │
│ Verifica: jenkins/agent tem containers? NÃO                 │
│ Ação: REMOVE jenkins/agent                                  │
│                                                              │
│ docker system prune -a -f --volumes                          │
│ Ação: REMOVE TUDO que está unused                           │
│                                                              │
│ Resultado: TODAS as imagens Jenkins removidas               │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│ SEGUNDA-FEIRA 09:00 - Primeiro job da semana                │
├──────────────────────────────────────────────────────────────┤
│ Jenkins tenta criar agent                                    │
│ └─> docker run jenkins/agent:latest                         │
│     └─> ❌ ERROR: No such image: jenkins/agent              │
│                                                              │
│ Job FALHA                                                    │
│ Todos os próximos jobs FALHAM                               │
│ CI/CD completamente QUEBRADO                                │
└──────────────────────────────────────────────────────────────┘
```

---

## Solução: Proteção de Imagens

### Método 1: Containers Dummy (Recomendado)

```bash
# Criar container que nunca termina
docker run -d \
  --name jenkins-agent-keeper \
  --restart=unless-stopped \
  --label="purpose=image-protection" \
  jenkins/agent:latest \
  sleep infinity
```

**Resultado:**
```
docker ps
CONTAINER ID   NAME                    STATUS
abc123         jenkins-agent-keeper    Up (mantém imagem "in use")

Agora quando cleanup executa:
- Verifica jenkins/agent tem containers? SIM ✓
- Ação: PRESERVA imagem ✓
```

---

### Método 2: Filtros Específicos

```bash
# Remover apenas imagens antigas (30+ dias), exceto protegidas
docker image prune -a -f \
  --filter "until=720h" \
  --filter "label!=preserve=true"
```

---

### Método 3: Exclusão Manual

```bash
# Listar imagens NÃO protegidas
docker images --format "{{.Repository}}:{{.Tag}}" | \
  grep -vE "jenkins|maven|node|python|openjdk"

# Remover apenas estas
```

---

## Impacto e Custos

### Downtime

```
Imagens apagadas → 10-30 minutos para recuperar
└─> Pull de imagens (3-5 GB) @ 100 Mbps = ~5 minutos
└─> Verificação e testes = ~5 minutos
└─> Comunicação e coordenação = ~10 minutos

Jobs falhados durante recuperação: TODOS
Impacto em desenvolvedores: ALTO
```

### Custos de Network

```
Re-download de imagens:
- jenkins/agent: 450MB
- jenkins/inbound-agent: 420MB
- maven:3.9: 650MB
- node:18-alpine: 180MB
- python:3.11-slim: 150MB
- openjdk:17: 470MB
TOTAL: ~2.3GB

Se acontece mensalmente: ~27GB/ano apenas em re-downloads
```

### Impacto em Produção

```
┌─────────────────────────────────────────────────────────┐
│ ANTES (com problema)                                    │
├─────────────────────────────────────────────────────────┤
│ - Jobs falham aleatoriamente após cleanup              │
│ - Desenvolvedores frustrados                           │
│ - CI/CD não confiável                                  │
│ - Necessário intervenção manual                        │
│ - Perda de produtividade                               │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ DEPOIS (com solução)                                    │
├─────────────────────────────────────────────────────────┤
│ - Cleanup seguro e automático                          │
│ - Imagens críticas sempre presentes                    │
│ - CI/CD confiável 24/7                                 │
│ - Zero intervenções manuais                            │
│ - Máxima produtividade                                 │
└─────────────────────────────────────────────────────────┘
```

---

## Resumo Executivo

### Problema
Pipeline de cleanup usava `docker image prune -a` que apagava imagens Jenkins necessárias para criar agentes, quebrando o CI/CD.

### Causa Raiz
Imagens Jenkins ficam "unused" entre jobs porque Jenkins remove os containers após execução.

### Solução Implementada
1. Pipeline segura que remove apenas dangling images
2. Pipeline de proteção que mantém imagens críticas "in use"
3. Filtros específicos para proteger recursos críticos
4. Monitorização proativa de espaço e imagens

### Resultado Esperado
- CI/CD funcionando 24/7
- Cleanup automático e seguro
- Zero downtime
- Zero intervenções manuais

---

**Documentos Relacionados:**
- [Checklist de Recuperação](RECOVERY-CHECKLIST.md)
- [Boas Práticas](DOCKER-CLEANUP-BEST-PRACTICES.md)
- [Referência Rápida](QUICK-REFERENCE.md)
