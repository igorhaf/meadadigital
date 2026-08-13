# 01 — Arquitetura e Stack

[← Home](00-HOME.md)

## Visão geral

Meada é um **monolito multi-tenant** (single-module Maven) que se apresenta como N produtos
verticais. O backend é o cérebro: recebe mensagens do WhatsApp (via Evolution API), gera respostas
com IA (Gemini), e devolve ao cliente — tudo isolado por tenant no banco (Supabase Postgres + RLS).

```
WhatsApp ⇄ Evolution API ⇄ [Webhook] → Backend Spring Boot → Gemini (IA)
                                          │
                                          ├─ Supabase Postgres (RLS, multi-tenant)
                                          ├─ Sidecar de embeddings (RAG)
                                          └─ Evolution API ([Outbound] envio de resposta)

Painel: Next 16 (frontend) ⇄ Backend (/admin, /api) + Supabase SDK (RLS) direto
```

## Stack

| Camada | Tecnologia | Notas |
|--------|-----------|-------|
| Backend | **Spring Boot 3.3.13 + Java 17 Temurin** | Single-module Maven, **JdbcTemplate (não JPA)**, sem Lombok, sem webflux. HTTP outbound síncrono via `RestClient`. |
| Banco/Auth | **Supabase (Postgres 17 + Auth + Storage)** | JWT ES256. Migrations SQL em `supabase/migrations/` (01→118). RLS em todas as tabelas de domínio. Em dev, Supabase **LOCAL via CLI** (`supabase start` — API/Auth `:54321`, Postgres `:54322`). |
| IA | **Gemini Flash** (Google) | `responseSchema` JSON (`reply` + `needs_human` + `reason` + intents/insights opcionais). Persona por perfil injetada no system prompt. |
| WhatsApp | **Evolution API self-hosted** (`evoapicloud/evolution-api:v2.3.1`) | Uma instância por tenant. Em dev, serviço compartilhado em `~/shared/evolution-local` (fora do repo). |
| RAG/Embeddings | **pgvector** + sidecar Python (porta 7080, modelo `multilingual-e5-small`) | Retrieval semântico da base de conhecimento. Código do sidecar no repo `shared` (`~/shared/python`), buildado pelo compose (`context: ../shared/python`). |
| Frontend | **Next 16 (app router) + React 19 + TS + Tailwind 4 + shadcn/ui + @base-ui/react** (NÃO Radix) + TanStack Query + react-hook-form + zod + @supabase/ssr | Em `frontend/`, isolado do Maven. |

Portas locais: backend **8095**, frontend **3000**, sidecar embeddings **7080**, Evolution local
**8086** (Postgres 5433 / Redis 6380), Supabase CLI **54321** (API/Auth) / **54322** (Postgres).

## Módulos top-level do backend (`src/main/java/com/meada/`)

| Módulo | Responsabilidade |
|--------|------------------|
| `admin/` | Painel super-admin + tenant: companies, users, invitations, security (JWT filter), audit, dashboard, health, metrics, announcements, plans, me. |
| `ai/` | Integração Gemini (`GeminiProvider`), `PromptBuilder`, `system-template.txt`, `AiResponse`. |
| `messaging/` | Contatos, conversas, mensagens (núcleo do atendimento). |
| `outbound/` | Envio de mensagens (`OutboundService`, `EvolutionClient`), gate de horário, **cadeia de handlers de tag**, retry. |
| `webhook/` | Inbound da Evolution (`EvolutionWebhookController`, `WebhookService`), validação HMAC, guard de frescor. |
| `webchat/` | Chat web embeddable (canal além do WhatsApp). |
| `knowledge/` | Base de conhecimento: upload de documentos, chunking, embeddings, retrieval semântico (RAG). |
| `training/` | Feedback dos agentes sobre respostas da IA. |
| `profiles/` | Suporte multi-perfil: enum `ProfileType` (33 nichos + `generic`), guards, context caches, os 33 diretórios de nicho, `features/` (feature flags por nicho — camada 9.0, gate `requireFeature` → 403 `feature_disabled`) e `showcase/` (páginas institucionais por nicho, `GET /public/niches`). |
| `cms/` | Site/página por tenant (page builder, domínio próprio). |
| `access/` | Logs de acesso (login sucesso/falha, troca de senha). |
| `teams/` | Times/grupos de usuários por tenant. |
| `invitations/` | Convite de novos operadores ao tenant. |
| `lgpd/` | Export e direito ao esquecimento (apaga dados de um contato). |
| `metrics/` | Métricas e dashboards (tenant + global), export. |
| `availability/` · `appointments/` | Helpers de slots/agenda (usados pelos perfis com agenda). |
| `engagement/` | Reativação de contatos inativos. |
| `savedreplies/` | Respostas pré-gravadas para operadores. |
| `search/` | Busca. |
| `common/` | Utilitários (AuditLogger, PageResponse, ErrorResponse). |
| `config/` | Configuração de plataforma. |

## Convenções de banco

- **PK:** `uuid primary key default gen_random_uuid()`.
- **Multi-tenant:** coluna `company_id uuid not null` em **toda** tabela de domínio (exceto `companies` e `users`).
- **FKs compostas anti-cross-tenant:** FKs para tabelas tenant-aware incluem `company_id` no par (ex.: `conversations (contact_id, company_id) → contacts (id, company_id)`). Isso fecha o isolamento **no nível do banco**, mesmo sob `service_role` (que ignora RLS). Ver [02 — Auth](02-auth-multitenancy.md).
- **Soft delete:** coluna `deleted_at` em services/faqs/documents/contacts; respeitada em índices parciais.
- **Timestamps:** `created_at/updated_at timestamptz not null default now()`.
- **Materialização, não GENERATED:** valores que cruzam linhas/tabelas (`total_cents`, `line_total_cents`, `end_at = start_at + duração`) são **materializados no INSERT/UPDATE**, nunca colunas geradas — porque `timestamptz + interval` não é IMMUTABLE e o recálculo cruza linhas. (Lição cravada; vale para todos os nichos.)
- **RLS:** `enable + force` em todas as tabelas de domínio; policies reduzem a `company_id = app.company_id()`. INSERT de artefatos sensíveis (pedidos, agendamentos) é só `service_role` (o backend é o escritor); o tenant tem `select/update`.

## Decisões arquiteturais cravadas

1. **JdbcTemplate, não JPA** — controle fino de SQL, zero ORM/Hibernate.
2. **Backend = escritor único via `service_role`; painel = leitor via Supabase SDK + RLS** — defesa em profundidade.
3. **JWT ES256 + JWKS** — validação assimétrica por filtro próprio (`JwtAuthenticationFilter`), não Spring Security.
4. **FKs compostas com `company_id`** — isolamento de tenant no banco, não só no RLS.
5. **Mensagens imutáveis** — `messages` nunca sofre DELETE (histórico íntegro).
6. **Perfis HARDCODED** — não existe tabela de perfis; enum Java + const TS + CHECK na migration, com teste de paridade (`ProfileTypeParityTest`).
7. **Tag handlers por perfil** — a IA emite tags em texto livre; cada perfil parseia as suas em `outbound/` sem forkar o core.
8. **Inbound assíncrono** — o webhook persiste e publica `MessageInboundProcessedEvent` DENTRO da transação; o `OutboundEventListener` é `@Async("outboundExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)` — a IA + outbound só rodam depois que a inbound está durável, sem bloquear o 200 do webhook.
9. **RAG nativo no banco** (pgvector) — sem Elasticsearch.
10. **Sidecar Python de embeddings** — desacoplado, reutilizável.

## Boot local

- **Backend** (porta 8095): `./scripts/run-local.sh`. Sanity: `GET /admin/me` sem token → 401 `missing_auth_header`.
- **Frontend** (porta 3000): `cd frontend && npm run dev`.
- **DevX Docker** (porta 80 via Caddy): `./scripts/meada-up.sh` / `meada-down.sh`. O compose do repo
  sobe **só os serviços do próprio Meada**: `backend`, `frontend`, `embeddings` e o ambiente
  **demo** (`demo-backend`/`demo-frontend`, só com `--profile demo` — mesmo código, Supabase
  separado via `DEMO_*`). O **Caddy (ingress)** e a rede externa compartilhada **`meada`** vivem no
  repo `shared` (`~/shared`), fora deste compose. O **banco fica fora** dos containers (dev:
  Supabase CLI local; prod: Supabase remoto via `.env`). O `docker-compose.override.yml` troca os
  targets para `dev` (hot-reload por volume) automaticamente em `docker compose up`.
- **Testes backend:** `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -B clean test`.
- **Build frontend:** `cd frontend && npm run build`.

## Referências de arquivo

- `pom.xml` — dependências/versões.
- `src/main/resources/prompts/system-template.txt` — template do system prompt.
- `supabase/migrations/` — schema (01→118).
- `CLAUDE.md` — convenções vivas e histórico de camadas.
