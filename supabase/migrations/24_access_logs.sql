-- =============================================================================
-- 24_access_logs.sql
-- Meada — Camada 5.24 (Fase H): logs de acesso (#92).
--
-- Registra eventos de AUTENTICAÇÃO (não cada request — volume): login_success,
-- login_failed, password_changed. Diferente do audit_log (mutações de dados de negócio),
-- access_logs é segurança/sessão. O backend escreve via service_role; o tenant lê os da
-- própria empresa (quando company_id resolvível).
--
-- company_id nullable: um login_failed pode não ter empresa resolvível (email
-- desconhecido). Nesses casos o registro existe para forense global (super-admin), mas
-- não aparece pra nenhum tenant (RLS exige company_id = app.company_id()).
--
-- #66 (comparação mês a mês) e #68 (top contatos) e #65 (export PDF) NÃO precisam de
-- schema — são query/RPC/render. Ficam nas suas fases sem migration.
-- =============================================================================

create table public.access_logs (
  id         uuid        primary key default gen_random_uuid(),
  company_id uuid        references public.companies(id) on delete cascade,
  user_id    uuid        references auth.users(id) on delete set null,
  email      text,
  action     text        not null check (action in
                ('login_success', 'login_failed', 'password_changed')),
  ip         text,
  user_agent text,
  created_at timestamptz not null default now()
);

comment on table public.access_logs is
  'Eventos de autenticação por tenant (camada 5.24 #92): login_success/failed, password_changed. Escrita service_role; leitura por tenant via RLS quando company_id presente.';

create index idx_access_logs_company_created on public.access_logs (company_id, created_at desc);

alter table public.access_logs enable row level security;
alter table public.access_logs force  row level security;

-- Tenant lê só os da própria empresa. Registros sem company_id (login_failed de email
-- desconhecido) não aparecem para nenhum tenant — só para o backend/super-admin.
create policy access_logs_select_own on public.access_logs
  for select to authenticated
  using (company_id is not null and company_id = app.company_id());

grant select on public.access_logs to authenticated;
grant all on public.access_logs to service_role;
