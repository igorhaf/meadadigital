-- =============================================================================
-- 35_pousada.sql
-- Meada WhatsApp — Camada 7.6 (SM-G: perfil Pousada / PousadaBot). Sexto perfil vertical
-- real. Tabelas exclusivas do perfil 'pousada': quartos, config e reservas.
--
-- EVOLUÇÃO ESTRUTURAL do padrão (vs salon/dental/restaurant): a reserva é um INTERVALO DE
-- DIAS (check_in_date/check_out_date como DATE, não timestamptz — é dia, não instante), não
-- um slot de horas. O conflito é overlap de intervalos HALF-OPEN [check_in, check_out) por
-- quarto — check-out de uma reserva e check-in de outra NO MESMO DIA não conflitam (o quarto
-- rotaciona). nights e total_cents são materializados no INSERT.
--
-- Convenções (padrão das migrations 30-34):
--   - RLS enable + force; policies do tenant via app.company_id(); grants authenticated +
--     service_role.
--   - pousada_reservations: INSERT pelo BACKEND (service_role) — IA ou tenant (POST manual).
--     Tenant só SELECT/UPDATE (status).
--   - SNAPSHOTS em pousada_reservations: room_name + nightly_rate_cents + capacity_snapshot +
--     total_cents congelados no momento — mudar preço/capacidade do quarto NÃO altera reservas
--     passadas.
--   - Cliente NÃO é entidade própria (decisão cravada, igual salon): hóspedes rotativos. O
--     histórico vem do contact + reservations. guest_name/guest_phone são snapshots.
--   - LGPD: notes é administrativo, sem RG/CPF/documento.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'pousada' (6º perfil real; 7º contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada'));

-- ---------------------------------------------------------------------------
-- pousada_rooms — catálogo de quartos
-- ---------------------------------------------------------------------------
create table public.pousada_rooms (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  name               text        not null check (length(trim(name)) between 1 and 200),
  capacity           integer     not null check (capacity between 1 and 20),
  nightly_rate_cents integer     not null check (nightly_rate_cents >= 0),
  description        text,
  active             boolean     not null default true,
  notes              text,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.pousada_rooms is
  'Quartos do tenant pousada (camada 7.6). nightly_rate_cents = diária em centavos; capacity = máx. hóspedes. Entram como SNAPSHOT na reserva. active=false retira da disponibilidade da IA.';

create index idx_pousada_rooms_company_active on public.pousada_rooms (company_id, active)
  where active = true;
create index idx_pousada_rooms_company_capacity on public.pousada_rooms (company_id, capacity)
  where active = true;

alter table public.pousada_rooms enable row level security;
alter table public.pousada_rooms force  row level security;

create policy pousada_rooms_select on public.pousada_rooms
  for select to authenticated using (company_id = app.company_id());
create policy pousada_rooms_insert on public.pousada_rooms
  for insert to authenticated with check (company_id = app.company_id());
create policy pousada_rooms_update on public.pousada_rooms
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy pousada_rooms_delete on public.pousada_rooms
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.pousada_rooms to authenticated;
grant all on public.pousada_rooms to service_role;

-- ---------------------------------------------------------------------------
-- pousada_config — check-in/check-out + política (1:1 com company)
-- ---------------------------------------------------------------------------
create table public.pousada_config (
  company_id          uuid        primary key references public.companies(id) on delete cascade,
  check_in_time       time        not null default '14:00',
  check_out_time      time        not null default '11:00',
  cancellation_policy text,        -- texto livre, opcional
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now()
);

comment on table public.pousada_config is
  'Config do tenant pousada (camada 7.6): horário de check-in/check-out + política de cancelamento (texto livre). 1:1 com company. Ausente → defaults (14:00/11:00/null).';

alter table public.pousada_config enable row level security;
alter table public.pousada_config force  row level security;

create policy pousada_config_select on public.pousada_config
  for select to authenticated using (company_id = app.company_id());
create policy pousada_config_insert on public.pousada_config
  for insert to authenticated with check (company_id = app.company_id());
create policy pousada_config_update on public.pousada_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.pousada_config to authenticated;
grant all on public.pousada_config to service_role;

-- ---------------------------------------------------------------------------
-- pousada_reservations — reservas (intervalo de dias)
-- ---------------------------------------------------------------------------
create table public.pousada_reservations (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  room_id            uuid        not null references public.pousada_rooms(id) on delete restrict,
  conversation_id    uuid        references public.conversations(id) on delete set null,
  contact_id         uuid        references public.contacts(id) on delete set null,
  guest_name         text        not null,   -- titular (snapshot)
  guest_phone        text,                   -- snapshot opcional
  guests_count       integer     not null check (guests_count >= 1),
  check_in_date      date        not null,
  check_out_date     date        not null,
  nights             integer     not null check (nights > 0),   -- materializado no INSERT
  room_name          text        not null,   -- snapshot
  nightly_rate_cents integer     not null,   -- snapshot
  capacity_snapshot  integer     not null,   -- snapshot do room.capacity
  total_cents        integer     not null,   -- materializado = nightly_rate_cents × nights
  status             text        not null default 'reservado' check (status in
                       ('reservado','confirmado','checked_in','checked_out','cancelado','no_show')),
  notes              text,
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now(),
  constraint pousada_res_dates_check check (check_out_date > check_in_date)
);

comment on table public.pousada_reservations is
  'Reservas do tenant pousada (camada 7.6). Intervalo de DIAS (check_in/check_out date). Conflito por overlap HALF-OPEN [check_in, check_out) por room — rotação no mesmo dia OK. nights/total_cents materializados; room_name/nightly_rate/capacity snapshots.';

create index idx_pousada_res_company_status_date on public.pousada_reservations (company_id, status, check_in_date);
-- Índice CRÍTICO da checagem de conflito: por QUARTO, só status bloqueantes.
create index idx_pousada_res_room_active on public.pousada_reservations (room_id, check_in_date)
  where status in ('reservado','confirmado','checked_in');
create index idx_pousada_res_contact on public.pousada_reservations (contact_id, check_in_date desc)
  where contact_id is not null;

alter table public.pousada_reservations enable row level security;
alter table public.pousada_reservations force  row level security;

create policy pousada_res_select on public.pousada_reservations
  for select to authenticated using (company_id = app.company_id());
create policy pousada_res_update on public.pousada_reservations
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.pousada_reservations to authenticated;
grant all on public.pousada_reservations to service_role;
