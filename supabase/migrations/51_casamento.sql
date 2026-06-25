-- =============================================================================
-- 51_casamento.sql
-- Meada WhatsApp — Camada 8.7 (SM: perfil Casamento / assessoria e cerimonial de casamento). 20º
-- perfil vertical real. A assessoria COORDENA casamentos (não é fornecedor isolado): a IA atende os
-- noivos, abre uma proposta a partir do briefing, e captura a aprovação/recusa quando a equipe já
-- montou o orçamento no painel.
--
-- CLONA o chassi do EVENTOS (camada 8.2): proposta order-based + itens de ORÇAMENTO (total
-- materializado) + cronograma do dia (ordenado por horário, NÃO entra no total) + gate de aprovação
-- em 2 fases via tag que muta o estado de um artefato existente. Cliente NÃO é entidade (continua o
-- contact; snapshots na proposta). UMA escapada NOVA:
--
--   ESCAPADA — TERCEIRA SUB-ENTIDADE no mesmo artefato: CHECKLIST DE TAREFAS PRÉ-CASAMENTO COM PRAZO
--     (wedding_checklist_tasks). Marcos de PREPARAÇÃO que acontecem ANTES do dia, cada um com um PRAZO
--     (due_date NULLABLE) e estado BINÁRIO pendente/concluída (done boolean). Ordenados por due_date
--     asc NULLS LAST (tarefa sem prazo vai ao fim). Eventos tinha 2 tipos de sub-item; casamento tem
--     3, que NÃO se misturam:
--       (1) orçamento  (wedding_proposal_items)  = DINHEIRO                 → entra no total
--       (2) cronograma (wedding_timeline_items)  = a HORA das coisas no dia → ordenado por start_time, NÃO entra no total
--       (3) checklist  (wedding_checklist_tasks) = o que FALTA fazer até lá → ordenado por due_date,  NÃO entra no total
--
-- Convenções (padrão das migrations 30-63; eventos 45 é a referência direta):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - wedding_proposals / *_items / *_timeline_items / *_checklist_tasks: INSERT pelo BACKEND
--     (service_role); tenant SELECT/UPDATE (gerencia no painel).
--   - total_cents (proposta) e line_total_cents (item de orçamento) MATERIALIZADOS no INSERT/UPDATE;
--     NÃO colunas geradas.
--   - Snapshots de cliente (customer_name/phone) na proposta. wedding_date é campo LIVRE (sem conflito
--     de agenda — a assessoria coordena ~1 casamento por data).
--   - Status da proposta hardcoded (CHECK) em sync com WeddingProposalStatus.java +
--     wedding-proposal-status.ts. O checklist NÃO tem enum/parity — done é boolean.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'casamento' (20º perfil real; 21º contando generic).
-- A lista ESPELHA a CHECK mais completa no disco (58_atelie, 20 perfis) e APENDA 'casamento' — nenhum
-- nicho some. (Esta migration deve ser aplicada DEPOIS das de número maior que já reescreveram a CHECK
-- — ver a ordem do SCRIPTS no AbstractIntegrationTest: 51_casamento entra por ÚLTIMO.)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento'));

-- ---------------------------------------------------------------------------
-- wedding_planners — assessores/cerimonialistas (catálogo SIMPLES, sem agenda/conflito). Espelho event_planners.
-- ---------------------------------------------------------------------------
create table public.wedding_planners (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "cerimonial completo", "destination wedding" (texto livre)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.wedding_planners is
  'Assessores/cerimonialistas do tenant casamento (camada 8.7). Catálogo simples SEM agenda — atribuição opcional na proposta. Espelho event_planners. active=false retira da disponibilidade; delete em uso → 409 planner_in_use.';

create index idx_wed_planner_company_active on public.wedding_planners (company_id, active) where active = true;
create index idx_wed_planner_company_name on public.wedding_planners (company_id, name);

alter table public.wedding_planners enable row level security;
alter table public.wedding_planners force  row level security;

create policy wed_planner_select on public.wedding_planners for select to authenticated using (company_id = app.company_id());
create policy wed_planner_insert on public.wedding_planners for insert to authenticated with check (company_id = app.company_id());
create policy wed_planner_update on public.wedding_planners for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy wed_planner_delete on public.wedding_planners for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.wedding_planners to authenticated;
grant all on public.wedding_planners to service_role;

-- ---------------------------------------------------------------------------
-- wedding_config — config simples (1:1 com company; SEM horário/slot — não há agenda).
-- ---------------------------------------------------------------------------
create table public.wedding_config (
  company_id    uuid        primary key references public.companies(id) on delete cascade,
  business_name text,        -- nome da assessoria (texto livre, nullable)
  notes         text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.wedding_config is
  'Config do tenant casamento (camada 8.7): nome da assessoria + notas. 1:1 com company. Ausente → defaults (vazios). SEM horário/slot. Espelho event_config.';

alter table public.wedding_config enable row level security;
alter table public.wedding_config force  row level security;

create policy wed_config_select on public.wedding_config for select to authenticated using (company_id = app.company_id());
create policy wed_config_insert on public.wedding_config for insert to authenticated with check (company_id = app.company_id());
create policy wed_config_update on public.wedding_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.wedding_config to authenticated;
grant all on public.wedding_config to service_role;

-- ---------------------------------------------------------------------------
-- wedding_proposals — propostas de casamento (order-based, total materializado, snapshots).
-- ---------------------------------------------------------------------------
create table public.wedding_proposals (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  contact_id        uuid        references public.contacts(id) on delete set null,         -- noivos (atalho)
  planner_id        uuid        references public.wedding_planners(id) on delete set null, -- opcional
  conversation_id   uuid        references public.conversations(id) on delete set null,
  customer_name     text        not null,   -- snapshot (pode ser "Ana & João")
  customer_phone    text,                   -- snapshot opcional
  wedding_style     text,                   -- estilo: clássico/praia/rústico (texto livre)
  wedding_date      date,                   -- data prevista (campo-data LIVRE, sem slot)
  guest_count       integer      check (guest_count >= 0),
  briefing          text,                   -- o que os noivos sonham pro dia
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

comment on table public.wedding_proposals is
  'Propostas de casamento do tenant casamento (camada 8.7). INSERT pelo backend (service_role). total_cents materializado a cada mutação de item de orçamento. Snapshots de cliente (noivos). Status com gate de aprovação em 2 fases. wedding_date é campo livre (SEM conflito de agenda). Espelho event_proposals.';

create index idx_wed_prop_company_status_opened on public.wedding_proposals (company_id, status, opened_at desc);
create index idx_wed_prop_company_planner on public.wedding_proposals (company_id, planner_id);
create index idx_wed_prop_company_contact on public.wedding_proposals (company_id, contact_id, opened_at desc);
create index idx_wed_prop_company_date on public.wedding_proposals (company_id, wedding_date);

alter table public.wedding_proposals enable row level security;
alter table public.wedding_proposals force  row level security;

create policy wed_prop_select on public.wedding_proposals for select to authenticated using (company_id = app.company_id());
create policy wed_prop_update on public.wedding_proposals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.wedding_proposals to authenticated;
grant all on public.wedding_proposals to service_role;

-- ---------------------------------------------------------------------------
-- wedding_proposal_items — itens de ORÇAMENTO (entram no total). line_total materializado.
-- ---------------------------------------------------------------------------
create table public.wedding_proposal_items (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  proposal_id      uuid        not null references public.wedding_proposals(id) on delete cascade,
  description      text        not null check (length(trim(description)) between 1 and 200),
  quantity         integer     not null default 1 check (quantity > 0),
  unit_price_cents integer     not null check (unit_price_cents >= 0),
  line_total_cents integer     not null check (line_total_cents >= 0),   -- = quantity * unit_price (materializado)
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.wedding_proposal_items is
  'Itens de ORÇAMENTO de uma proposta de casamento (camada 8.7). line_total_cents materializado (quantity*unit_price); o total_cents da proposta é recalculado na mesma transação. Espelho event_proposal_items (linha de PREÇO — entra no total). Trava itemsLocked a partir de fechada.';

create index idx_wed_pitem_proposal on public.wedding_proposal_items (proposal_id);
create index idx_wed_pitem_company on public.wedding_proposal_items (company_id);

alter table public.wedding_proposal_items enable row level security;
alter table public.wedding_proposal_items force  row level security;

create policy wed_pitem_select on public.wedding_proposal_items for select to authenticated using (company_id = app.company_id());
create policy wed_pitem_update on public.wedding_proposal_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.wedding_proposal_items to authenticated;
grant all on public.wedding_proposal_items to service_role;

-- ---------------------------------------------------------------------------
-- wedding_timeline_items — marcos de CRONOGRAMA DO DIA (ordenados por horário). NÃO entra no total.
-- ---------------------------------------------------------------------------
create table public.wedding_timeline_items (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete restrict,
  proposal_id  uuid        not null references public.wedding_proposals(id) on delete cascade,
  start_time   time        not null,   -- horário do marco no dia (ex.: '17:00')
  title        text        not null check (length(trim(title)) between 1 and 200),
  description  text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

comment on table public.wedding_timeline_items is
  'Marcos de CRONOGRAMA do dia do casamento (camada 8.7). Roteiro ORDENADO por start_time (ex.: 16:00 chegada / 17:00 cerimônia / 19:00 jantar / 22:00 festa). NÃO entra no total_cents (≠ wedding_proposal_items). Espelho event_timeline_items.';

create index idx_wed_timeline_proposal_time on public.wedding_timeline_items (proposal_id, start_time);
create index idx_wed_timeline_company on public.wedding_timeline_items (company_id);

alter table public.wedding_timeline_items enable row level security;
alter table public.wedding_timeline_items force  row level security;

create policy wed_timeline_select on public.wedding_timeline_items for select to authenticated using (company_id = app.company_id());
create policy wed_timeline_update on public.wedding_timeline_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.wedding_timeline_items to authenticated;
grant all on public.wedding_timeline_items to service_role;

-- ---------------------------------------------------------------------------
-- wedding_checklist_tasks — A ENTIDADE NOVA: checklist pré-casamento (ordenado por prazo). NÃO entra no total.
-- Estado BINÁRIO (done boolean). Gerenciado SÓ no painel (sem tag de IA, igual o cronograma).
-- ---------------------------------------------------------------------------
create table public.wedding_checklist_tasks (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete restrict,
  proposal_id  uuid        not null references public.wedding_proposals(id) on delete cascade,
  title        text        not null check (length(trim(title)) between 1 and 200),  -- "enviar convites", "provar o bolo"
  description  text,
  due_date     date,                       -- prazo do marco (NULLABLE — tarefa "sem prazo ainda")
  done         boolean     not null default false,  -- false=pendente, true=concluída
  done_at      timestamptz,                -- preenchido quando done vira true; zerado quando volta a false
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

comment on table public.wedding_checklist_tasks is
  'Checklist de tarefas PRÉ-CASAMENTO de uma proposta (camada 8.7). A ESCAPADA da SM: a 3ª sub-entidade — marcos de PREPARAÇÃO antes do dia, cada um com PRAZO (due_date nullable) e estado BINÁRIO done (pendente/concluída). Lido ordenado por due_date asc NULLS LAST, created_at asc (tarefa sem prazo vai ao fim). NÃO entra no total_cents (≠ wedding_proposal_items) e NÃO é o cronograma do dia (≠ wedding_timeline_items). Gerenciado SÓ no painel, sem tag de IA. Trava junto com itemsLocked (fechada+).';

create index idx_wed_checklist_proposal_due on public.wedding_checklist_tasks (proposal_id, due_date);
create index idx_wed_checklist_company on public.wedding_checklist_tasks (company_id);

alter table public.wedding_checklist_tasks enable row level security;
alter table public.wedding_checklist_tasks force  row level security;

create policy wed_checklist_select on public.wedding_checklist_tasks for select to authenticated using (company_id = app.company_id());
create policy wed_checklist_update on public.wedding_checklist_tasks for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.wedding_checklist_tasks to authenticated;
grant all on public.wedding_checklist_tasks to service_role;
