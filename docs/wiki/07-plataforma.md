# 07 — Núcleo de Plataforma

[← Home](00-HOME.md)

Features de plataforma que não pertencem a um nicho específico — são do core ou do super-admin.
(Re-verificado contra o código em 2026-08-09.)

## CMS / Site por tenant (camada 9.x) — `cms/`

Page builder com domínio próprio. **Gateado por feature flag** (`requireFeature(CMS)`) para
tenants; para o **root** é EMBUTIDO (sempre ligado, fora da grade — ver company-âncora abaixo).

- **Modelo (migrations 41/42):** `cms_sites` (1:1 com company: domain, domain_verified,
  verify_token, theme JSONB, published) + `cms_pages` (N por company: page_slug, title, blocks
  JSONB, is_home, position, published). 1 home por company; UNIQUE (company_id, page_slug);
  máximo **30 páginas** por site (`too_many_pages`).
- **Blocos (`CmsBlockType`, com parity test) — hoje são 29 tipos**, não mais 8:
  - 22 genéricos: `hero`, `text`, `services`, `contact`, `gallery`, `faq`, `testimonials`,
    `map`, `banner_strip`, `stats`, `feature_grid`, `image_text_split`, `steps`, `columns`,
    `packages` (preço/planos), `marquee`, `quote`, `cta`, `reviews_carousel`, `video`,
    `rating_badge`, `logo_strip`;
  - 6 institucionais `meada_*` (`meada_hero/services/portfolio/cta/navbar/footer`) +
    `niches_grid` (consome `/public/niches` — ver Showcase).
  - `blocks` é JSONB ordenado `{id, type, props}`, validação app-level. Galeria por URL colada
    (upload bloqueado — sem SERVICE_ROLE_KEY).
- **Tema:** `cms_sites.theme` (`{primaryColor, dark}`).
- **Domínio + posse:** valida hostname, rejeita hosts do Meada (`invalid_domain`), UNIQUE
  (`domain_taken`). Posse por TXT DNS: gera `verify_token`, tenant publica
  `_meada-verify=<token>`, `verifyDomain` consulta via DNS (`JndiDnsTxtResolver`).
- **Público (sem auth):** `/public/cms/by-slug/{slug}[/{pageSlug}]`, `/public/cms/by-domain?host=`,
  `/public/cms/tls-allowed?domain=` (ask do Caddy para emitir cert só de domínio
  verificado+publicado).
- **Editor (tenant):** `/dashboard/cms` — multi-página, blocos com drag-drop, tema, domínio.
- **Endpoints tenant:** `/api/cms/site` (GET/publish/theme/domain/verify) + `/api/cms/pages`
  (POST/PUT/home/DELETE). Erros: `feature_disabled` (403), `invalid_*`/`too_many_pages` (400),
  `domain_taken`/`page_slug_taken` (409).
- **Company-âncora do root (migration 44):** o site institucional do Meada é um site do MESMO
  CMS, ancorado numa company única `is_platform=true` (profile `generic`, id zero-UUID). O
  super-admin edita o site do produto no mesmo editor; o `CmsTenantController` resolve a âncora
  para o root.
- **Páginas institucionais por nicho (migration 70):** seed de 1 página CMS por vertical
  (33 nichos) na âncora, servida em `meadadigital.com/{nicho}` via
  `/public/cms/by-slug/meada/{nicho}` — navbar/hero/feature_grid/cta/footer `meada_*` + bloco
  `packages` com **preço por nicho** (R$ 147–497/mês). Fallback estático em
  `frontend/lib/cms/meada-institutional-fallback.json`.
- Doc: `docs/CMS.md`.

## Feature flags por nicho (camada 9.0) — `profiles/ProfileFeature` + `admin/`

Infra para o root ligar/desligar features por nicho num lugar só.

- **Feature hardcoded** (`ProfileFeature` enum Java ↔ `profile-feature.ts`, parity). Membro
  único até hoje: `CMS`.
- **Tabela `profile_features`** (migration 40): guarda só os **desvios do default**. Ausência de
  linha = **OFF** (opt-in explícito do root). PK `(profile_id, feature_key)`, `enabled`,
  `updated_at/by`. Tabela de PLATAFORMA (RLS force, só service_role).
- **`generic` não entra na grade** (é o produto do próprio admin).
- **Resolver** `ProfileFeatureService` (Caffeine TTL 20s — toggle demora ≤20s); o tenant recebe
  o resolvido via `GET /admin/me.features`.
- **Gate** `ProfileFeatureGuard.requireFeature(user, ProfileFeature.X)` → 403 `feature_disabled`.
- **Root:** tela-grade `/dashboard/profile-features` (superAdminOnly). `GET /admin/profile-features`
  + `PUT /admin/profile-features/{profileId}/{featureKey} {enabled}` (audita
  `PROFILE_FEATURE_TOGGLED` + invalida cache).
- Doc: `docs/FEATURE_FLAGS.md`.

> Sobre **quais nichos fazem sentido ter CMS ligado**: os de varejo/serviço local/visual (salão,
> barbearia, estética, restaurantes, pousada, eventos, pet, etc.) se beneficiam de vitrine pública;
> os clínicos/regulados (legal, dental, nutri, dermatologia) têm restrições de publicidade
> profissional (OAB/CFO/CFN/CFM) e pedem cautela — preferir OFF ou um conjunto de blocos restrito.

## Showcase de nichos — `profiles/showcase/` (migrations 48 + 70)

Vitrine institucional do produto: o ROOT marca quais nichos são **destaque** na home e a ordem
do grid; a página `/produtos` lista todos.

- **Tabela `niche_showcase`** (mig 48): `profile_id` (hardcoded em `ProfileType`, sem FK —
  validação app-level → 400 `unknown_profile`), `featured`, `display_order`. Ausência de linha =
  não-destaque + fim da ordem (resolver itera o enum e sobrepõe as linhas, igual
  `profile_features`). Tabela de PLATAFORMA (só service_role).
- **Limite de destaque:** `MAX_FEATURED = 6` — regra no service, não constraint → 409
  `too_many_featured`.
- **Root (superAdminOnly, 403 `forbidden_not_super_admin`):** `GET /admin/niches/showcase`
  (grade completa) + `PUT /admin/niches/showcase/{profileId} {featured, displayOrder}`.
- **Público:** `GET /public/niches[?featured=true]` — consumido pelo bloco `niches_grid` do CMS
  (home pede só destaques; `/produtos` pede todos). O card usa productName + paleta do nicho.

## Assinaturas do produto — `subscriptions` (migration 118)

Schema pronto para o pagamento recorrente do PRÓPRIO Meada (Mercado Pago Preapproval) —
**só a tabela, sem código ainda**. Detalhes e estado real em [06 — Pagamentos](06-pagamentos.md).

## LGPD — `lgpd/`

Tenant-scoped (companyId do JWT, nunca de input; 404 `contact_not_found`):

- `GET /admin/contacts/{id}/export` — **export JSON** com todos os dados do contato: a linha do
  contato + conversas + todas as mensagens + agendamentos + tags.
- `DELETE /admin/contacts/{id}/erase` — direito ao esquecimento: **hard delete real** em ordem
  FK-segura (messages → conversation_tags → appointments → conversations → contact; as FKs do
  core são ON DELETE RESTRICT). Lê nome/telefone antes para o registro de auditoria.

## Teams — `teams/`

Times/departamentos por tenant (preparação para RBAC fino). **Tenant-admin only** (403
`forbidden_not_tenant_admin`): `GET/POST /admin/teams`, `PUT/DELETE /admin/teams/{id}`
(404 se não achar). Validação de nome fica no zod do frontend + CHECK do banco.

## Convites — `invitations/` + `admin/invitations/`

Ver [02 — Auth](02-auth-multitenancy.md) (fluxo INVITEE).

## Métricas — `metrics/` + `admin/metrics|dashboard|health`

- **Tenant** (tenant-admin only): `GET /admin/metrics/comparison`, `GET /admin/metrics/export.pdf`,
  `GET /admin/contacts/top`.
- **Super-admin:** `GET /admin/metrics/global`, `GET /admin/dashboard/overview`,
  `GET /admin/health`, `GET /admin/jobs`, `GET /admin/errors`.
- Agregações via `MetricsQueryService`/RPCs no Postgres (conversas, taxa de atendimento, tempos).

## Saved replies — `savedreplies/`

Respostas prontas reutilizáveis pelos operadores. **Tenant-admin only**. CRUD em
`/admin/saved-replies` (GET/POST) e `/admin/saved-replies/{id}` (PUT/DELETE; 404 se não achar).
Campos: `{title, body}` (tabela `saved_replies`).

## Engagement / reativação — `engagement/`

`ReactivationJob` (cron configurável, default diário 9h): para cada empresa com reativação
configurada em **`ai_settings`** (`reactivation_days` + `reactivation_message`), encontra
contatos sem mensagem há N dias e envia a mensagem pela Evolution.

- **Disparo único por janela de silêncio:** marca `contacts.reactivated_at`; contato volta a ser
  elegível só se falar de novo e silenciar de novo (`reactivated_at < last_activity`).
- Canal irresolúvel (sem conversa/credencial) → loga e marca mesmo assim (evita revarredura
  eterna). `EVOLUTION_DRY_RUN` honrado pelo `EvolutionSender`.

## Webchat — `webchat/`

Canal web embeddable (além do WhatsApp): `POST /api/chat/{companySlug}` **público**, body
`{sessionId, message}`, resposta **síncrona** da IA.

- Contato sintético `web:<sessionId>` ("Visitante Web"), conversa `channel='web'` isolada,
  mesma engine de IA (espelho enxuto do `OutboundService`: sem retry/handoff/insights).
- Reusa uma `whatsapp_instance` da empresa como portadora da FK NOT NULL — `channel` distingue.
- **Defensivo por contrato:** falha da IA devolve fallback educado com **200** (não 500).
- Erros: 400 `invalid_request`, 404 `company_not_found` (slug desconhecido/inativo),
  409 `company_not_provisioned` (empresa sem instância). Ver [03 — IA e Fluxo](03-ia-fluxo.md).

## Busca global — `search/`

`GET /admin/search?q=` — **tenant-admin only**. Pesquisa contatos, conversas e mensagens da
própria empresa por similaridade textual (**pg_trgm**: filtro `ilike` nos índices GIN + ordenação
por `similarity()`). Mínimo 2 caracteres (senão listas vazias); top 10 por grupo.

## Treinamento / feedback da IA — `training/`

O tenant-admin avalia respostas da IA (modo treinamento):

- `POST /admin/message-feedback {messageId, rating, correction?}` — upsert (UNIQUE por
  message_id): 201 cria, 200 atualiza. `rating ∈ {good, bad}`; 400 `invalid_request`;
  404 `message_not_found` (FK + escopo garantem só mensagem da própria empresa).
- `GET /admin/message-feedback?rating=bad` — 50 mais recentes com o conteúdo da mensagem.

## Base de conhecimento / RAG — `knowledge/`

Documentos PDF do tenant viram contexto semântico da IA. **Tenant-admin only** (403
`forbidden_not_tenant`).

- **Ingestão SÍNCRONA:** `POST /admin/knowledge/documents` cria o documento
  (`status=processing`), extrai texto (PDFBox), chunka (`KnowledgeChunker`), embeda no **sidecar
  Python** (FastAPI + sentence-transformers, dim **384**, batches de 32 — serviço `embeddings`
  :7080) e persiste os chunks (pgvector); marca `ready` (char_count, chunk_count) ou `failed`
  (a marcação sobrevive ao rollback de propósito). O PDF original NÃO sobe pro Storage.
- **Gestão:** `GET /admin/knowledge/documents`, `DELETE /admin/knowledge/documents/{id}`,
  `PATCH /admin/knowledge/documents/{id}/active`.
- **Retrieval** (`KnowledgeRetrievalService`): embeda a mensagem do cliente (kind=QUERY) e busca
  os top-5 chunks acima do threshold por cosine distance (`<=>`), só de documentos ativos do
  tenant; lista vazia = a IA não recebe contexto de documento.

## Logs de acesso — `access/` + `admin/audit/`

- `POST /api/access-logs` — **PÚBLICO** (o login acontece no frontend via Supabase; um
  `login_failed` não tem sessão). Só aceita as 3 ações do enum (`login_success`, `login_failed`,
  `password_changed`; senão 400 `invalid_action`); ip/user_agent derivados do request;
  company/user resolvidos best-effort pelo email (podem ficar null — forense global).
- `GET /admin/access-logs` — tenant-admin: os 100 acessos mais recentes da própria empresa.
- `GET /admin/security/access-logs/all` — super-admin: forense global.

## Conexão do WhatsApp pelo painel — `admin/instances/`

O tenant conecta o próprio número sem o root (camada 4.6). **Tenant-admin only**; a Evolution é
a fonte da verdade do estado.

- `GET /admin/whatsapp` — estado sincronizado · `POST /admin/whatsapp/connect` —
  provisiona/retoma a instância e devolve o **QR code** (data-URI base64) ·
  `POST /admin/whatsapp/disconnect` — logout (instância e histórico permanecem).
- Erros: 403 `forbidden_not_tenant`, 503 `whatsapp_unavailable` (servidor sem
  `EVOLUTION_GLOBAL_API_KEY` — recurso opcional, boot não quebra), 409 `already_connected` /
  `instance_name_taken`, 502 `evolution_error`. Envs: `EVOLUTION_GLOBAL_API_KEY` +
  `EVOLUTION_WEBHOOK_URL` (vazias por default).
- Tela: `/dashboard/whatsapp`.

## Admin / super-admin — `admin/`

Painel root e tenant. Áreas principais:

- **companies/** — CRUD de empresas, suspender/reativar, notas internas. **Impersonation:**
  `POST /admin/companies/{id}/impersonate` gera magiclink via Supabase Admin API (auditado
  `impersonated`; 503 `impersonation_unavailable`, 502 `impersonation_link_failed`). Criação de
  empresa **provisiona o tenant-admin** com email determinístico
  `meada_{slug}_{admin_token}@meadadigital.com` (token de 8 chars — migration 71).
- **users/** — CRUD global de usuários, suspender/reativar, reset de senha (Supabase Admin API).
- **plans/** — catálogo de planos (superAdminOnly): slug/name únicos → 409 `plan_slug_exists`;
  DELETE é soft (`active=false`). **Não integra com `companies.plan_id`** — catálogo isolado;
  pricing real é parte da pendência #50.
- **announcements/** — comunicados: root CRUD (`GET/POST /admin/announcements`,
  `PATCH/DELETE /admin/announcements/{id}`); tenant lê e dá dismiss
  (`GET /admin/me/announcements`, `POST /admin/me/announcements/{id}/dismiss`).
- **audit/** — tenant: `GET /admin/audit-logs`; root: `GET /admin/audit/all`,
  `GET /admin/actions` (catálogo de ações).
- **health/dashboard/metrics** — ver Métricas acima.
- **me/** — `GET /admin/me` devolve identidade resolvida `{email, role, companyId, companyName,
  profileId, profileName, features}`.

## Referências de doc

- `docs/CMS.md`, `docs/FEATURE_FLAGS.md`, `docs/MULTI_PROFILE_DEV.md`
- `docs/PERFIL_*.md` — guia operacional por nicho
- `docs/BACKLOG_EXECUCAO.md` — backlog de execução (inclui o gateway #50)
- `CLAUDE.md` — convenções vivas
