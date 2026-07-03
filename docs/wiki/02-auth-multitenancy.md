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
  3. `SELECT company_id, role, suspended FROM public.users WHERE id = sub`.
  4. Popula `AuthenticatedUser` (injetado nos controllers via `@RequestAttribute`).
- Sem token → **401** `missing_auth_header`. Token válido sem linha em `users` (e não no fluxo de
  convite) → **403** `user_not_provisioned`.

### Paths que o filtro protege

O `JwtAuthenticationFilter` exige token em `/admin/**` e em `/api/{nicho}/**` (todos os 34 nichos:
`/api/sushi/**`, `/api/legal/**`, … `/api/suplementos/**`). Ficam **fora** da exigência de token:
`/webhooks/**` (tem o próprio filtro de secret), `/public/**` (CMS público) e
`/api/invitations/{token}` + `/api/invitations/{token}/accept` (fluxo INVITEE).

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
| `companies` | O tenant. `id, slug, status ('active'/'suspended'), profile_id` (NOT NULL, CHECK nos 34 perfis). |
| `users` | Operadores do painel. `id (FK auth.users), company_id, role, suspended, deleted_at`. RLS por `company_id`. |
| `whatsapp_instances` | Instâncias Evolution por tenant. `evolution_token` é coluna blindada (grant restrito). |

## Suspensão e LGPD

- **Usuário suspenso:** coluna `suspended` em `users` → filtro retorna **403**.
- **Empresa suspensa:** `companies.status = 'suspended'` → **403**.
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
