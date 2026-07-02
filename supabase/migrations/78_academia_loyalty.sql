-- =============================================================================
-- 78_academia_loyalty.sql
-- Meada — Academia (camada 7.7): FIDELIDADE POR ASSIDUIDADE (pontos por check-in → recompensa).
--
-- Feature de RETENÇÃO (backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md #12): premia quem frequenta.
-- A cada check-in (feature #4) o backend credita `points_per_checkin` pontos ao contato; ao atingir
-- `reward_threshold` pontos, o aluno faz jus à recompensa (`reward_text`, texto livre descritivo). A
-- concessão real da recompensa é operação manual do tenant — aqui entra apenas o SALDO e a config.
-- SEM cashback, SEM crédito financeiro, SEM gateway.
--
-- Convenções (padrão das migrations 36/72/76):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - academia_loyalty_config: 1:1 com company (espelha academia_config). Ausente → defaults abaixo
--     (enabled=false: fidelidade é opt-in explícito do tenant). Tenant faz SELECT/INSERT/UPDATE.
--   - academia_loyalty: saldo por (company_id, contact_id). O crédito de pontos é BACKEND
--     (service_role) — a partir do check-in; a IA nunca credita/gasta pontos. Tenant só SELECT.
--   - Trava academia: pontos são dado ADMINISTRATIVO de engajamento; a IA nunca prescreve treino/
--     dieta/avaliação nem promete resultado — fidelidade só conta presença.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- academia_loyalty_config — política de fidelidade por tenant (1:1 com company)
-- ---------------------------------------------------------------------------
create table public.academia_loyalty_config (
  company_id        uuid        primary key references public.companies(id) on delete cascade,
  enabled           boolean     not null default false,
  points_per_checkin integer    not null default 1 check (points_per_checkin >= 1),
  reward_threshold  integer     check (reward_threshold is null or reward_threshold >= 1),
  reward_text       text,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.academia_loyalty_config is
  'Config de fidelidade por assiduidade do tenant academia (camada 7.7, feature #12). 1:1 com company. enabled=false por padrão (opt-in). points_per_checkin = pontos creditados a cada check-in; reward_threshold = pontos p/ recompensa; reward_text = descrição livre da recompensa.';

alter table public.academia_loyalty_config enable row level security;
alter table public.academia_loyalty_config force  row level security;

create policy academia_loyalty_config_select on public.academia_loyalty_config
  for select to authenticated using (company_id = app.company_id());
create policy academia_loyalty_config_insert on public.academia_loyalty_config
  for insert to authenticated with check (company_id = app.company_id());
create policy academia_loyalty_config_update on public.academia_loyalty_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.academia_loyalty_config to authenticated;
grant all on public.academia_loyalty_config to service_role;

-- ---------------------------------------------------------------------------
-- academia_loyalty — saldo de pontos por contato
-- ---------------------------------------------------------------------------
create table public.academia_loyalty (
  company_id uuid        not null references public.companies(id) on delete restrict,
  contact_id uuid        not null references public.contacts(id) on delete cascade,
  points     integer     not null default 0 check (points >= 0),
  updated_at timestamptz not null default now(),
  primary key (company_id, contact_id)
);

comment on table public.academia_loyalty is
  'Saldo de pontos de fidelidade por contato do tenant academia (camada 7.7, feature #12). PK (company_id, contact_id). Crédito é BACKEND (service_role) via check-in; a IA nunca credita/gasta. Tenant só lê.';

alter table public.academia_loyalty enable row level security;
alter table public.academia_loyalty force  row level security;

create policy academia_loyalty_select on public.academia_loyalty
  for select to authenticated using (company_id = app.company_id());

grant select on public.academia_loyalty to authenticated;
grant all on public.academia_loyalty to service_role;
