-- =============================================================================
-- 46_estetica.sql
-- Meada WhatsApp — Camada 8.3 (SM-Q: perfil Estética / EsteticaBot). DÉCIMO TERCEIRO perfil
-- vertical real (14º contando generic): clínica de estética (facial/corporal, drenagem, limpeza de
-- pele, depilação a laser…). Tabelas exclusivas do perfil 'estetica': profissionais, procedimentos,
-- config, PACOTES (saldo de sessões), agendamentos (que consomem saldo) e ficha/evolução por sessão.
--
-- EVOLUÇÃO ESTRUTURAL — combina 3 eixos + UMA escapada nova:
--   - (1) AGENDA POR PROFISSIONAL: clone do chassi do SALON (7.5). Conflito por professional_id
--     (2 clientes no mesmo horário com profissionais distintos NÃO conflitam). end_at materializado
--     no INSERT (timestamptz+interval não é IMMUTABLE — lição SM-D/E). Snapshots no agendamento.
--   - (2) ESCAPADA NOVA — PACOTE MULTI-SESSÃO COM SALDO QUE DECREMENTA (aesthetic_packages): o
--     cliente compra um pacote de N sessões de um procedimento; cada agendamento pode CONSUMIR 1
--     sessão. sessions_remaining é MATERIALIZADO (= total - used) e re-derivado na MESMA transação
--     a cada agendamento criado/cancelado. Primeiro nicho em que um agendamento mexe num contador
--     pré-pago de OUTRA entidade transacionalmente. Esgotar (remaining→0) muta o pacote pra
--     'esgotado'; cancelar um agendamento consumido DEVOLVE a sessão (esgotado→ativo).
--   - (3) FICHA/EVOLUÇÃO TEXTUAL POR SESSÃO (aesthetic_session_notes): sub-entidade 1:1 do
--     agendamento (área tratada, parâmetros do aparelho, observações — texto livre). SEM FOTO
--     (bloqueador SERVICE_ROLE_KEY). LGPD: registro administrativo-estético, NÃO prontuário médico.
--
-- Convenções (padrão das migrations 30-45):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - packages/appointments/session_notes: INSERT pelo BACKEND (service_role). Tenant SELECT/UPDATE.
--   - sessions_remaining + total_cents (pacote) e end_at (agendamento) MATERIALIZADOS; NÃO colunas
--     geradas (recálculo cruza linhas / timestamptz+interval não-IMMUTABLE — lições anteriores).
--   - SNAPSHOTS: pacote congela procedure_name/unit_price; agendamento congela professional_name/
--     procedure_name/duration_minutes. Cliente NÃO é entidade própria (continua o contact).
--   - A IA NÃO inventa preço: o total do pacote é total_sessions * unit_price do procedimento.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'estetica' (13º perfil real; 14º contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica'));

-- ---------------------------------------------------------------------------
-- aesthetic_professionals — profissionais (catálogo). Espelho salon_professionals.
-- ---------------------------------------------------------------------------
create table public.aesthetic_professionals (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "facial", "corporal", "laser" (texto livre)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.aesthetic_professionals is
  'Profissionais do tenant estetica (camada 8.3). Conflito de agenda é POR profissional. active=false retira da disponibilidade. Espelho salon_professionals.';

create index idx_aest_prof_company_active on public.aesthetic_professionals (company_id, active) where active = true;
create index idx_aest_prof_company_name on public.aesthetic_professionals (company_id, name);

alter table public.aesthetic_professionals enable row level security;
alter table public.aesthetic_professionals force  row level security;

create policy aest_prof_select on public.aesthetic_professionals for select to authenticated using (company_id = app.company_id());
create policy aest_prof_insert on public.aesthetic_professionals for insert to authenticated with check (company_id = app.company_id());
create policy aest_prof_update on public.aesthetic_professionals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy aest_prof_delete on public.aesthetic_professionals for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.aesthetic_professionals to authenticated;
grant all on public.aesthetic_professionals to service_role;

-- ---------------------------------------------------------------------------
-- aesthetic_procedures — catálogo de procedimentos. unit_price_cents = preço de UMA sessão.
-- ---------------------------------------------------------------------------
create table public.aesthetic_procedures (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  name             text        not null check (length(trim(name)) between 1 and 200),
  category         text,        -- "facial", "corporal", "depilação" (texto livre)
  duration_minutes integer     not null check (duration_minutes between 15 and 480),
  unit_price_cents integer     not null check (unit_price_cents >= 0),   -- preço de 1 sessão
  active           boolean     not null default true,
  notes            text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.aesthetic_procedures is
  'Procedimentos do tenant estetica (camada 8.3). duration_minutes + unit_price_cents (preço de UMA sessão). O preço do pacote é total_sessions * unit_price (a IA não inventa preço). Espelho salon_offerings + price obrigatório.';

create index idx_aest_proc_company_active on public.aesthetic_procedures (company_id, active) where active = true;
create index idx_aest_proc_company_cat on public.aesthetic_procedures (company_id, category);

alter table public.aesthetic_procedures enable row level security;
alter table public.aesthetic_procedures force  row level security;

create policy aest_proc_select on public.aesthetic_procedures for select to authenticated using (company_id = app.company_id());
create policy aest_proc_insert on public.aesthetic_procedures for insert to authenticated with check (company_id = app.company_id());
create policy aest_proc_update on public.aesthetic_procedures for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy aest_proc_delete on public.aesthetic_procedures for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.aesthetic_procedures to authenticated;
grant all on public.aesthetic_procedures to service_role;

-- ---------------------------------------------------------------------------
-- aesthetic_config — horário + slot (1:1 com company). Espelho salon_config.
-- ---------------------------------------------------------------------------
create table public.aesthetic_config (
  company_id    uuid        primary key references public.companies(id) on delete cascade,
  opens_at      time        not null default '09:00',
  closes_at     time        not null default '19:00',
  slot_minutes  integer     not null default 30 check (slot_minutes between 5 and 240),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.aesthetic_config is
  'Config do tenant estetica (camada 8.3): janela de funcionamento + granularidade de slot. 1:1 com company. Ausente → defaults (09:00/19:00/30).';

alter table public.aesthetic_config enable row level security;
alter table public.aesthetic_config force  row level security;

create policy aest_config_select on public.aesthetic_config for select to authenticated using (company_id = app.company_id());
create policy aest_config_insert on public.aesthetic_config for insert to authenticated with check (company_id = app.company_id());
create policy aest_config_update on public.aesthetic_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.aesthetic_config to authenticated;
grant all on public.aesthetic_config to service_role;

-- ---------------------------------------------------------------------------
-- aesthetic_packages — A ESCAPADA: pacote multi-sessão com saldo que decrementa.
-- sessions_remaining MATERIALIZADO (= total - used). total_cents = total_sessions * unit_price.
-- ---------------------------------------------------------------------------
create table public.aesthetic_packages (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  contact_id         uuid        references public.contacts(id) on delete set null,
  procedure_id       uuid        not null references public.aesthetic_procedures(id) on delete restrict,
  conversation_id    uuid        references public.conversations(id) on delete set null,
  customer_name      text        not null,   -- snapshot do contact
  customer_phone     text,                   -- snapshot opcional
  procedure_name     text        not null,   -- snapshot
  unit_price_cents   integer     not null check (unit_price_cents >= 0),   -- snapshot do preço de 1 sessão
  total_sessions     integer     not null check (total_sessions > 0),
  sessions_used      integer     not null default 0 check (sessions_used >= 0),
  sessions_remaining integer     not null check (sessions_remaining >= 0),   -- MATERIALIZADO = total - used
  total_cents        integer     not null check (total_cents >= 0),         -- MATERIALIZADO = total_sessions * unit_price
  status             text        not null default 'pendente' check (status in
                       ('pendente','ativo','esgotado','expirado','cancelado')),
  notes              text,
  purchased_at       timestamptz not null default now(),
  activated_at       timestamptz,            -- preenchido quando o tenant confirma o pagamento (→ ativo)
  status_updated_at  timestamptz not null default now(),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  check (sessions_used <= total_sessions)
);

comment on table public.aesthetic_packages is
  'Pacotes multi-sessão do tenant estetica (camada 8.3). A ESCAPADA: saldo pré-pago consumível. sessions_remaining e total_cents MATERIALIZADOS. status pendente→ativo (tenant confirma pagamento)→esgotado/expirado/cancelado. Só pacote ATIVO permite agendamento que consome. INSERT pelo backend (service_role).';

create index idx_aest_pkg_company_contact_status on public.aesthetic_packages (company_id, contact_id, status);
create index idx_aest_pkg_company_status on public.aesthetic_packages (company_id, status);
create index idx_aest_pkg_procedure on public.aesthetic_packages (procedure_id);

alter table public.aesthetic_packages enable row level security;
alter table public.aesthetic_packages force  row level security;

create policy aest_pkg_select on public.aesthetic_packages for select to authenticated using (company_id = app.company_id());
create policy aest_pkg_update on public.aesthetic_packages for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.aesthetic_packages to authenticated;
grant all on public.aesthetic_packages to service_role;

-- ---------------------------------------------------------------------------
-- aesthetic_appointments — agendamentos (clone salon + package_id + consumed_session).
-- ---------------------------------------------------------------------------
create table public.aesthetic_appointments (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  professional_id   uuid        not null references public.aesthetic_professionals(id) on delete restrict,
  procedure_id      uuid        not null references public.aesthetic_procedures(id) on delete restrict,
  package_id        uuid        references public.aesthetic_packages(id) on delete set null,   -- null = avulso
  conversation_id   uuid        references public.conversations(id) on delete set null,
  contact_id        uuid        references public.contacts(id) on delete set null,
  guest_name        text        not null,   -- snapshot do contato
  guest_phone       text,                   -- snapshot opcional
  start_at          timestamptz not null,
  duration_minutes  integer     not null,   -- snapshot do procedure.duration_minutes
  end_at            timestamptz not null,   -- materializado no INSERT (start + duration)
  procedure_name    text        not null,   -- snapshot
  professional_name text        not null,   -- snapshot
  consumed_session  boolean     not null default false,   -- true se abateu 1 sessão do pacote
  status            text        not null default 'agendado' check (status in
                      ('agendado','confirmado','realizado','cancelado','falta')),
  notes             text,
  created_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.aesthetic_appointments is
  'Agendamentos do tenant estetica (camada 8.3). Conflito POR professional_id (clone salon). package_id nullable (null = avulso). consumed_session=true abateu saldo do pacote (decrementado na MESMA transação). end_at + snapshots materializados.';

create index idx_aest_appts_company_status_start on public.aesthetic_appointments (company_id, status, start_at);
-- Índice CRÍTICO do conflito: por PROFISSIONAL, só status bloqueantes.
create index idx_aest_appts_prof_active on public.aesthetic_appointments (professional_id, start_at)
  where status in ('agendado','confirmado');
create index idx_aest_appts_contact on public.aesthetic_appointments (contact_id, start_at desc)
  where contact_id is not null;
create index idx_aest_appts_package on public.aesthetic_appointments (package_id)
  where package_id is not null;

alter table public.aesthetic_appointments enable row level security;
alter table public.aesthetic_appointments force  row level security;

create policy aest_appts_select on public.aesthetic_appointments for select to authenticated using (company_id = app.company_id());
create policy aest_appts_update on public.aesthetic_appointments for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.aesthetic_appointments to authenticated;
grant all on public.aesthetic_appointments to service_role;

-- ---------------------------------------------------------------------------
-- aesthetic_session_notes — ficha/evolução TEXTUAL por sessão (1:1 com o agendamento). SEM foto.
-- ---------------------------------------------------------------------------
create table public.aesthetic_session_notes (
  id             uuid        primary key default gen_random_uuid(),
  company_id     uuid        not null references public.companies(id) on delete restrict,
  appointment_id uuid        not null references public.aesthetic_appointments(id) on delete cascade,
  treated_area   text,        -- "rosto", "axila", "abdômen" (texto livre)
  device_params  text,        -- parâmetros do aparelho (texto livre)
  observations   text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  unique (appointment_id)      -- 1:1 com o agendamento
);

comment on table public.aesthetic_session_notes is
  'Ficha/evolução textual por sessão (camada 8.3). 1:1 com o agendamento. Registro ADMINISTRATIVO-estético (área tratada, parâmetros, observações) — NÃO prontuário médico (dado clínico sensível é fase futura com cripto). SEM foto (bloqueador SERVICE_ROLE_KEY).';

create index idx_aest_notes_company on public.aesthetic_session_notes (company_id);

alter table public.aesthetic_session_notes enable row level security;
alter table public.aesthetic_session_notes force  row level security;

create policy aest_notes_select on public.aesthetic_session_notes for select to authenticated using (company_id = app.company_id());
create policy aest_notes_update on public.aesthetic_session_notes for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.aesthetic_session_notes to authenticated;
grant all on public.aesthetic_session_notes to service_role;
