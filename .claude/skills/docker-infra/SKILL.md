---
name: docker-infra
description: Padrões de Docker e infra local do Meada. Use ao editar Dockerfile, frontend/Dockerfile, docker-compose.yml, caddy/Caddyfile, evolution-local/ ou a estrutura de variáveis de ambiente (nunca os valores) — multi-stage, volumes de hot-reload, rede interna do compose, portas.
---

# Docker e infra local

## Dockerfiles — multi-stage com stage `dev` explícito

Ambos os Dockerfiles seguem o mesmo desenho: stage de **deps/builder** cacheável, stage **prod**
enxuto e stage **dev** usado pelo compose (código real vem por volume, hot-reload).

- Backend (`Dockerfile`): `eclipse-temurin:17-jdk AS builder` → `17-jre AS runtime` →
  `17-jdk AS dev` (mvn spring-boot:run com volume em `src/`).
- Frontend (`frontend/Dockerfile`): `node:20-alpine AS dev` (deps primeiro — `COPY package.json
  package-lock.json*` ANTES do `COPY . .`, para cachear o npm install) → `AS builder` →
  `AS prod` (standalone: copia `.next/standalone`, `.next/static`, `public`).

Regras: dependências SEMPRE instaladas em camada própria antes do código; imagens base fixadas
por major (`node:20-alpine`, `temurin:17`), nunca `latest`; nada instalado no stage prod que só
o dev usa; NUNCA copiar `.env*` para dentro da imagem.

## docker-compose.yml (dev local — fase 0.5)

| Serviço | Porta | Papel |
|---------|-------|-------|
| backend | 8095 | Spring via `mvn spring-boot:run`, hot-reload por volume em `src/` |
| frontend | 3000 | Next dev, hot-reload por volume |
| embeddings | 7080 | sidecar Python |
| caddy | 80 | proxy reverso, vhosts `*.meadadigital.local` + `api.meadadigital.local` |

- O BANCO fica FORA do compose: Supabase local via CLI (`supabase start` — API/Auth :54321,
  Postgres :54322). A Evolution local também é compose SEPARADO (`evolution-local/`:
  API :8086, Postgres :5433, Redis :6380).
- Rede interna: backend fala com o sidecar por `embeddings:7080`; o SSR do Next fala com o
  backend por `CMS_BACKEND_URL=http://backend:8095`; o browser chama a API por
  `api.meadadigital.local`. Esses overrides vão em `environment` DO COMPOSE — o `.env` NUNCA é
  alterado pelo compose.
- Subir/derrubar SEMPRE pelos scripts: `./scripts/meada-up.sh` / `./scripts/meada-down.sh`
  (param o Apache da porta 80 antes; sem disable permanente).
- Testes do backend rodam no HOST (`mvn -B clean test`), não no container (precisam de
  Testcontainers + Docker socket).

## Env — estrutura (NUNCA valores)

- Backend: `.env` na raiz (gitignored), espelhado por `.env.example` — toda variável NOVA entra
  no `.env.example` com placeholder (`GEMINI_API_KEY=<TOKEN>`), nunca com valor real.
- Frontend: `frontend/.env.local` (gitignored) espelhado por `frontend/.env.example`. Só chaves
  `NEXT_PUBLIC_*` públicas por design (anon key protegida por RLS); NUNCA `SERVICE_ROLE_KEY`.
- `CONTEXT.md` (estado operacional vivo) é gitignored — detalhe de porta/credencial vive lá.
- Em skills/docs/commits: valores sempre como placeholder (`<API_URL>`, `<TOKEN>`). Sanity de
  staging antes de todo commit: grep por `eyJ`/`password`/`secret=`.

## Caddy

- `caddy/Caddyfile` roteia os subdomínios de dev (5 vhosts de perfil + api). O template
  `on_demand_tls` do CMS (domínio próprio do tenant) fica COMENTADO em dev — emissão real de
  cert é operação de PROD, gateada pelo `ask` em `/public/cms/tls-allowed` (só domínio
  verificado+publicado). Runbook: docs/CMS.md.

## Portas reservadas (não reaproveitar — tabela viva no CLAUDE.md do workspace)

8095 backend Meada · 3000 frontend · 7080 embeddings · 54321/54322 Supabase CLI ·
8086/5433/6380 Evolution local · 8000 Chroma (claude-mem) · 8001 Meada IA API.
Porta nova = registrar na tabela do workspace ANTES de subir o serviço.

## O que não fazer

- ❌ `COPY . .` antes do install de deps (mata o cache de camada).
- ❌ Banco/valores de env dentro do compose (paridade com prod exige Supabase fora).
- ❌ Editar `docker-compose.yml`/Dockerfile em lote misto — mudança de infra é commit próprio
  (histórico auditável; regra de padronização: prefixo `[REVISAR]`).
