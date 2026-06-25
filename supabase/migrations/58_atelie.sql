-- =============================================================================
-- 58_atelie.sql
-- Meada WhatsApp — Camada 8.14 (SM: perfil Ateliê / Costura sob medida · Arte · Design). 19º perfil
-- vertical real (20º contando generic). UM perfil 'atelie' serve os TRÊS tipos de negócio (costura
-- sob medida, arte, design) — o tipo é um CAMPO da proposta (project_type), NÃO três perfis.
--
-- CLONA o chassi do EVENTOS (camada 8.2): proposta order-based + itens de ORÇAMENTO (total
-- materializado) + gate de aprovação em 2 fases via tag que muta o estado de um artefato existente.
-- Cliente NÃO é entidade (continua o contact; snapshots na proposta). UMA escapada NOVA:
--
--   ESCAPADA — SUB-ENTIDADE DE ETAPAS DE PROVA/AJUSTE (atelie_fittings): uma peça sob medida tem
--     PROVAS MARCADAS ao longo da produção (1ª prova → 2ª prova → ajuste final → entrega), cada uma
--     com title + due_date NULLABLE (previsão) + position (ORDEM explícita) + status BINÁRIO
--     (pendente/realizada) + completed_at. Espelha os marcos de cronograma do eventos (event_timeline_
--     items) na FORMA (sub-item ordenado, NÃO entra no total, gerenciado só no painel sem tag de IA),
--     com DUAS diferenças cravadas: (1) status BINÁRIO (pendente/realizada — uma prova ou aconteceu ou
--     não), não os 3 estados das etapas de execução do projetos; (2) vocabulário de PROVA DE COSTURA/
--     AJUSTE/MARCO DE ENTREGA (não cronograma de horas, não produção/obra).
--
-- Convenções (padrão das migrations 30-63; eventos 45 é a referência direta):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - atelie_proposals / atelie_proposal_items / atelie_fittings: INSERT pelo BACKEND (service_role)
--     onde vem da IA (proposta) ou do painel (itens/provas); tenant SELECT/UPDATE.
--   - total_cents (proposta) e line_total_cents (item de orçamento) MATERIALIZADOS no INSERT/UPDATE;
--     NÃO colunas geradas (lição das SMs anteriores).
--   - Snapshots de cliente (customer_name/phone) na proposta. estimated_date / due_date são campos
--     LIVRES (SEM conflito de agenda — a peça não disputa slot).
--   - project_type hardcoded (CHECK) em sync com AtelieProjectType.java + atelie-project-type.ts.
--   - Status da proposta hardcoded (CHECK) em sync com AtelieProposalStatus.java + atelie-proposal-
--     status.ts. Status da PROVA (pendente/realizada) é CHECK simples, SEM enum/parity.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'atelie' (19º perfil real; 20º contando generic).
-- A lista ESPELHA a CHECK mais recente (63_escola, 19 perfis) e APENDA 'atelie' — nenhum nicho some.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie'));

-- ---------------------------------------------------------------------------
-- atelie_artisans — artesãos/responsáveis (catálogo SIMPLES, sem agenda/conflito). Espelho event_planners.
-- ---------------------------------------------------------------------------
create table public.atelie_artisans (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "costura sob medida / alfaiataria", "arte / ilustração" (texto livre)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.atelie_artisans is
  'Artesãos/responsáveis do tenant atelie (camada 8.14). Catálogo simples SEM agenda — atribuição opcional na proposta. Espelho event_planners. active=false retira da disponibilidade; delete em uso → 409 artisan_in_use.';

create index idx_atl_artisan_company_active on public.atelie_artisans (company_id, active) where active = true;
create index idx_atl_artisan_company_name on public.atelie_artisans (company_id, name);

alter table public.atelie_artisans enable row level security;
alter table public.atelie_artisans force  row level security;

create policy atl_artisan_select on public.atelie_artisans for select to authenticated using (company_id = app.company_id());
create policy atl_artisan_insert on public.atelie_artisans for insert to authenticated with check (company_id = app.company_id());
create policy atl_artisan_update on public.atelie_artisans for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy atl_artisan_delete on public.atelie_artisans for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.atelie_artisans to authenticated;
grant all on public.atelie_artisans to service_role;

-- ---------------------------------------------------------------------------
-- atelie_config — config simples/informativa (1:1 com company; SEM horário/slot — não há agenda).
-- ---------------------------------------------------------------------------
create table public.atelie_config (
  company_id    uuid        primary key references public.companies(id) on delete cascade,
  business_name text,        -- nome do ateliê/estúdio (texto livre, nullable)
  notes         text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.atelie_config is
  'Config do tenant atelie (camada 8.14): nome do ateliê + notas. 1:1 com company. Ausente → defaults (vazios). SEM horário/slot — a proposta é order-based, não agendada. Espelho event_config.';

alter table public.atelie_config enable row level security;
alter table public.atelie_config force  row level security;

create policy atl_config_select on public.atelie_config for select to authenticated using (company_id = app.company_id());
create policy atl_config_insert on public.atelie_config for insert to authenticated with check (company_id = app.company_id());
create policy atl_config_update on public.atelie_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.atelie_config to authenticated;
grant all on public.atelie_config to service_role;

-- ---------------------------------------------------------------------------
-- atelie_proposals — propostas de peça/obra (order-based, total materializado, snapshots).
-- project_type (costura|arte|design) é CAMPO da proposta — o MESMO perfil serve os três tipos.
-- ---------------------------------------------------------------------------
create table public.atelie_proposals (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  contact_id        uuid        references public.contacts(id) on delete set null,         -- cliente (atalho)
  artisan_id        uuid        references public.atelie_artisans(id) on delete set null,  -- opcional
  conversation_id   uuid        references public.conversations(id) on delete set null,
  customer_name     text        not null,   -- snapshot do contact
  customer_phone    text,                   -- snapshot opcional
  project_type      text        not null default 'costura' check (project_type in ('costura','arte','design')),
  occasion          text,                   -- ocasião: casamento/formatura/presente/decoração (texto livre)
  briefing          text,                   -- o que o cliente imagina + medidas/dimensões aprox. + referência descrita
  estimated_date    date,                   -- previsão de entrega (campo-data LIVRE, sem slot)
  total_cents       integer     not null default 0,   -- MATERIALIZADO a cada mutação de item de orçamento
  status            text        not null default 'rascunho' check (status in
                      ('rascunho','orcada','aprovada','recusada','fechada','realizada','cancelada')),
  notes             text,
  opened_at         timestamptz not null default now(),
  closed_at         timestamptz,            -- preenchido em terminais (realizada/recusada/cancelada)
  status_updated_at timestamptz not null default now(),
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.atelie_proposals is
  'Propostas de peça/obra do tenant atelie (camada 8.14). INSERT pelo backend (service_role). project_type (costura|arte|design) = CAMPO da proposta (UM perfil serve os 3 tipos). total_cents materializado a cada mutação de item de orçamento. Snapshots de cliente. Status com gate de aprovação em 2 fases. estimated_date é campo livre (SEM conflito de agenda). Espelho event_proposals.';

create index idx_atl_prop_company_status_opened on public.atelie_proposals (company_id, status, opened_at desc);
create index idx_atl_prop_company_artisan on public.atelie_proposals (company_id, artisan_id);
create index idx_atl_prop_company_contact on public.atelie_proposals (company_id, contact_id, opened_at desc);
create index idx_atl_prop_company_type on public.atelie_proposals (company_id, project_type);

alter table public.atelie_proposals enable row level security;
alter table public.atelie_proposals force  row level security;

create policy atl_prop_select on public.atelie_proposals for select to authenticated using (company_id = app.company_id());
create policy atl_prop_update on public.atelie_proposals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.atelie_proposals to authenticated;
grant all on public.atelie_proposals to service_role;

-- ---------------------------------------------------------------------------
-- atelie_proposal_items — itens de ORÇAMENTO (entram no total). line_total materializado.
-- ---------------------------------------------------------------------------
create table public.atelie_proposal_items (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  proposal_id      uuid        not null references public.atelie_proposals(id) on delete cascade,
  description      text        not null check (length(trim(description)) between 1 and 200),
  quantity         integer     not null default 1 check (quantity > 0),
  unit_price_cents integer     not null check (unit_price_cents >= 0),
  line_total_cents integer     not null check (line_total_cents >= 0),   -- = quantity * unit_price (materializado)
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.atelie_proposal_items is
  'Itens de ORÇAMENTO de uma proposta de ateliê (camada 8.14). line_total_cents materializado (quantity*unit_price); o total_cents da proposta é recalculado na mesma transação. Espelho event_proposal_items (linha de PREÇO — entra no total). Trava itemsLocked a partir de fechada.';

create index idx_atl_pitem_proposal on public.atelie_proposal_items (proposal_id);
create index idx_atl_pitem_company on public.atelie_proposal_items (company_id);

alter table public.atelie_proposal_items enable row level security;
alter table public.atelie_proposal_items force  row level security;

create policy atl_pitem_select on public.atelie_proposal_items for select to authenticated using (company_id = app.company_id());
create policy atl_pitem_update on public.atelie_proposal_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.atelie_proposal_items to authenticated;
grant all on public.atelie_proposal_items to service_role;

-- ---------------------------------------------------------------------------
-- atelie_fittings — A ENTIDADE NOVA: etapas de PROVA/AJUSTE (ordenadas por position). NÃO entra no total.
-- Status BINÁRIO (pendente/realizada). Espelho event_timeline_items na FORMA — semântica de PROVA DE
-- COSTURA/AJUSTE/MARCO DE ENTREGA (não cronograma de horas). Gerenciada SÓ no painel (sem tag de IA).
-- ---------------------------------------------------------------------------
create table public.atelie_fittings (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  proposal_id   uuid        not null references public.atelie_proposals(id) on delete cascade,
  title         text        not null check (length(trim(title)) between 1 and 200),  -- "1ª prova", "ajuste final", "entrega"
  description   text,
  due_date      date,                       -- previsão da prova (NULLABLE — campo livre, sem conflito)
  status        text        not null default 'pendente' check (status in ('pendente','realizada')),
  position      integer     not null default 0,   -- ORDEM explícita (asc)
  completed_at  timestamptz,                -- set quando vira realizada; zerado quando volta a pendente
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.atelie_fittings is
  'Etapas de PROVA/AJUSTE de uma proposta de ateliê (camada 8.14). A ESCAPADA da SM: SEQUÊNCIA ORDENADA DE PROVAS/AJUSTES da peça sob medida (1ª prova → 2ª prova → ajuste final → entrega), status BINÁRIO (pendente/realizada) + position (ordem) + due_date prevista. NÃO entra no total_cents (≠ atelie_proposal_items). Gerenciada SÓ no painel, sem tag de IA. Espelha event_timeline_items mas com status BINÁRIO e vocabulário de prova de costura/ajuste. Trava junto com itemsLocked (fechada+).';

create index idx_atl_fitting_proposal_pos on public.atelie_fittings (proposal_id, position);
create index idx_atl_fitting_company on public.atelie_fittings (company_id);

alter table public.atelie_fittings enable row level security;
alter table public.atelie_fittings force  row level security;

create policy atl_fitting_select on public.atelie_fittings for select to authenticated using (company_id = app.company_id());
create policy atl_fitting_update on public.atelie_fittings for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.atelie_fittings to authenticated;
grant all on public.atelie_fittings to service_role;
