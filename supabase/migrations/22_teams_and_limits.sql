-- =============================================================================
-- 22_teams_and_limits.sql
-- Meada — Camada 5.20 (Fase D): times (#76) + limites por plano (#77).
--
-- #76 TIMES/DEPARTAMENTOS:
--   tabela teams (nome por empresa) + conversations.team_id nullable. Sem hierarquia
--   entre times (decisão cravada). Atribuição manual de conversa a time no painel.
--
-- #77 LIMITES POR PLANO (infra, sem plano associado ainda — sem #50):
--   colunas em companies: max_admins, max_faqs, max_conversations_month (todas int
--   nullable = sem limite). O backend valida onde aplicável (InvitationService p/ admins,
--   FAQ create, etc.). null = ilimitado (default).
--
-- #78 (audit log visível) NÃO precisa de migration — a tabela audit_log e o trigger já
--   existem (fase-5.3); é só UI. Fica no mesmo commit lógico mas sem schema novo.
--
-- RLS por company_id (teams). Audit em teams.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- #77 — limites por plano em companies (null = ilimitado)
-- ---------------------------------------------------------------------------
alter table public.companies
  add column max_admins              int,
  add column max_faqs                int,
  add column max_conversations_month int;


-- ---------------------------------------------------------------------------
-- #76 — teams + conversations.team_id
-- ---------------------------------------------------------------------------
create table public.teams (
  id         uuid        primary key default gen_random_uuid(),
  company_id uuid        not null references public.companies(id) on delete cascade,
  name       text        not null check (length(trim(name)) between 1 and 60),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.teams is
  'Times/departamentos do tenant (camada 5.20 #76). Conversa pode ser atribuída a um time. Sem hierarquia.';

create index idx_teams_company on public.teams (company_id);

alter table public.conversations
  add column team_id uuid references public.teams(id) on delete set null;

alter table public.teams enable row level security;
alter table public.teams force  row level security;

create policy teams_select on public.teams
  for select to authenticated using (company_id = app.company_id());
create policy teams_insert on public.teams
  for insert to authenticated with check (company_id = app.company_id());
create policy teams_update on public.teams
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy teams_delete on public.teams
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.teams to authenticated;
grant all on public.teams to service_role;

create trigger trg_teams_audit after insert or update on public.teams
  for each row execute function app.audit_trigger();
