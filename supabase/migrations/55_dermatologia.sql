-- =============================================================================
-- 55_dermatologia.sql
-- Meada WhatsApp — Camada 8.11 (SM: perfil Dermatologia / clínica dermatológica). Tabelas exclusivas
-- do perfil 'dermatologia': dermatologistas, config, tipos de atendimento (com duração + preparo),
-- pacientes (sub-entidade do contact) e consultas.
--
-- CLONA o chassi de AGENDA do DENTAL/NUTRI: profissional + conflito POR professional_id (half-open,
-- re-verificado na transação) + end_at MATERIALIZADO no INSERT (start_at + duration_minutes; NÃO
-- coluna gerada — timestamptz+interval não é IMMUTABLE) + paciente sub-entidade do contact. UMA
-- escapada NOVA:
--
--   ESCAPADA — TIPOS DE ATENDIMENTO COM DURAÇÃO E PREPARO PRÓPRIOS (dermatologia_procedure_types):
--     dental/nutri tinham appointment_type enum com duração FIXA do config. Aqui o tenant CADASTRA os
--     tipos (consulta 30min, botox 60min…), cada um com SUA duration_minutes e, opcionalmente, uma NOTA
--     DE PREPARO (prep_instructions — orientação pré-procedimento gravada pelo médico). A consulta
--     referencia procedure_type_id e SNAPSHOTA name+duration_minutes. A nota de preparo é entregue
--     READ-ONLY pela IA (verbatim, espelho EXATO da entrega de plano do nutri).
--
-- TRAVA CLÍNICA (na persona + schema): a IA NUNCA diagnostica, avalia lesão/mancha/pinta/foto,
-- recomenda tratamento/medicação/procedimento, opina "é grave/é câncer". O ÚNICO conteúdo clínico que
-- a IA "entrega" é a nota de preparo READ-ONLY. LGPD: notes (paciente/consulta) e prep_instructions são
-- ADMINISTRATIVOS, não prontuário.
--
-- Convenções: RLS enable+force; policies via app.company_id(); grants authenticated + service_role.
-- appointments: INSERT pelo backend (service_role) — IA via handler OU POST manual; tenant SELECT/UPDATE.
-- Demais: CRUD do tenant. end_at materializado no INSERT. SNAPSHOTS de paciente/profissional/tipo na
-- consulta — alterar o tipo NÃO altera consultas já criadas. Status feminino (consulta) hardcoded em
-- sync com DermatologiaAppointmentStatus.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'dermatologia' (24º contando generic). ESPELHA a CHECK mais completa
-- (54_lavanderia, 23) e APENDA. Entra por ÚLTIMO no SCRIPTS de teste.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia'));

-- ---------------------------------------------------------------------------
-- dermatologia_professionals — dermatologistas (conflito de agenda POR profissional).
-- ---------------------------------------------------------------------------
create table public.dermatologia_professionals (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "dermatologia clínica", "dermatologia estética", "tricologia"
  crm_rqe     text,        -- registro profissional
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.dermatologia_professionals is
  'Dermatologistas do tenant dermatologia (camada 8.11). Conflito de agenda da consulta é POR professional_id (paralelismo). active=false arquiva; delete em uso → 409 professional_in_use.';

create index idx_derma_prof_company_active on public.dermatologia_professionals (company_id, active) where active = true;

alter table public.dermatologia_professionals enable row level security;
alter table public.dermatologia_professionals force  row level security;

create policy derma_prof_select on public.dermatologia_professionals for select to authenticated using (company_id = app.company_id());
create policy derma_prof_insert on public.dermatologia_professionals for insert to authenticated with check (company_id = app.company_id());
create policy derma_prof_update on public.dermatologia_professionals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy derma_prof_delete on public.dermatologia_professionals for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.dermatologia_professionals to authenticated;
grant all on public.dermatologia_professionals to service_role;

-- ---------------------------------------------------------------------------
-- dermatologia_config — horário de funcionamento (1:1 com company). SEM duration (vem do tipo).
-- ---------------------------------------------------------------------------
create table public.dermatologia_config (
  company_id     uuid        primary key references public.companies(id) on delete cascade,
  opens_at       time        not null default '08:00',
  closes_at      time        not null default '18:00',
  buffer_minutes integer     not null default 0 check (buffer_minutes >= 0),
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

comment on table public.dermatologia_config is
  'Config do tenant dermatologia (camada 8.11): horário de funcionamento. 1:1 com company. Ausente → defaults (08:00/18:00/0). SEM duration aqui — a duração vem do procedure_type (escapada).';

alter table public.dermatologia_config enable row level security;
alter table public.dermatologia_config force  row level security;

create policy derma_config_select on public.dermatologia_config for select to authenticated using (company_id = app.company_id());
create policy derma_config_insert on public.dermatologia_config for insert to authenticated with check (company_id = app.company_id());
create policy derma_config_update on public.dermatologia_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.dermatologia_config to authenticated;
grant all on public.dermatologia_config to service_role;

-- ---------------------------------------------------------------------------
-- dermatologia_procedure_types — A ENTIDADE NOVA (escapada): tipos de atendimento com duração + preparo.
-- ---------------------------------------------------------------------------
create table public.dermatologia_procedure_types (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  name               text        not null check (length(trim(name)) between 1 and 120),  -- "Consulta", "Botox"
  duration_minutes   integer     not null check (duration_minutes between 5 and 480),
  prep_instructions  text,        -- nota de preparo READ-ONLY (entregue VERBATIM pela IA; vazio = sem preparo)
  active             boolean     not null default true,
  notes              text,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.dermatologia_procedure_types is
  'Tipos de atendimento do tenant dermatologia (camada 8.11, ESCAPADA). Duração POR TIPO (≠ config fixo do dental). prep_instructions é orientação PRÉ-procedimento entregue VERBATIM pela IA (espelho do body do plano do nutri), NÃO é prontuário (LGPD administrativo). A consulta SNAPSHOTA name+duration. delete em uso → 409 procedure_type_in_use.';

create index idx_derma_ptype_company_active on public.dermatologia_procedure_types (company_id, active) where active = true;

alter table public.dermatologia_procedure_types enable row level security;
alter table public.dermatologia_procedure_types force  row level security;

create policy derma_ptype_select on public.dermatologia_procedure_types for select to authenticated using (company_id = app.company_id());
create policy derma_ptype_insert on public.dermatologia_procedure_types for insert to authenticated with check (company_id = app.company_id());
create policy derma_ptype_update on public.dermatologia_procedure_types for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy derma_ptype_delete on public.dermatologia_procedure_types for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.dermatologia_procedure_types to authenticated;
grant all on public.dermatologia_procedure_types to service_role;

-- ---------------------------------------------------------------------------
-- dermatologia_patients — pacientes (SUB-ENTIDADE do contact — espelho nutri_patients).
-- ---------------------------------------------------------------------------
create table public.dermatologia_patients (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  contact_id  uuid        not null references public.contacts(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 120),
  birth_date  date,
  notes       text,        -- ADMINISTRATIVO (não clínico — LGPD)
  active      boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.dermatologia_patients is
  'Pacientes do tenant dermatologia (camada 8.11). SUB-ENTIDADE do contact (espelho nutri_patients). active=false arquiva. notes ADMINISTRATIVO, sem prontuário/diagnóstico (LGPD). delete em uso → 409 patient_in_use.';

create index idx_derma_pat_company_contact_active on public.dermatologia_patients (company_id, contact_id, active) where active = true;
create index idx_derma_pat_company_name on public.dermatologia_patients (company_id, name);

alter table public.dermatologia_patients enable row level security;
alter table public.dermatologia_patients force  row level security;

create policy derma_pat_select on public.dermatologia_patients for select to authenticated using (company_id = app.company_id());
create policy derma_pat_insert on public.dermatologia_patients for insert to authenticated with check (company_id = app.company_id());
create policy derma_pat_update on public.dermatologia_patients for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy derma_pat_delete on public.dermatologia_patients for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.dermatologia_patients to authenticated;
grant all on public.dermatologia_patients to service_role;

-- ---------------------------------------------------------------------------
-- dermatologia_appointments — consultas (snapshots; conflito POR profissional; espelho nutri_appointments).
-- ---------------------------------------------------------------------------
create table public.dermatologia_appointments (
  id                    uuid        primary key default gen_random_uuid(),
  company_id            uuid        not null references public.companies(id) on delete restrict,
  professional_id       uuid        not null references public.dermatologia_professionals(id) on delete restrict,
  patient_id            uuid        not null references public.dermatologia_patients(id) on delete restrict,
  procedure_type_id     uuid        not null references public.dermatologia_procedure_types(id) on delete restrict,
  contact_id            uuid        references public.contacts(id) on delete set null,
  conversation_id       uuid        references public.conversations(id) on delete set null,
  patient_name          text        not null,   -- snapshot
  patient_phone         text,                   -- snapshot
  professional_name     text        not null,   -- snapshot
  procedure_type_name   text        not null,   -- snapshot
  duration_minutes      integer     not null,   -- snapshot do tipo
  start_at              timestamptz not null,
  end_at                timestamptz not null,   -- MATERIALIZADO no INSERT (start_at + duration_minutes)
  status                text        not null default 'agendada' check (status in
                          ('agendada','confirmada','realizada','cancelada','falta')),
  notes                 text,        -- ADMINISTRATIVO
  created_at            timestamptz not null default now(),
  status_updated_at     timestamptz not null default now()
);

comment on table public.dermatologia_appointments is
  'Consultas do tenant dermatologia (camada 8.11). INSERT pelo backend (service_role) — IA (AgendamentoDermaConfirmHandler) ou tenant. Clone nutri_appointments. Conflito POR professional_id. end_at MATERIALIZADO. SNAPSHOTS de paciente/profissional/tipo+duração. Status feminino. notes ADMINISTRATIVO.';

create index idx_derma_appts_company_status_start on public.dermatologia_appointments (company_id, status, start_at);
create index idx_derma_appts_company_prof_active on public.dermatologia_appointments (company_id, professional_id, start_at)
  where status in ('agendada','confirmada');
create index idx_derma_appts_patient on public.dermatologia_appointments (patient_id, start_at desc);
create index idx_derma_appts_contact on public.dermatologia_appointments (contact_id, start_at desc);

alter table public.dermatologia_appointments enable row level security;
alter table public.dermatologia_appointments force  row level security;

create policy derma_appts_select on public.dermatologia_appointments for select to authenticated using (company_id = app.company_id());
create policy derma_appts_update on public.dermatologia_appointments for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.dermatologia_appointments to authenticated;
grant all on public.dermatologia_appointments to service_role;
