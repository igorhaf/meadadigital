# 02 — Autenticação e Multi-tenancy

[← Home](00-HOME.md)

## Dois perfis de acesso, duas vias

| Perfil | Quem é | Via de acesso | Escopo |
|--------|--------|---------------|--------|
| **Super-admin (root)** | Email na allowlist `ADMIN_SUPER_ADMIN_EMAILS` (env). **Sem** linha em `public.users`. | Spring REST + `service_role` (FORA do RLS). | Lê/edita TODAS as empresas e usuários (ex.: lista global de empresas). |
| **Tenant-admin** | Linha em `public.users` com `company_id` + `role`. | Supabase SDK + RLS para CRUD interno; Spring REST (`/admin`, `/api/{nicho}`) para o resto. | Só dados da própria empresa, isolado por `company_id`. |

Roles dentro do tenant: `owner / admin / agent` (CHECK na tabela `users`).

Há também o estado transitório **INVITEE**: um JWT válido (a pessoa criou conta no Supabase Auth)
mas **sem** linha em `users` ainda — só pode chamar `POST /api/invitations/{token}/accept`, que
cria a linha em `users` e o promove a tenant-admin.

## JWT ES256 + JWKS

- Tokens são emitidos pelo **Supabase Auth**, assinados em **ES256** (assimétrico). As chaves
  públicas vêm do **JWKS** remoto do Supabase.
- Validação por filtro próprio: `src/main/java/com/meada/admin/security/JwtAuthenticationFilter.java`
  — **não** Spring Security.
- Header: `Authorization: Bearer <token>`.
- Claims lidos: `sub` (= `auth.uid()`), `email`.
- Fluxo do filtro:
  1. Valida assinatura contra o JWKS.
  2. Lê `sub`/`email`.
  3. Se o email está na allowlist `ADMIN_SUPER_ADMIN_EMAILS` (normalizada lowercase no boot) →
     **super-admin**, sem SELECT em `public.users`.
  4. Senão: `SELECT u.company_id, u.palette_id, u.role, u.suspended, u.deleted_at` + status da
     company `FROM public.users u ... WHERE u.id = sub`.
  5. Popula `AuthenticatedUser` (injetado nos controllers via `@RequestAttribute`).
- Sem token → **401** `missing_auth_header`. Token válido sem linha em `users` (e não no fluxo de
  convite), ou com `deleted_at` preenchido → **403** `user_not_provisioned`. Usuário suspenso →
  **403** `forbidden_user_suspended`. Empresa suspensa → **403** `forbidden_company_suspended`.

### Paths que o filtro protege

O `JwtAuthenticationFilter` exige token em `/admin/**`, em `/api/{nicho}/**` (todos os 33 nichos:
`/api/sushi/**`, `/api/legal/**`, … `/api/suplementos/**`), em **`/api/cms/**`** (CMS do tenant —
adicionalmente gateado por feature flag, ver abaixo) e no aceite de convite
(`POST /api/invitations/{token}/accept`, fluxo INVITEE). Ficam **fora** da exigência de token:
`/webhooks/**` (tem o próprio filtro de secret), `/public/**` (CMS público + showcase
`/public/niches`), `GET /api/invitations/{token}` (lookup do convite), `/api/chat/{companySlug}`
(webchat público) e `POST /api/access-logs`.

### Feature flags por nicho (camada 9.0)

1. Features são **HARDCODED** (`ProfileFeature` ↔ `profile-feature.ts`, com teste de paridade);
   a tabela `profile_features` guarda **só desvios** (ausência = OFF; opt-in do root). É tabela de
   **plataforma** (RLS force, só `service_role`).
2. Gate no backend: `ProfileFeatureGuard.requireFeature(user, ProfileFeature.X)` → **403**
   `feature_disabled`. O CMS inteiro (`/api/cms/**`) é gateado por `requireFeature(CMS)`.
3. O tenant recebe o conjunto resolvido via `GET /admin/me` (campo `features`); o root administra
   a grade em `/dashboard/profile-features` (cache Caffeine TTL 20s — toggle demora ≤20s).

## RLS — isolamento por tenant no banco

A função central é **`app.company_id()`** (definida em `supabase/migrations/02_tables.sql`):

- `SECURITY DEFINER` — executa com privilégio do dono para ler `public.users` sem recursão de RLS.
- `STABLE` — cacheável dentro de uma query.
- Retorna o `company_id` do usuário autenticado (via `auth.uid()`); retorna **NULL** sob
  `service_role` (o backend ignora RLS de propósito).

Todas as policies reduzem a `company_id = app.company_id()`. O tenant-admin, operando pelo Supabase
SDK, só enxerga as próprias linhas. O **backend** opera como `service_role` (RLS desligado) e
**compensa** passando `company_id` explícito em cada query — não confia só no RLS.

### FKs compostas (defesa em profundidade)

Como o `service_role` ignora RLS, o isolamento é reforçado no schema: FKs para tabelas
tenant-aware incluem `company_id` no par. Exemplo:

```sql
conversations (contact_id, company_id) → contacts (id, company_id)
```

Assim, mesmo um bug no backend não consegue ligar um recurso de uma empresa a outro — o banco
recusa a FK. (Ver [01 — Arquitetura](01-arquitetura.md), convenções de banco.)

## Tabelas de identidade

| Tabela | Papel |
|--------|-------|
| `companies` | O tenant. `id, slug, status ('active'/'suspended'), profile_id` (NOT NULL, CHECK com todos os perfis — 33 nichos + `generic`) e `admin_token` (8 chars hex, migration 71) que compõe o email **determinístico** do tenant-admin: `meada_{slug}_{admin_token}@meadadigital.com`. |
| `users` | Operadores do painel. `id (FK auth.users), company_id, role, suspended, deleted_at`. RLS por `company_id`. |
| `whatsapp_instances` | Instâncias Evolution por tenant. `evolution_token` é coluna blindada (grant restrito). |

## Suspensão e LGPD

- **Usuário suspenso:** coluna `suspended` em `users` → filtro retorna **403** `forbidden_user_suspended`.
- **Empresa suspensa:** `companies.status = 'suspended'` → **403** `forbidden_company_suspended`.
- **Soft delete de usuário:** `deleted_at`; backend/RLS filtram `deleted_at IS NULL`.
- **Direito ao esquecimento (contato):** módulo `lgpd/` — `GET /admin/contacts/{id}/export` (ZIP com
  todos os dados do contato) e `DELETE /admin/contacts/{id}/erase` (apaga em cascata). Ver
  [07 — Plataforma](07-plataforma.md).

## Convites de operador

Fluxo (`invitations/` + `admin/invitations/`):

1. Tenant-admin: `POST /admin/invitations {email, role}` → cria token (`tenant_invitations`, status `pending`).
2. Convidado abre o link: `GET /api/invitations/{token}` (público) valida o convite.
3. Convidado cria conta no Supabase Auth (ganha JWT), e chama `POST /api/invitations/{token}/accept`
   — o filtro reconhece o modo INVITEE (JWT válido, sem linha em `users`), o serviço cria a linha em
   `users` e marca o convite como `accepted`.

Super-admin vê todos os convites (`GET /admin/invitations/all`) e pode revogar
(`POST /admin/invitations/{id}/revoke`).

## Auditoria e logs de acesso

- `access/` — `POST /api/access-logs` (público; registra login_success/login_failed/password_changed,
  captura IP via 1º hop de `X-Forwarded-For` + User-Agent). `GET /admin/access-logs` lista os 100
  mais recentes do tenant.
- `common/AuditLogger` — log estruturado de ações sensíveis (quem, o quê, quando), consultável em
  `/admin/audit-logs` e nas telas de auditoria do super-admin.

## Webhook — autenticação própria

O endpoint `/webhooks/evolution` **não** usa JWT. Ele é protegido por `WebhookSecretFilter`
(HMAC/secret): o header `apikey` (ou query `?apikey=`) é comparado em tempo constante com
`WEBHOOK_SECRET`. Secret inválido → **401** `invalid_secret`. Ver [03 — IA e Fluxo](03-ia-fluxo.md).
