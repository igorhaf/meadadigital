-- =============================================================================
-- 26_admin_operacao.sql
-- Meada — Camada 6 (sub-maratona 1: Operação). Migration única consolidada
-- das fases 6.1 (empresas), 6.2 (usuários/convites) e 6.5 (auditoria/ações admin).
--
-- TABELAS NOVAS:
--   admin_action_log — toda ação destrutiva/sensível do super-admin (suspender, editar,
--     excluir empresa/usuário, revogar convite, notas). Diferente do audit_log (mutações
--     do TENANT via trigger) — este é o rastro do SUPER-ADMIN sobre a plataforma.
--   admin_notes — anotações internas do super-admin por empresa (NUNCA visíveis ao tenant).
--
-- COLUNAS NOVAS:
--   users: suspended/suspended_at/suspended_reason (suspensão reversível — #6.2),
--          last_login_at (gravado pelo JwtAuthenticationFilter, throttle 5min),
--          deleted_at (soft delete de usuário — #6.2; hard delete fica pra LGPD).
--   tenant_invitations: revoked_at (revogação pelo super-admin — #6.2; estado distinto
--          de "expirado"). O status exibido é derivado: used_at→aceito, revoked_at→
--          revogado, expires_at<now→expirado, senão pendente.
--   companies.status já aceita 'suspended' (CHECK existente) — sem alteração.
--
-- RLS: admin_action_log e admin_notes são acessadas SÓ pelo backend (service_role) — o
--   super-admin opera via Spring, fora do RLS. NÃO há policy para 'authenticated'
--   (nenhum tenant lê/escreve). Habilitamos RLS + force como defesa em profundidade
--   (sem policy = ninguém via SDK, só service_role/BYPASSRLS).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- admin_action_log — rastro de ações do super-admin
-- ---------------------------------------------------------------------------
create table public.admin_action_log (
  id                  uuid        primary key default gen_random_uuid(),
  super_admin_user_id uuid        not null,
  action              text        not null,
  target_type         text        not null,
  target_id           uuid,
  payload             jsonb       not null default '{}'::jsonb,
  created_at          timestamptz not null default now()
);

comment on table public.admin_action_log is
  'Rastro de ações do super-admin sobre a plataforma (camada 6). Escrito só via service_role (AdminActionLogger). super_admin_user_id = auth.users.id do super-admin (que NÃO tem linha em public.users, por isso sem FK).';

create index idx_admin_action_log_actor on public.admin_action_log (super_admin_user_id, created_at desc);
create index idx_admin_action_log_target on public.admin_action_log (target_type, target_id);
create index idx_admin_action_log_action on public.admin_action_log (action, created_at desc);

alter table public.admin_action_log enable row level security;
alter table public.admin_action_log force  row level security;
grant all on public.admin_action_log to service_role;

-- ---------------------------------------------------------------------------
-- admin_notes — notas internas por empresa (só super-admin)
-- ---------------------------------------------------------------------------
create table public.admin_notes (
  id                  uuid        primary key default gen_random_uuid(),
  company_id          uuid        not null references public.companies(id) on delete cascade,
  super_admin_user_id uuid        not null,
  content             text        not null check (length(trim(content)) between 1 and 5000),
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now()
);

comment on table public.admin_notes is
  'Notas internas do super-admin por empresa (camada 6.1). NUNCA visíveis ao tenant — sem policy authenticated, só service_role.';

create index idx_admin_notes_company on public.admin_notes (company_id, created_at desc);

alter table public.admin_notes enable row level security;
alter table public.admin_notes force  row level security;
grant all on public.admin_notes to service_role;

-- ---------------------------------------------------------------------------
-- users — suspensão, last_login, soft delete
-- ---------------------------------------------------------------------------
alter table public.users
  add column suspended        boolean     not null default false,
  add column suspended_at     timestamptz,
  add column suspended_reason text,
  add column last_login_at    timestamptz,
  add column deleted_at       timestamptz;

-- ---------------------------------------------------------------------------
-- tenant_invitations — revogação pelo super-admin (estado distinto de expirado)
-- ---------------------------------------------------------------------------
alter table public.tenant_invitations
  add column revoked_at timestamptz;
