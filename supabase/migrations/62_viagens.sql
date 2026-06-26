-- =============================================================================
-- 62_viagens.sql
-- Meada WhatsApp — Camada 8.18 (SM: perfil Viagens / agência de viagens). CLONA o chassi do EVENTOS
-- (45_eventos.sql) — proposta order-based + itens de cotação (total materializado) + gate de aprovação
-- em 2 fases via tag que muta o estado — e inaugura a ESCAPADA:
--
--   ESCAPADA — ROTEIRO / ITINERÁRIO MULTI-DIA (travel_itinerary_days): a proposta de viagem tem um
--   itinerário dia-a-dia (dia 1: chegada + city tour; dia 2: passeio X; …; dia N: retorno). DIFERENTE
--   de tudo:
--     - O CRONOGRAMA do eventos/casamento é o roteiro de UM DIA (ordenado por HORA, start_time time).
--     - O ITINERÁRIO de viagens é MULTI-DIA: UMA linha por DIA da viagem (day_number int + day_date date
--       NULLABLE), ordenado por day_date asc NULLS LAST, day_number asc. Cobre 7/10/15 dias.
--     - SEM status/progresso (≠ checklist binário do casamento, ≠ etapas de 3 estados de projetos) — é
--       descritivo. NÃO entra no total. Gerenciado SÓ no painel (SEM tag de IA).
--
-- SEM conflito de agenda/data (start_date/end_date/day_date são campos LIVRES — é COTAÇÃO, não reserva
-- de recurso). Cliente NÃO é entidade (continua o contact; snapshots na proposta). Funil idêntico ao
-- EventProposalStatus.
--
-- Convenções (padrão 30-61): RLS enable+force; policies via app.company_id(); grants authenticated +
-- service_role; proposals/items/itinerary INSERT pelo backend; total/line_total materializados.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'viagens'. ESPELHA a CHECK mais completa (59_papelaria, 32 perfis) +
-- 'viagens'. Entra por ÚLTIMO no SCRIPTS de teste (sua lista tem os 33).
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia','cursos','lingerie','moda_infantil','las',
                        'padaria','otica','papelaria','viagens'));

-- ---------------------------------------------------------------------------
-- travel_consultants — consultores/agentes de viagem (catálogo SIMPLES; espelho event_planners).
-- ---------------------------------------------------------------------------
create table public.travel_consultants (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "internacional / lua-de-mel", "nacional / cruzeiros" (texto livre)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.travel_consultants is
  'Consultores de viagem do tenant viagens (camada 8.18). Catálogo simples, SEM agenda/conflito (espelho event_planners). Atribuir à proposta é opcional. delete em uso → 409 consultant_in_use.';

create index idx_travel_consultants_company_active on public.travel_consultants (company_id, active) where active = true;

alter table public.travel_consultants enable row level security;
alter table public.travel_consultants force  row level security;

create policy travel_consultants_select on public.travel_consultants for select to authenticated using (company_id = app.company_id());
create policy travel_consultants_insert on public.travel_consultants for insert to authenticated with check (company_id = app.company_id());
create policy travel_consultants_update on public.travel_consultants for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy travel_consultants_delete on public.travel_consultants for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.travel_consultants to authenticated;
grant all on public.travel_consultants to service_role;

-- ---------------------------------------------------------------------------
-- travel_config — config 1:1 (SEM horário/slot; espelho event_config).
-- ---------------------------------------------------------------------------
create table public.travel_config (
  company_id    uuid        primary key references public.companies(id) on delete cascade,
  business_name text,        -- nome da agência
  notes         text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.travel_config is
  'Config do tenant viagens (camada 8.18): nome da agência + notas. 1:1 com company. SEM horário/slot (não há agenda). Espelho event_config.';

alter table public.travel_config enable row level security;
alter table public.travel_config force  row level security;

create policy travel_config_select on public.travel_config for select to authenticated using (company_id = app.company_id());
create policy travel_config_insert on public.travel_config for insert to authenticated with check (company_id = app.company_id());
create policy travel_config_update on public.travel_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.travel_config to authenticated;
grant all on public.travel_config to service_role;

-- ---------------------------------------------------------------------------
-- travel_proposals — propostas de viagem (order-based; total materializado; snapshots). Clone event_proposals.
-- ---------------------------------------------------------------------------
create table public.travel_proposals (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  contact_id        uuid        references public.contacts(id) on delete set null,
  consultant_id     uuid        references public.travel_consultants(id) on delete set null,
  conversation_id   uuid        references public.conversations(id) on delete set null,
  customer_name     text        not null,   -- snapshot do contact
  customer_phone    text,                   -- snapshot opcional
  destination       text,                   -- destino desejado (texto livre)
  start_date        date,                   -- ida (CAMPO LIVRE, sem conflito)
  end_date          date,                   -- volta (CAMPO LIVRE)
  num_travelers     integer     not null default 1 check (num_travelers >= 1),
  travel_style      text,                   -- econômico/conforto/luxo/aventura/lua-de-mel (texto livre)
  briefing          text,                   -- o que o cliente sonha + orçamento aproximado (texto)
  total_cents       integer     not null default 0,   -- MATERIALIZADO a cada mutação de item de cotação
  status            text        not null default 'rascunho' check (status in
                      ('rascunho','orcada','aprovada','recusada','fechada','realizada','cancelada')),
  notes             text,
  opened_at         timestamptz not null default now(),
  closed_at         timestamptz,
  status_updated_at timestamptz not null default now(),
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.travel_proposals is
  'Propostas de viagem do tenant viagens (camada 8.18). INSERT pelo backend. total_cents materializado a cada mutação de item de cotação. Snapshots de cliente. Status com gate de aprovação em 2 fases (idêntico EventProposalStatus). start_date/end_date são campos LIVRES (SEM conflito de agenda — é cotação). Espelho event_proposals (event_type→destination/travel_style; event_date→start_date/end_date; + num_travelers).';

create index idx_travel_prop_company_status on public.travel_proposals (company_id, status, opened_at desc);
create index idx_travel_prop_company_consultant on public.travel_proposals (company_id, consultant_id);
create index idx_travel_prop_company_contact on public.travel_proposals (company_id, contact_id, opened_at desc);

alter table public.travel_proposals enable row level security;
alter table public.travel_proposals force  row level security;

create policy travel_prop_select on public.travel_proposals for select to authenticated using (company_id = app.company_id());
create policy travel_prop_update on public.travel_proposals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.travel_proposals to authenticated;
grant all on public.travel_proposals to service_role;

-- ---------------------------------------------------------------------------
-- travel_proposal_items — itens de COTAÇÃO (entram no total; line_total materializado). Clone event_proposal_items.
-- ---------------------------------------------------------------------------
create table public.travel_proposal_items (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  proposal_id      uuid        not null references public.travel_proposals(id) on delete cascade,
  category         text        not null default 'outro' check (category in
                     ('aereo','hospedagem','traslado','passeio','outro')),
  description      text        not null check (length(trim(description)) between 1 and 200),
  quantity         integer     not null default 1 check (quantity > 0),
  unit_price_cents integer     not null check (unit_price_cents >= 0),
  line_total_cents integer     not null check (line_total_cents >= 0),   -- = quantity * unit_price (materializado)
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.travel_proposal_items is
  'Itens de COTAÇÃO de uma proposta de viagem (camada 8.18). category aereo/hospedagem/traslado/passeio/outro. line_total_cents materializado; total_cents da proposta recalculado na mesma transação. Espelho event_proposal_items (entra no total).';

create index idx_travel_pitem_proposal on public.travel_proposal_items (proposal_id);
create index idx_travel_pitem_company on public.travel_proposal_items (company_id);

alter table public.travel_proposal_items enable row level security;
alter table public.travel_proposal_items force  row level security;

create policy travel_pitem_select on public.travel_proposal_items for select to authenticated using (company_id = app.company_id());
create policy travel_pitem_update on public.travel_proposal_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.travel_proposal_items to authenticated;
grant all on public.travel_proposal_items to service_role;

-- ---------------------------------------------------------------------------
-- travel_itinerary_days — A ESCAPADA: roteiro MULTI-DIA (uma linha por DIA, ordenado por data). NÃO entra
-- no total. SEM status (descritivo). Gerenciado só no painel (sem tag de IA).
-- ---------------------------------------------------------------------------
create table public.travel_itinerary_days (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete restrict,
  proposal_id  uuid        not null references public.travel_proposals(id) on delete cascade,
  day_number   integer     not null default 1 check (day_number >= 1),   -- sequência do dia da viagem
  day_date     date,                                                     -- data do dia (NULLABLE — pode estar em aberto)
  title        text        not null check (length(trim(title)) between 1 and 200),
  description  text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

comment on table public.travel_itinerary_days is
  'Roteiro/ITINERÁRIO MULTI-DIA de uma proposta de viagem (camada 8.18). A ESCAPADA: UMA linha por DIA da viagem (day_number + day_date NULLABLE), ordenado por day_date asc NULLS LAST, day_number asc, created_at asc. Descritivo SEM status (≠ checklist binário casamento, ≠ etapas 3 estados projetos, ≠ cronograma de UM dia ordenado por hora eventos). NÃO entra no total. Gerenciado só no painel (sem tag de IA). Trava junto com itemsLocked().';

create index idx_travel_itin_proposal_date on public.travel_itinerary_days (proposal_id, day_date, day_number);
create index idx_travel_itin_company on public.travel_itinerary_days (company_id);

alter table public.travel_itinerary_days enable row level security;
alter table public.travel_itinerary_days force  row level security;

create policy travel_itin_select on public.travel_itinerary_days for select to authenticated using (company_id = app.company_id());
create policy travel_itin_update on public.travel_itinerary_days for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.travel_itinerary_days to authenticated;
grant all on public.travel_itinerary_days to service_role;
