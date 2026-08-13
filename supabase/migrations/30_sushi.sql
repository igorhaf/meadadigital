-- =============================================================================
-- 30_sushi.sql
-- Meada — Camada 7.1 (SM-B: perfil Sushi / SushiBot). Primeiro perfil vertical
-- real depois da fundação multi-perfil (SM-A). Tabelas exclusivas do perfil 'sushi':
-- cardápio, config do restaurante, pedidos e itens de pedido.
--
-- Convenções (padrão das migrations anteriores):
--   - RLS enable + force; policies do tenant via app.company_id(); grants authenticated +
--     service_role.
--   - sushi_orders/sushi_order_items: INSERT vem do BACKEND (service_role) — o pedido é
--     criado pela IA via OrderConfirmHandler, não pelo SDK do tenant. O tenant só SELECT/
--     UPDATE (status pelo Kanban).
--   - Snapshot de preço+nome em sushi_order_items: alterar/excluir um item do cardápio NÃO
--     altera pedidos passados.
--   - updated_at é mantido pelos repositórios (set updated_at = now() no UPDATE), padrão do
--     projeto — não há trigger genérico de updated_at.
--   - Categorias hardcoded (CHECK) em sync com SushiCategory.java + sushi-categories.ts
--     (SushiCategoryParityTest garante a paridade Java↔TS).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- sushi_menu_items — cardápio do restaurante (só texto; foto bloqueada por SERVICE_ROLE_KEY)
-- ---------------------------------------------------------------------------
create table public.sushi_menu_items (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 120),
  description text,
  price_cents integer     not null check (price_cents >= 0),
  category    text        not null check (category in
                ('entradas','hot_rolls','sashimi','combinados','bebidas','sobremesas')),
  available   boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.sushi_menu_items is
  'Cardápio do tenant sushi (camada 7.1). Categorias hardcoded em sync com SushiCategory.java. Sem foto (bloqueador SERVICE_ROLE_KEY).';

create index idx_sushi_menu_company_cat on public.sushi_menu_items (company_id, category)
  where available = true;

alter table public.sushi_menu_items enable row level security;
alter table public.sushi_menu_items force  row level security;

create policy sushi_menu_select on public.sushi_menu_items
  for select to authenticated using (company_id = app.company_id());
create policy sushi_menu_insert on public.sushi_menu_items
  for insert to authenticated with check (company_id = app.company_id());
create policy sushi_menu_update on public.sushi_menu_items
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy sushi_menu_delete on public.sushi_menu_items
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.sushi_menu_items to authenticated;
grant all on public.sushi_menu_items to service_role;

-- ---------------------------------------------------------------------------
-- sushi_restaurant_config — taxa de entrega + pedido mínimo (1:1 com company)
-- ---------------------------------------------------------------------------
create table public.sushi_restaurant_config (
  company_id         uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents    integer     not null default 0 check (min_order_cents >= 0),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.sushi_restaurant_config is
  'Config do restaurante sushi (camada 7.1): taxa de entrega + pedido mínimo. 1:1 com company. Ausente → taxa/mínimo = 0.';

alter table public.sushi_restaurant_config enable row level security;
alter table public.sushi_restaurant_config force  row level security;

create policy sushi_config_select on public.sushi_restaurant_config
  for select to authenticated using (company_id = app.company_id());
create policy sushi_config_insert on public.sushi_restaurant_config
  for insert to authenticated with check (company_id = app.company_id());
create policy sushi_config_update on public.sushi_restaurant_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.sushi_restaurant_config to authenticated;
grant all on public.sushi_restaurant_config to service_role;

-- ---------------------------------------------------------------------------
-- sushi_orders — pedidos (criados pelo backend via IA; tenant gerencia status)
-- ---------------------------------------------------------------------------
create table public.sushi_orders (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  conversation_id    uuid        not null references public.conversations(id) on delete restrict,
  contact_id         uuid        not null references public.contacts(id) on delete restrict,
  status             text        not null default 'recebido' check (status in
                       ('recebido','preparo','saiu_pra_entrega','entregue','cancelado')),
  subtotal_cents     integer     not null,
  delivery_fee_cents integer     not null default 0,
  total_cents        integer     not null,
  delivery_address   text        not null,
  notes              text,
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.sushi_orders is
  'Pedidos do tenant sushi (camada 7.1). INSERT pelo backend (service_role) via OrderConfirmHandler; tenant só SELECT/UPDATE (status no Kanban).';

create index idx_sushi_orders_company_status on public.sushi_orders (company_id, status, created_at desc);
create index idx_sushi_orders_conversation on public.sushi_orders (conversation_id);

alter table public.sushi_orders enable row level security;
alter table public.sushi_orders force  row level security;

-- Tenant SELECT/UPDATE do próprio; INSERT é só backend (sem policy authenticated de insert).
create policy sushi_orders_select on public.sushi_orders
  for select to authenticated using (company_id = app.company_id());
create policy sushi_orders_update on public.sushi_orders
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.sushi_orders to authenticated;
grant all on public.sushi_orders to service_role;

-- ---------------------------------------------------------------------------
-- sushi_order_items — itens do pedido com snapshot de preço+nome
-- ---------------------------------------------------------------------------
create table public.sushi_order_items (
  id                 uuid        primary key default gen_random_uuid(),
  order_id           uuid        not null references public.sushi_orders(id) on delete cascade,
  menu_item_id       uuid        not null references public.sushi_menu_items(id) on delete restrict,
  qtd                integer     not null check (qtd > 0),
  unit_price_cents   integer     not null,
  item_name_snapshot text        not null
);

comment on table public.sushi_order_items is
  'Itens de um pedido sushi (camada 7.1). unit_price_cents + item_name_snapshot são SNAPSHOTS do momento do pedido — alterar/excluir o item no cardápio não altera o histórico.';

create index idx_sushi_order_items_order on public.sushi_order_items (order_id);

alter table public.sushi_order_items enable row level security;
alter table public.sushi_order_items force  row level security;

-- Tenant SELECT só dos itens de pedidos da própria empresa (via join no order).
create policy sushi_order_items_select on public.sushi_order_items
  for select to authenticated using (
    exists (select 1 from public.sushi_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.sushi_order_items to authenticated;
grant all on public.sushi_order_items to service_role;
