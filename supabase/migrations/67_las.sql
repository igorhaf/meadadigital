-- =============================================================================
-- 67_las.sql
-- Meada WhatsApp — Camada 8.23 (SM: perfil Lãs / loja de lãs · novelos · tricô/crochê · varejo). CLONA o
-- chassi de VAREJO COM VARIANTES da Lingerie (65_lingerie.sql), com a adaptação do EIXO DE VARIANTE:
--   aqui a variante é COR × DYE_LOT (lote de tingimento) — não tamanho×cor.
--
-- ⭐ ESCAPADA — DYE LOT (lote de tingimento) + regra "MESMO LOTE PREFERENCIAL":
--   Novelos de lã da MESMA cor mas de LOTES de tingimento diferentes têm variação visível de tom. Quem
--   tricota um projeto grande precisa de novelos do MESMO lote. Por isso a variante é (color, dye_lot):
--   cada lote da mesma cor é um SKU próprio com SEU estoque. O pedido pode pedir `same_lot_guaranteed`
--   (todos os novelos do MESMO lote). Quando garantido, o backend valida que TODOS os itens da mesma cor
--   referenciam o MESMO dye_lot → senão 422 `mixed_dye_lots` (com as cores que misturaram lote). O eixo
--   `size` da lingerie vira `dye_lot` (texto livre, ex.: "L2024-A", "L2024-B"); a cor continua texto livre.
--
-- CLONA o resto do chassi do LINGERIE: produto + variantes com estoque + decremento transacional
-- (UPDATE condicional stock_qty >= qtd → out_of_stock aborta), pedido nasce 'aguardando' (gate de aceite
-- humano), Kanban, total materializado, snapshots, fulfillment entrega/retirada.
--
-- Convenções (padrão 30-66): RLS enable+force; policies via app.company_id(); grants authenticated +
-- service_role; orders/order_items INSERT só backend; total/unit_price materializados; categorias
-- hardcoded em sync com LasCategory; cor/dye_lot texto livre.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'las' (29º contando generic). ESPELHA 66_moda_infantil (28) + 'las'.
-- Entra por ÚLTIMO no SCRIPTS de teste.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia','cursos','lingerie','moda_infantil','las'));

-- ---------------------------------------------------------------------------
-- las_config — taxa de entrega + mínimo (clone lingerie_config).
-- ---------------------------------------------------------------------------
create table public.las_config (
  company_id         uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents    integer     not null default 0 check (min_order_cents >= 0),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.las_config is
  'Config do tenant las (camada 8.23): taxa + mínimo. 1:1 com company. Clone lingerie_config.';

alter table public.las_config enable row level security;
alter table public.las_config force  row level security;

create policy las_config_select on public.las_config for select to authenticated using (company_id = app.company_id());
create policy las_config_insert on public.las_config for insert to authenticated with check (company_id = app.company_id());
create policy las_config_update on public.las_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.las_config to authenticated;
grant all on public.las_config to service_role;

-- ---------------------------------------------------------------------------
-- las_products — catálogo (clone lingerie_products; categorias de lãs).
-- ---------------------------------------------------------------------------
create table public.las_products (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  description text,
  category    text        not null check (category in
                ('las','linhas','kits','agulhas','acessorios','pelucia')),
  base_price_cents integer not null check (base_price_cents >= 0),
  available   boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.las_products is
  'Produtos do tenant las (camada 8.23). category hardcoded (sync LasCategory). A grade de variantes (cor×dye_lot) é las_variants. delete em uso → 409 product_in_use.';

create index idx_las_products_company_cat on public.las_products (company_id, category) where available = true;

alter table public.las_products enable row level security;
alter table public.las_products force  row level security;

create policy las_products_select on public.las_products for select to authenticated using (company_id = app.company_id());
create policy las_products_insert on public.las_products for insert to authenticated with check (company_id = app.company_id());
create policy las_products_update on public.las_products for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy las_products_delete on public.las_products for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.las_products to authenticated;
grant all on public.las_products to service_role;

-- ---------------------------------------------------------------------------
-- las_variants — ⭐ grade COR × DYE_LOT com estoque por lote (clone lingerie_variants, eixo trocado).
-- ---------------------------------------------------------------------------
create table public.las_variants (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  product_id       uuid        not null references public.las_products(id) on delete cascade,
  color            text        not null check (length(trim(color)) between 1 and 40),   -- texto livre
  dye_lot          text        not null check (length(trim(dye_lot)) between 1 and 40), -- lote de tingimento
  sku              text,
  price_cents      integer,     -- nullable: herda base_price do produto
  stock_qty        integer     not null default 0 check (stock_qty >= 0),
  available        boolean     not null default true,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.las_variants is
  'Variantes (cor×dye_lot) do produto (camada 8.23, ⭐ escapada). dye_lot = LOTE DE TINGIMENTO: novelos da mesma cor de lotes diferentes têm variação de tom. Cada (cor,lote) é um SKU com SEU estoque. UNIQUE(product_id, color, dye_lot). O pedido pode exigir same_lot_guaranteed (todos do mesmo lote por cor). Estoque decrementado transacionalmente.';

create unique index uniq_las_variant_combo on public.las_variants (product_id, color, dye_lot);
create index idx_las_variants_product on public.las_variants (product_id) where available = true;
create index idx_las_variants_company on public.las_variants (company_id);

alter table public.las_variants enable row level security;
alter table public.las_variants force  row level security;

create policy las_variants_select on public.las_variants for select to authenticated using (company_id = app.company_id());
create policy las_variants_insert on public.las_variants for insert to authenticated with check (company_id = app.company_id());
create policy las_variants_update on public.las_variants for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy las_variants_delete on public.las_variants for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.las_variants to authenticated;
grant all on public.las_variants to service_role;

-- ---------------------------------------------------------------------------
-- las_orders — pedidos (clone lingerie_orders + same_lot_guaranteed).
-- ---------------------------------------------------------------------------
create table public.las_orders (
  id                   uuid        primary key default gen_random_uuid(),
  company_id           uuid        not null references public.companies(id) on delete restrict,
  conversation_id      uuid        not null references public.conversations(id) on delete restrict,
  contact_id           uuid        not null references public.contacts(id) on delete restrict,
  status               text        not null default 'aguardando' check (status in
                         ('aguardando','separando','enviado','entregue','recusado','cancelado')),
  fulfillment          text        not null default 'entrega' check (fulfillment in ('entrega','retirada')),
  same_lot_guaranteed  boolean     not null default false,  -- ⭐ exige todos os itens da mesma cor no MESMO lote
  subtotal_cents       integer     not null,
  delivery_fee_cents   integer     not null default 0,
  total_cents          integer     not null,
  delivery_address     text,
  notes                text,
  rejection_reason     text,
  created_at           timestamptz not null default now(),
  status_updated_at    timestamptz not null default now()
);

comment on table public.las_orders is
  'Pedidos do tenant las (camada 8.23). INSERT pelo backend. Nasce ''aguardando'' (gate de aceite). same_lot_guaranteed: quando true, o backend valida que todos os itens da MESMA cor referenciam o MESMO dye_lot (senão 422 mixed_dye_lots). Total materializado. Clone lingerie_orders.';

create index idx_las_orders_company_status on public.las_orders (company_id, status, created_at desc);
create index idx_las_orders_conversation on public.las_orders (conversation_id);

alter table public.las_orders enable row level security;
alter table public.las_orders force  row level security;

create policy las_orders_select on public.las_orders for select to authenticated using (company_id = app.company_id());
create policy las_orders_update on public.las_orders for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.las_orders to authenticated;
grant all on public.las_orders to service_role;

-- ---------------------------------------------------------------------------
-- las_order_items — itens (snapshot cor+lote+preço; clone lingerie_order_items).
-- ---------------------------------------------------------------------------
create table public.las_order_items (
  id                    uuid        primary key default gen_random_uuid(),
  order_id              uuid        not null references public.las_orders(id) on delete cascade,
  variant_id            uuid        not null references public.las_variants(id) on delete restrict,
  qtd                   integer     not null check (qtd > 0),
  unit_price_cents      integer     not null,
  product_name_snapshot text        not null,
  color_snapshot        text        not null,
  dye_lot_snapshot      text        not null
);

comment on table public.las_order_items is
  'Itens do pedido las (camada 8.23) com SNAPSHOT de produto+cor+lote+preço. variant_id on delete restrict (→ 409 variant_in_use). A validação same_lot_guaranteed agrupa por color_snapshot e exige um único dye_lot_snapshot.';

create index idx_las_order_items_order on public.las_order_items (order_id);

alter table public.las_order_items enable row level security;
alter table public.las_order_items force  row level security;

create policy las_order_items_select on public.las_order_items
  for select to authenticated using (
    exists (select 1 from public.las_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.las_order_items to authenticated;
grant all on public.las_order_items to service_role;
