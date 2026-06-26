-- =============================================================================
-- 52_padaria.sql
-- Meada WhatsApp — Camada 8.8 (SM: perfil Padaria / Confeitaria). CLONA o chassi do FLORICULTURA
-- (49_floricultura.sql) — que clonou o COMIDA — cardápio + OPÇÕES/personalização (modifiers via
-- price_delta) + carrinho-na-conversa + tag de pedido + recálculo de total + snapshot + taxa/mínimo +
-- Kanban + GATE DE ACEITE HUMANO + pedido AGENDADO por dia + período. DUAS escapadas novas:
--
--   ESCAPADA 1 — PRONTA-ENTREGA × SOB-ENCOMENDA com ANTECEDÊNCIA MÍNIMA (lead time): cada item tem
--   `made_to_order` + `lead_time_days` (override do default da config). Itens de pronta-entrega (pão,
--   salgado, doce de balcão) NÃO exigem data; itens sob encomenda (bolo) exigem
--   `pickup_or_delivery_date` que respeite o lead_time (validado no backend → 422 lead_time_violation
--   com a 1ª data possível). A data é CONDICIONAL: obrigatória só se há item sob encomenda; é a MAIOR
--   antecedência exigida entre os itens. (A floricultura agenda SEMPRE; aqui a data é condicional.)
--
--   ESCAPADA 2 — PERSONALIZAÇÃO DO BOLO: além dos modifiers planos (sabor/recheio/peso via options com
--   price_delta), o item carrega `cake_message` (texto livre de placa, snapshot no order_item).
--
--   RETIRADA × ENTREGA: `fulfillment` ('retirada'|'entrega'). retirada = balcão (sem taxa/endereço);
--   entrega = exige delivery_address + soma delivery_fee. (A floricultura é sempre entrega.)
--
-- Convenções (padrão 30-67): RLS enable+force; policies via app.company_id(); grants authenticated +
-- service_role; orders/order_items/order_item_options INSERT só backend; total/unit_price materializados;
-- categorias hardcoded (PadariaCategory); fulfillment/period hardcoded (parity).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'padaria'. ESPELHA a CHECK mais recente (67_las, 29 perfis) + 'padaria'.
-- Como 52 < 67, esta migration roda ANTES no disco; mas no SCRIPTS de teste entra DEPOIS da las (sua
-- CHECK tem TODOS os 30). Aqui acrescenta padaria à lista completa.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia','cursos','lingerie','moda_infantil','las',
                        'padaria'));

-- ---------------------------------------------------------------------------
-- padaria_config — taxa + mínimo + lead_time_days_default (clone floricultura_config + lead default).
-- ---------------------------------------------------------------------------
create table public.padaria_config (
  company_id            uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents    integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents       integer     not null default 0 check (min_order_cents >= 0),
  lead_time_days_default integer    not null default 1 check (lead_time_days_default >= 0),
  created_at            timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

comment on table public.padaria_config is
  'Config do tenant padaria (camada 8.8): taxa + mínimo + lead_time_days_default (antecedência default p/ encomenda). 1:1 com company. Ausente → 0/0/default. Clone floricultura_config + lead.';

alter table public.padaria_config enable row level security;
alter table public.padaria_config force  row level security;

create policy padaria_config_select on public.padaria_config for select to authenticated using (company_id = app.company_id());
create policy padaria_config_insert on public.padaria_config for insert to authenticated with check (company_id = app.company_id());
create policy padaria_config_update on public.padaria_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.padaria_config to authenticated;
grant all on public.padaria_config to service_role;

-- ---------------------------------------------------------------------------
-- padaria_menu_items — cardápio (+ made_to_order + lead_time_days + allergens). ESCAPADA 1.
-- ---------------------------------------------------------------------------
create table public.padaria_menu_items (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  name          text        not null check (length(trim(name)) between 1 and 200),
  description   text,
  price_cents   integer     not null check (price_cents >= 0),
  category      text        not null check (category in
                  ('paes','salgados','doces_balcao','bolos_encomenda','tortas','bebidas')),
  made_to_order boolean     not null default false,             -- ESCAPADA 1: item sob encomenda
  lead_time_days integer    check (lead_time_days is null or lead_time_days >= 0),  -- override do default da config
  allergens     text,                                           -- texto livre informativo
  available     boolean     not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.padaria_menu_items is
  'Cardápio do tenant padaria (camada 8.8). price_cents = preço BASE. made_to_order = sob encomenda (exige data que respeite lead_time_days, ou o default da config). Pronta-entrega = made_to_order false. Categorias hardcoded (PadariaCategory). Sem foto.';

create index idx_padaria_menu_company_cat on public.padaria_menu_items (company_id, category) where available = true;

alter table public.padaria_menu_items enable row level security;
alter table public.padaria_menu_items force  row level security;

create policy padaria_menu_select on public.padaria_menu_items for select to authenticated using (company_id = app.company_id());
create policy padaria_menu_insert on public.padaria_menu_items for insert to authenticated with check (company_id = app.company_id());
create policy padaria_menu_update on public.padaria_menu_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy padaria_menu_delete on public.padaria_menu_items for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.padaria_menu_items to authenticated;
grant all on public.padaria_menu_items to service_role;

-- ---------------------------------------------------------------------------
-- padaria_menu_item_options — modifiers/personalização (Sabor/Recheio/Tamanho). Clone floricultura opts.
-- ---------------------------------------------------------------------------
create table public.padaria_menu_item_options (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  menu_item_id      uuid        not null references public.padaria_menu_items(id) on delete cascade,
  group_label       text        not null check (length(trim(group_label)) between 1 and 60),   -- "Sabor","Recheio","Tamanho"
  option_label      text        not null check (length(trim(option_label)) between 1 and 80),
  price_delta_cents integer     not null default 0 check (price_delta_cents >= 0),
  available         boolean     not null default true,
  sort_order        integer     not null default 0,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.padaria_menu_item_options is
  'Modifiers/personalização de um item da padaria (camada 8.8, ESCAPADA 2). Cada linha = UMA opção de UM grupo (Sabor/Recheio/Tamanho). price_delta soma ao preço base. on delete cascade.';

create index idx_padaria_opt_item on public.padaria_menu_item_options (menu_item_id, sort_order) where available = true;
create index idx_padaria_opt_company on public.padaria_menu_item_options (company_id);

alter table public.padaria_menu_item_options enable row level security;
alter table public.padaria_menu_item_options force  row level security;

create policy padaria_opt_select on public.padaria_menu_item_options for select to authenticated using (company_id = app.company_id());
create policy padaria_opt_insert on public.padaria_menu_item_options for insert to authenticated with check (company_id = app.company_id());
create policy padaria_opt_update on public.padaria_menu_item_options for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy padaria_opt_delete on public.padaria_menu_item_options for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.padaria_menu_item_options to authenticated;
grant all on public.padaria_menu_item_options to service_role;

-- ---------------------------------------------------------------------------
-- padaria_orders — pedidos (INSERT backend; gate de aceite + data condicional + fulfillment).
-- ---------------------------------------------------------------------------
create table public.padaria_orders (
  id                      uuid        primary key default gen_random_uuid(),
  company_id              uuid        not null references public.companies(id) on delete restrict,
  conversation_id         uuid        not null references public.conversations(id) on delete restrict,
  contact_id              uuid        not null references public.contacts(id) on delete restrict,
  status                  text        not null default 'aguardando' check (status in
                            ('aguardando','em_preparo','pronto','retirado','saiu_entrega','entregue','recusado','cancelado')),
  fulfillment             text        not null default 'retirada' check (fulfillment in ('retirada','entrega')),
  subtotal_cents          integer     not null,
  delivery_fee_cents      integer     not null default 0,
  total_cents             integer     not null,
  delivery_address        text,                                 -- obrigatório só p/ entrega (validado no backend)
  pickup_or_delivery_date date,                                 -- obrigatória só se há item sob encomenda (ESCAPADA 1)
  delivery_period         text        check (delivery_period is null or delivery_period in ('manha','tarde')),
  notes                   text,
  rejection_reason        text,                                 -- gate de aceite
  created_at              timestamptz not null default now(),
  status_updated_at       timestamptz not null default now()
);

comment on table public.padaria_orders is
  'Pedidos do tenant padaria (camada 8.8). INSERT pelo backend. Nasce ''aguardando'' (gate de aceite humano). fulfillment retirada(balcão, sem taxa/endereço)/entrega(com). pickup_or_delivery_date CONDICIONAL: obrigatória só se há item made_to_order, e respeita o lead_time. Funil diverge no fim (retirado vs entregue). Total materializado.';

create index idx_padaria_orders_company_status on public.padaria_orders (company_id, status, created_at desc);
create index idx_padaria_orders_conversation on public.padaria_orders (conversation_id);

alter table public.padaria_orders enable row level security;
alter table public.padaria_orders force  row level security;

create policy padaria_orders_select on public.padaria_orders for select to authenticated using (company_id = app.company_id());
create policy padaria_orders_update on public.padaria_orders for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.padaria_orders to authenticated;
grant all on public.padaria_orders to service_role;

-- ---------------------------------------------------------------------------
-- padaria_order_items — itens (snapshot preço+nome + made_to_order + cake_message). Clone floricultura.
-- ---------------------------------------------------------------------------
create table public.padaria_order_items (
  id                      uuid        primary key default gen_random_uuid(),
  order_id                uuid        not null references public.padaria_orders(id) on delete cascade,
  menu_item_id            uuid        not null references public.padaria_menu_items(id) on delete restrict,
  qtd                     integer     not null check (qtd > 0),
  unit_price_cents        integer     not null,                 -- JÁ inclui Σ deltas
  item_name_snapshot      text        not null,
  made_to_order_snapshot  boolean     not null default false,
  cake_message            text                                  -- ESCAPADA 2: texto de placa (snapshot, nullable)
);

comment on table public.padaria_order_items is
  'Itens de um pedido padaria (camada 8.8). unit_price_cents (JÁ inclui Σ deltas) + item_name_snapshot + made_to_order_snapshot + cake_message são SNAPSHOTS. menu_item_id on delete restrict → item com pedido → 409 menu_item_in_use.';

create index idx_padaria_order_items_order on public.padaria_order_items (order_id);

alter table public.padaria_order_items enable row level security;
alter table public.padaria_order_items force  row level security;

create policy padaria_order_items_select on public.padaria_order_items
  for select to authenticated using (
    exists (select 1 from public.padaria_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.padaria_order_items to authenticated;
grant all on public.padaria_order_items to service_role;

-- ---------------------------------------------------------------------------
-- padaria_order_item_options — snapshot das opções escolhidas por item. Clone floricultura.
-- ---------------------------------------------------------------------------
create table public.padaria_order_item_options (
  id                    uuid        primary key default gen_random_uuid(),
  order_item_id         uuid        not null references public.padaria_order_items(id) on delete cascade,
  menu_option_id        uuid        references public.padaria_menu_item_options(id) on delete set null,
  group_label_snapshot  text        not null,
  option_label_snapshot text        not null,
  price_delta_cents     integer     not null
);

comment on table public.padaria_order_item_options is
  'Opções/personalização escolhidas de um item de pedido padaria (camada 8.8, ESCAPADA 2). Snapshots de group/option/delta. menu_option_id on delete set null preserva o histórico.';

create index idx_padaria_oio_item on public.padaria_order_item_options (order_item_id);

alter table public.padaria_order_item_options enable row level security;
alter table public.padaria_order_item_options force  row level security;

create policy padaria_oio_select on public.padaria_order_item_options
  for select to authenticated using (
    exists (select 1 from public.padaria_order_items oi
            join public.padaria_orders o on o.id = oi.order_id
            where oi.id = order_item_id and o.company_id = app.company_id()));

grant select on public.padaria_order_item_options to authenticated;
grant all on public.padaria_order_item_options to service_role;
