-- =============================================================================
-- 36_academia.sql
-- Meada WhatsApp — Camada 7.7 (SM-H: perfil Academia / AcademiaBot). Sétimo perfil vertical
-- real. Tabelas exclusivas do perfil 'academia': planos, aulas semanais, config, matrículas
-- (assinaturas), junction matrícula↔aulas, e pagamentos manuais.
--
-- EVOLUÇÃO ESTRUTURAL MAIS PROFUNDA: a matrícula é uma RECORRÊNCIA INDEFINIDA (assinatura
-- ativa-até-cancelar), não um evento pontual (slot) nem um intervalo finito (pousada). Uma
-- matrícula ocupa N vagas em N aulas semanais recorrentes. O conflito é por CAPACITY por aula
-- (capacity - count(matrículas ativas naquela aula) > 0), não por overlap temporal. Pagamento é
-- manual (Stripe é fase futura #50).
--
-- Convenções (padrão das migrations 30-35):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - academia_memberships: INSERT pelo BACKEND (service_role). Tenant só SELECT/UPDATE.
--   - SNAPSHOTS: a matrícula congela plan_name + plan_monthly_cents + student_name/phone; a
--     junction congela class_name + day_of_week + start_time + duration + modality. Mudar o
--     plano/aula depois NÃO altera matrículas existentes.
--   - Aluno NÃO é entidade própria (igual salon/pousada — alta rotatividade). Histórico via
--     contact + memberships.
--   - day_of_week: 0=domingo .. 6=sábado.
--   - LGPD: notes é administrativo, sem dado de saúde do aluno.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'academia' (7º perfil real; 8º contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada','academia'));

-- ---------------------------------------------------------------------------
-- academia_plans — planos mensais
-- ---------------------------------------------------------------------------
create table public.academia_plans (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  name          text        not null check (length(trim(name)) between 1 and 200),
  monthly_cents integer     not null check (monthly_cents >= 0),
  description   text,
  active        boolean     not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.academia_plans is
  'Planos mensais do tenant academia (camada 7.7). monthly_cents em centavos. Entra como SNAPSHOT na matrícula.';

create index idx_academia_plans_company_active on public.academia_plans (company_id, active)
  where active = true;

alter table public.academia_plans enable row level security;
alter table public.academia_plans force  row level security;

create policy academia_plans_select on public.academia_plans
  for select to authenticated using (company_id = app.company_id());
create policy academia_plans_insert on public.academia_plans
  for insert to authenticated with check (company_id = app.company_id());
create policy academia_plans_update on public.academia_plans
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy academia_plans_delete on public.academia_plans
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.academia_plans to authenticated;
grant all on public.academia_plans to service_role;

-- ---------------------------------------------------------------------------
-- academia_classes — aulas semanais recorrentes
-- ---------------------------------------------------------------------------
create table public.academia_classes (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  name             text        not null check (length(trim(name)) between 1 and 200),
  modality         text        not null check (length(trim(modality)) between 1 and 100),
  day_of_week      integer     not null check (day_of_week between 0 and 6),  -- 0=domingo
  start_time       time        not null,
  duration_minutes integer     not null check (duration_minutes between 15 and 240),
  capacity         integer     not null check (capacity between 1 and 100),
  instructor       text,
  active           boolean     not null default true,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.academia_classes is
  'Aulas semanais recorrentes do tenant academia (camada 7.7). capacity = máx. de matrículas ativas. day_of_week 0=domingo. Entra como SNAPSHOT na junction.';

create index idx_academia_classes_company_active_day on public.academia_classes (company_id, active, day_of_week)
  where active = true;

alter table public.academia_classes enable row level security;
alter table public.academia_classes force  row level security;

create policy academia_classes_select on public.academia_classes
  for select to authenticated using (company_id = app.company_id());
create policy academia_classes_insert on public.academia_classes
  for insert to authenticated with check (company_id = app.company_id());
create policy academia_classes_update on public.academia_classes
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy academia_classes_delete on public.academia_classes
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.academia_classes to authenticated;
grant all on public.academia_classes to service_role;

-- ---------------------------------------------------------------------------
-- academia_config — horário de funcionamento (1:1 com company)
-- ---------------------------------------------------------------------------
create table public.academia_config (
  company_id uuid        primary key references public.companies(id) on delete cascade,
  opens_at   time        not null default '06:00',
  closes_at  time        not null default '22:00',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.academia_config is
  'Config do tenant academia (camada 7.7): horário de funcionamento. 1:1 com company. Ausente → defaults (06:00/22:00).';

alter table public.academia_config enable row level security;
alter table public.academia_config force  row level security;

create policy academia_config_select on public.academia_config
  for select to authenticated using (company_id = app.company_id());
create policy academia_config_insert on public.academia_config
  for insert to authenticated with check (company_id = app.company_id());
create policy academia_config_update on public.academia_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.academia_config to authenticated;
grant all on public.academia_config to service_role;

-- ---------------------------------------------------------------------------
-- academia_memberships — matrículas (assinaturas)
-- ---------------------------------------------------------------------------
create table public.academia_memberships (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  plan_id            uuid        not null references public.academia_plans(id) on delete restrict,
  conversation_id    uuid        references public.conversations(id) on delete set null,
  contact_id         uuid        references public.contacts(id) on delete set null,
  student_name       text        not null,   -- snapshot
  student_phone      text,                   -- snapshot opcional
  plan_name          text        not null,   -- snapshot
  plan_monthly_cents integer     not null,   -- snapshot
  start_date         date        not null default current_date,
  end_date           date,                   -- materializado em cancelada
  status             text        not null default 'ativa' check (status in ('ativa','suspensa','cancelada')),
  notes              text,
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.academia_memberships is
  'Matrículas (assinaturas) do tenant academia (camada 7.7). RECORRÊNCIA INDEFINIDA: ativa-até-cancelar. end_date só em cancelada. snapshots de plano + student. Aluno não é entidade.';

create index idx_academia_memberships_company_status on public.academia_memberships (company_id, status, start_date desc);
create index idx_academia_memberships_contact on public.academia_memberships (contact_id, start_date desc)
  where contact_id is not null;
-- Impede 2 matrículas ATIVAS para o mesmo contato (defesa contra dupla matrícula via IA).
create unique index uniq_active_membership_per_contact on public.academia_memberships (company_id, contact_id)
  where status = 'ativa' and contact_id is not null;

alter table public.academia_memberships enable row level security;
alter table public.academia_memberships force  row level security;

create policy academia_memberships_select on public.academia_memberships
  for select to authenticated using (company_id = app.company_id());
create policy academia_memberships_update on public.academia_memberships
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.academia_memberships to authenticated;
grant all on public.academia_memberships to service_role;

-- ---------------------------------------------------------------------------
-- academia_membership_classes — junction matrícula ↔ aulas (com snapshot)
-- ---------------------------------------------------------------------------
create table public.academia_membership_classes (
  membership_id                  uuid    not null references public.academia_memberships(id) on delete cascade,
  class_id                       uuid    not null references public.academia_classes(id) on delete restrict,
  class_name_snapshot            text    not null,
  class_day_of_week_snapshot     integer not null,
  class_start_time_snapshot      time    not null,
  class_duration_minutes_snapshot integer not null,
  class_modality_snapshot        text    not null,
  created_at                     timestamptz not null default now(),
  primary key (membership_id, class_id)
);

comment on table public.academia_membership_classes is
  'Junction matrícula↔aula (camada 7.7) com SNAPSHOT da aula. A vaga é contada por class_id sobre matrículas ATIVAS. SELECT do tenant via JOIN com a matrícula (sem RLS direto na junction).';

create index idx_academia_mc_class on public.academia_membership_classes (class_id);

alter table public.academia_membership_classes enable row level security;
alter table public.academia_membership_classes force  row level security;

-- SELECT/DELETE via EXISTS na matrícula do tenant; INSERT só service_role (backend).
create policy academia_mc_select on public.academia_membership_classes
  for select to authenticated using (
    exists (select 1 from public.academia_memberships m
            where m.id = membership_id and m.company_id = app.company_id()));

grant select on public.academia_membership_classes to authenticated;
grant all on public.academia_membership_classes to service_role;

-- ---------------------------------------------------------------------------
-- academia_payments — pagamentos manuais mensais
-- ---------------------------------------------------------------------------
create table public.academia_payments (
  id              uuid        primary key default gen_random_uuid(),
  company_id      uuid        not null references public.companies(id) on delete restrict,
  membership_id   uuid        not null references public.academia_memberships(id) on delete restrict,
  reference_month date        not null,   -- sempre dia 01 do mês de referência
  paid_at         timestamptz not null default now(),
  amount_cents    integer     not null check (amount_cents >= 0),
  method          text,                   -- "dinheiro", "Pix", "transferência"
  notes           text,
  created_at      timestamptz not null default now(),
  unique (membership_id, reference_month)
);

comment on table public.academia_payments is
  'Pagamentos manuais mensais do tenant academia (camada 7.7). UNIQUE (membership, reference_month) impede duplicidade no mês. SEM cobrança automática (Stripe é #50, futuro).';

create index idx_academia_payments_company_month on public.academia_payments (company_id, reference_month desc);

alter table public.academia_payments enable row level security;
alter table public.academia_payments force  row level security;

create policy academia_payments_select on public.academia_payments
  for select to authenticated using (company_id = app.company_id());

grant select on public.academia_payments to authenticated;
grant all on public.academia_payments to service_role;
