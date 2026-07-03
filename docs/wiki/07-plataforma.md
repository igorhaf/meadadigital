# 07 — Núcleo de Plataforma

[← Home](00-HOME.md)

Features de plataforma que não pertencem a um nicho específico — são do core ou do super-admin.

## CMS / Site por tenant (camada 9.x)

Page builder com domínio próprio. **Gateado por feature flag** (`requireFeature(CMS)`): só nichos
com a flag ligada têm acesso.

- **Modelo (migrations 41/42):** `cms_sites` (1:1 com company: domain, domain_verified, verify_token,
  theme JSONB, published) + `cms_pages` (N por company: page_slug, title, blocks JSONB, is_home,
  position, published). 1 home por company; UNIQUE (company_id, page_slug).
- **8 blocos** (`CmsBlockType`, com parity test): `hero`, `text`, `services`, `contact`, `gallery`,
  `faq`, `testimonials`, `map`. `blocks` é JSONB ordenado `{id, type, props}`; validação app-level.
  Galeria por URL colada (upload bloqueado — sem SERVICE_ROLE_KEY).
- **Tema:** `cms_sites.theme` (`{primaryColor, dark}`).
- **Domínio + posse:** valida hostname, rejeita hosts do Meada (`invalid_domain`), UNIQUE
  (`domain_taken`). Posse por TXT DNS: gera `verify_token`, tenant publica `_meada-verify=<token>`,
  `verifyDomain` consulta via DNS (`JndiDnsTxtResolver`).
- **Público (sem auth):** `/public/cms/by-slug/{slug}[/{pageSlug}]`, `/public/cms/by-domain?host=`,
  `/public/cms/tls-allowed?domain=` (ask do Caddy para emitir cert só de domínio verificado+publicado).
- **Editor (tenant):** `/dashboard/cms` — multi-página, blocos com drag-drop, tema, domínio.
- **Endpoints tenant:** `/api/cms/site` (GET/publish/theme/domain/verify) + `/api/cms/pages`
  (POST/PUT/home/DELETE). Erros: `feature_disabled` (403), `invalid_*`/`too_many_pages` (400),
  `domain_taken`/`page_slug_taken` (409).
- Doc: `docs/CMS.md`.

## Feature flags por nicho (camada 9.0)

Infra para o root ligar/desligar features por nicho num lugar só.

- **Feature hardcoded** (`ProfileFeature` enum Java ↔ `profile-feature.ts`, parity). Membro inicial:
  `CMS`.
- **Tabela `profile_features`** (migration 40): guarda só os **desvios do default**. Ausência de
  linha = **OFF**. PK `(profile_id, feature_key)`, `enabled`, `updated_at/by`.
- **`generic` não entra na grade** (é o produto do próprio admin).
- **Resolver** `ProfileFeatureService` (cache 20s); o tenant recebe o resolvido via `/admin/me.features`.
- **Gate** `ProfileFeatureGuard.requireFeature(user, ProfileFeature.X)` → 403 `feature_disabled`.
- **Root:** tela-grade `/dashboard/profile-features` (linhas=nichos, colunas=features, célula=toggle).
  `GET /admin/profile-features` + `PUT /admin/profile-features/{profileId}/{featureKey} {enabled}`
  (audita `PROFILE_FEATURE_TOGGLED`).
- Doc: `docs/FEATURE_FLAGS.md`.

> Sobre **quais nichos fazem sentido ter CMS ligado**: os de varejo/serviço local/visual (salão,
> barbearia, estética, restaurantes, pousada, eventos, pet, etc.) se beneficiam de vitrine pública;
> os clínicos/regulados (legal, dental, nutri, dermatologia) têm restrições de publicidade
> profissional (OAB/CFO/CFN/CFM) e pedem cautela — preferir OFF ou um conjunto de blocos restrito.

## LGPD — `lgpd/`

- `GET /admin/contacts/{id}/export` — ZIP com todos os dados do contato (mensagens, agendamentos,
  pedidos, etc.).
- `DELETE /admin/contacts/{id}/erase` — direito ao esquecimento: apaga/anonimiza em cascata.

## Teams — `teams/`

Times/grupos de operadores por tenant (preparação para RBAC fino). CRUD em `/admin/teams`
(tenant-admin). Erros: 403 `forbidden_not_tenant_admin`, 404.

## Convites — `invitations/` + `admin/invitations/`

Ver [02 — Auth](02-auth-multitenancy.md) (fluxo INVITEE).

## Métricas — `metrics/`

- Tenant: `GET /admin/metrics/comparison`, `GET /admin/metrics/export.pdf`, `GET /admin/contacts/top`.
- Super-admin: `GET /admin/metrics/global`, `GET /admin/dashboard/overview`.
- RPCs de agregação no Postgres (total de conversas, taxa de atendimento, tempo médio, etc.).

## Saved replies — `savedreplies/`

Respostas pré-gravadas reutilizáveis pelos operadores. CRUD em `/admin/saved-replies` (ou
equivalente), tabela `saved_replies` (title, content, category).

## Engagement — `engagement/`

`ReactivationJob`: job agendado que identifica contatos inativos e dispara campanha de reativação.
Config por company (`reactivation_config`: days_inactive, enabled).

## Webchat — `webchat/`

Canal web embeddable (além do WhatsApp): formulário → contato web → conversa isolada → mesma engine
de IA. Ver [03 — IA e Fluxo](03-ia-fluxo.md).

## Admin / super-admin — `admin/`

Painel root e tenant. Áreas principais:

- **companies/** — CRUD de empresas, suspender/reativar, **impersonar**, notas internas.
- **users/** — CRUD global de usuários, suspender/reativar, reset de senha (via Supabase Admin API).
- **plans/** — planos de assinatura (hoje todos no mesmo plano; pricing é parte da pendência #50).
- **announcements/** — comunicados globais (root) + dismiss pelo tenant.
- **audit/**, **access-logs** — trilha de auditoria e logs de acesso.
- **health/**, **dashboard/**, **metrics/** — saúde da plataforma, jobs, erros, métricas globais.
- **me/** — `GET /admin/me` devolve identidade resolvida `{email, role, companyId, companyName,
  profileId, profileName, features}`.

## Showcase de nichos — `search/`/showcase (migration 48 + 70)

Vitrine institucional do produto Meada com uma página por nicho (landing por vertical), gerida no
admin. Endpoints públicos `/public/showcase/{nicho}` e a página `/meada-site`.

## Referências de doc

- `docs/CMS.md`, `docs/FEATURE_FLAGS.md`, `docs/MULTI_PROFILE_DEV.md`
- `docs/PERFIL_*.md` — guia operacional por nicho
- `docs/RELATORIO_POS_MARATONA_2026-06-16.md` — roadmap/pendências (inclui #50)
- `CLAUDE.md` — convenções vivas
