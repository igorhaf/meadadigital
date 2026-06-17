-- =============================================================================
-- 37_pet.sql
-- Meada WhatsApp — Camada 7.8 (SM-I: perfil Pet / PetBot). Oitavo perfil vertical real e
-- ÚLTIMO da fila planejada — fecha o catálogo de 8 perfis. Tabelas exclusivas do perfil
-- 'pet': profissionais, serviços (com restrição de espécie), config, animais (sub-entidade
-- do tutor) e agendamentos.
--
-- EVOLUÇÃO ESTRUTURAL: paralelo do salon (catálogo professional+service, conflito por
-- profissional, slot de horas), com a NOVIDADE central: pet_animals é SUB-ENTIDADE do
-- contact (tutor). Primeira SM com sub-entidade de cliente persistente entre conversas —
-- cada tutor (contact) pode ter N animais; cada agendamento referencia 1 animal. O
-- service.species_restriction valida o fit serviço↔animal.
--
-- Convenções (padrão das migrations 30-36):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - pet_appointments: INSERT pelo BACKEND (service_role). Tenant só SELECT/UPDATE.
--   - end_at materializado no INSERT (start_at + duration_minutes); NÃO coluna gerada
--     (timestamptz+interval não é IMMUTABLE — lição SM-D/E/F).
--   - SNAPSHOTS no appointment: tutor_name/phone + animal_name/species + professional_name +
--     service_name/category + price + duration. Mudar prof/serviço/animal depois NÃO altera
--     agendamentos passados.
--   - Tutor NÃO é entidade própria (continua o contact). pet_animals.contact_id é a verdade.
--   - species: 'cao'|'gato'|'outro' (CHECK hardcoded simples). sex: 'macho'|'femea'|'desconhecido'.
--   - LGPD: notes (animal e appointment) é administrativo, SEM prontuário clínico.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'pet' (8º perfil real; 9º contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada','academia','pet'));

-- ---------------------------------------------------------------------------
-- pet_professionals — veterinários/banhistas/tosadores (catálogo)
-- ---------------------------------------------------------------------------
create table public.pet_professionals (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "veterinário", "banhista", "tosador" (texto livre)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.pet_professionals is
  'Profissionais do tenant pet (camada 7.8). Conflito de agenda é por profissional. active=false retira da disponibilidade da IA.';

create index idx_pet_prof_company_active on public.pet_professionals (company_id, active) where active = true;
create index idx_pet_prof_company_name on public.pet_professionals (company_id, name);

alter table public.pet_professionals enable row level security;
alter table public.pet_professionals force  row level security;

create policy pet_prof_select on public.pet_professionals for select to authenticated using (company_id = app.company_id());
create policy pet_prof_insert on public.pet_professionals for insert to authenticated with check (company_id = app.company_id());
create policy pet_prof_update on public.pet_professionals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy pet_prof_delete on public.pet_professionals for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.pet_professionals to authenticated;
grant all on public.pet_professionals to service_role;

-- ---------------------------------------------------------------------------
-- pet_services — serviços (banho/tosa/consulta/vacinação) com restrição de espécie opcional
-- ---------------------------------------------------------------------------
create table public.pet_services (
  id                   uuid        primary key default gen_random_uuid(),
  company_id           uuid        not null references public.companies(id) on delete restrict,
  name                 text        not null check (length(trim(name)) between 1 and 200),
  category             text,        -- "banho", "tosa", "consulta", "vacinação" (texto livre)
  duration_minutes     integer     not null check (duration_minutes between 15 and 240),
  price_cents          integer,     -- nullable
  species_restriction  text        check (species_restriction in ('cao','gato','outro')),  -- NULL = qualquer espécie
  active               boolean     not null default true,
  description          text,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

comment on table public.pet_services is
  'Serviços do tenant pet (camada 7.8). species_restriction (NULL=qualquer) valida o fit serviço↔animal no agendamento. duration_minutes entra como snapshot.';

create index idx_pet_services_company_active on public.pet_services (company_id, active) where active = true;
create index idx_pet_services_company_cat on public.pet_services (company_id, category);

alter table public.pet_services enable row level security;
alter table public.pet_services force  row level security;

create policy pet_services_select on public.pet_services for select to authenticated using (company_id = app.company_id());
create policy pet_services_insert on public.pet_services for insert to authenticated with check (company_id = app.company_id());
create policy pet_services_update on public.pet_services for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy pet_services_delete on public.pet_services for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.pet_services to authenticated;
grant all on public.pet_services to service_role;

-- ---------------------------------------------------------------------------
-- pet_config — horário de funcionamento (1:1 com company)
-- ---------------------------------------------------------------------------
create table public.pet_config (
  company_id     uuid        primary key references public.companies(id) on delete cascade,
  opens_at       time        not null default '09:00',
  closes_at      time        not null default '19:00',
  buffer_minutes integer     not null default 0 check (buffer_minutes >= 0),
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

comment on table public.pet_config is
  'Config do tenant pet (camada 7.8): janela de funcionamento + buffer. 1:1 com company. Ausente → defaults (09:00/19:00/0).';

alter table public.pet_config enable row level security;
alter table public.pet_config force  row level security;

create policy pet_config_select on public.pet_config for select to authenticated using (company_id = app.company_id());
create policy pet_config_insert on public.pet_config for insert to authenticated with check (company_id = app.company_id());
create policy pet_config_update on public.pet_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.pet_config to authenticated;
grant all on public.pet_config to service_role;

-- ---------------------------------------------------------------------------
-- pet_animals — animais (SUB-ENTIDADE do tutor/contact)
-- ---------------------------------------------------------------------------
create table public.pet_animals (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  contact_id  uuid        not null references public.contacts(id) on delete restrict,  -- TUTOR
  name        text        not null check (length(trim(name)) between 1 and 100),
  species     text        not null check (species in ('cao','gato','outro')),
  breed       text,        -- "Golden Retriever", "SRD" (texto livre)
  sex         text        not null default 'desconhecido' check (sex in ('macho','femea','desconhecido')),
  birth_year  integer      check (birth_year between 1990 and 2030),
  notes       text,        -- administrativo (LGPD)
  active      boolean     not null default true,  -- false = arquivado (não perde histórico)
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.pet_animals is
  'Animais do tenant pet (camada 7.8). SUB-ENTIDADE do contact (tutor) — persiste entre conversas. active=false arquiva sem perder histórico de agendamentos. species/sex hardcoded. notes administrativo, SEM dado clínico.';

create index idx_pet_animals_company_contact_active on public.pet_animals (company_id, contact_id, active) where active = true;
create index idx_pet_animals_company_name on public.pet_animals (company_id, name);

alter table public.pet_animals enable row level security;
alter table public.pet_animals force  row level security;

create policy pet_animals_select on public.pet_animals for select to authenticated using (company_id = app.company_id());
create policy pet_animals_insert on public.pet_animals for insert to authenticated with check (company_id = app.company_id());
create policy pet_animals_update on public.pet_animals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy pet_animals_delete on public.pet_animals for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.pet_animals to authenticated;
grant all on public.pet_animals to service_role;

-- ---------------------------------------------------------------------------
-- pet_appointments — agendamentos (snapshots de tutor/animal/profissional/serviço)
-- ---------------------------------------------------------------------------
create table public.pet_appointments (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  professional_id   uuid        not null references public.pet_professionals(id) on delete restrict,
  service_id        uuid        not null references public.pet_services(id) on delete restrict,
  animal_id         uuid        not null references public.pet_animals(id) on delete restrict,
  contact_id        uuid        references public.contacts(id) on delete set null,   -- tutor (snapshot/atalho)
  conversation_id   uuid        references public.conversations(id) on delete set null,
  tutor_name        text        not null,   -- snapshot do contact
  tutor_phone       text,                   -- snapshot opcional
  animal_name       text        not null,   -- snapshot
  animal_species    text        not null,   -- snapshot
  professional_name text        not null,   -- snapshot
  service_name      text        not null,   -- snapshot
  service_category  text,                   -- snapshot
  price_cents       integer,                -- snapshot opcional
  duration_minutes  integer     not null,   -- snapshot
  start_at          timestamptz not null,
  end_at            timestamptz not null,   -- materializado no INSERT
  status            text        not null default 'agendado' check (status in
                      ('agendado','confirmado','realizado','cancelado','falta')),
  notes             text,
  created_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.pet_appointments is
  'Agendamentos do tenant pet (camada 7.8). INSERT pelo backend (service_role). Conflito por professional_id. Snapshots completos de tutor/animal/profissional/serviço.';

create index idx_pet_appts_company_status_start on public.pet_appointments (company_id, status, start_at);
-- Índice CRÍTICO do conflito: por PROFISSIONAL, só status bloqueantes.
create index idx_pet_appts_prof_active on public.pet_appointments (professional_id, start_at)
  where status in ('agendado','confirmado');
create index idx_pet_appts_animal on public.pet_appointments (animal_id, start_at desc);
create index idx_pet_appts_contact on public.pet_appointments (contact_id, start_at desc);

alter table public.pet_appointments enable row level security;
alter table public.pet_appointments force  row level security;

create policy pet_appts_select on public.pet_appointments for select to authenticated using (company_id = app.company_id());
create policy pet_appts_update on public.pet_appointments for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.pet_appointments to authenticated;
grant all on public.pet_appointments to service_role;
