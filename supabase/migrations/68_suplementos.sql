-- =============================================================================
-- 68_suplementos.sql
-- Meada WhatsApp — Camada 8.24 (SM: perfil Suplementos / loja de saúde · nutrição esportiva). CLONA o
-- chassi order-based do COMIDA (47_comida.sql) — pedido + gate de aceite humano + total recalculado +
-- taxa/mínimo + Kanban — combinado com o chassi de VARIANTES COM ESTOQUE do LINGERIE (65), e inaugura
-- DUAS coisas num perfil de VAREJO:
--
--   ESCAPADA 1 — CATÁLOGO DE PRODUTOS COM VARIANTES (sabor × peso/tamanho) E ESTOQUE POR VARIANTE COM
--   DECREMENTO TRANSACIONAL: um produto (sup_products: "Whey Protein") tem N VARIANTES vendáveis
--   (sup_variants: "Chocolate 900g", "Baunilha 2kg"), cada uma com SEU price_cents, sku e stock_quantity.
--   O pedido referencia a VARIANTE. Na criação o backend DECREMENTA o estoque DENTRO da transação com
--   UPDATE condicional `... where stock_quantity >= :qtd`; 0 linhas → 409 out_of_stock e ABORTA o pedido
--   inteiro (rollback). expiry_date por variante = campo administrativo informativo.
--
--   ESCAPADA 2 (o coração) — TRAVA DE SAÚDE / NÃO-PRESCRIÇÃO (espelho LEVE da trava clínica do nutri,
--   adaptada a VAREJO): a IA NUNCA prescreve dosagem/posologia/uso, NUNCA recomenda suplemento como
--   tratamento/conduta por objetivo/sintoma, NUNCA opina sobre saúde/patologia/interação medicamentosa —
--   encaminha a nutricionista/médico/educador físico. Só mostra catálogo, tira dúvida de PRODUTO e monta
--   pedido. A trava vive na persona E no contexto (2 lugares, igual nutri).
--
-- SÓ ENTREGA nesta SM (sem retirada): delivery_address obrigatório; soma delivery_fee. Cliente NÃO é
-- entidade (continua o contact). Cancelar NÃO devolve estoque nesta SM (fase futura).
--
-- Convenções (padrão 30-67): RLS enable+force; policies via app.company_id(); grants authenticated +
-- service_role; orders/order_items INSERT só backend; total/unit_price materializados; categorias
-- hardcoded (SuplementosCategory).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'suplementos'. ESPELHA a CHECK mais completa (62_viagens, 33 perfis) +
-- 'suplementos'. Entra por ÚLTIMO no SCRIPTS de teste (sua lista tem os 34).
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia','cursos','lingerie','moda_infantil','las',
                        'padaria','otica','papelaria','viagens','suplementos'));

-- ---------------------------------------------------------------------------
-- sup_config — taxa de entrega + mínimo (clone comida_config).
-- ---------------------------------------------------------------------------
create table public.sup_config (
  company_id         uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents    integer     not null default 0 check (min_order_cents >= 0),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.sup_config is
  'Config do tenant suplementos (camada 8.24): taxa de entrega + pedido mínimo. 1:1 com company. Ausente → ZERO. Clone comida_config.';

alter table public.sup_config enable row level security;
alter table public.sup_config force  row level security;

create policy sup_config_select on public.sup_config for select to authenticated using (company_id = app.company_id());
create policy sup_config_insert on public.sup_config for insert to authenticated with check (company_id = app.company_id());
create policy sup_config_update on public.sup_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.sup_config to authenticated;
grant all on public.sup_config to service_role;

-- ---------------------------------------------------------------------------
-- sup_products — catálogo (o "pai"). description é informativo de PRODUTO, NÃO dosagem.
-- ---------------------------------------------------------------------------
create table public.sup_products (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  brand       text,        -- marca
  category    text        not null check (category in
                ('proteinas','aminoacidos','vitaminas','pre_treino','emagrecedores','acessorios')),
  description text,        -- informativo de PRODUTO (SEM dosagem/posologia — a IA não usa pra recomendar)
  active      boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.sup_products is
  'Produtos do tenant suplementos (camada 8.24). category hardcoded (SuplementosCategory). description é informativo de PRODUTO, NÃO dosagem/posologia (a IA NÃO usa pra recomendar). As variantes vendáveis (sabor×peso) são sup_variants. delete em uso → 409 product_in_use.';

create index idx_sup_products_company_cat on public.sup_products (company_id, category) where active = true;

alter table public.sup_products enable row level security;
alter table public.sup_products force  row level security;

create policy sup_products_select on public.sup_products for select to authenticated using (company_id = app.company_id());
create policy sup_products_insert on public.sup_products for insert to authenticated with check (company_id = app.company_id());
create policy sup_products_update on public.sup_products for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy sup_products_delete on public.sup_products for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.sup_products to authenticated;
grant all on public.sup_products to service_role;

-- ---------------------------------------------------------------------------
-- sup_variants — ESCAPADA 1: variante vendável (sabor × peso) com ESTOQUE. O SKU real.
-- ---------------------------------------------------------------------------
create table public.sup_variants (
  id             uuid        primary key default gen_random_uuid(),
  company_id     uuid        not null references public.companies(id) on delete restrict,  -- denorm p/ RLS direta
  product_id     uuid        not null references public.sup_products(id) on delete cascade,
  flavor         text,        -- sabor (nullable, ex.: 'Chocolate'); acessório sem sabor
  size_label     text        not null check (length(trim(size_label)) between 1 and 60),  -- peso/tamanho ('900g','2kg','120 caps','600ml')
  sku            text,
  price_cents    integer     not null check (price_cents >= 0),   -- preço DA VARIANTE
  stock_quantity integer     not null default 0 check (stock_quantity >= 0),
  expiry_date    date,        -- administrativo informativo (a IA não promete validade)
  active         boolean     not null default true,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

comment on table public.sup_variants is
  'Variantes (sabor × peso/tamanho) do produto (camada 8.24, ESCAPADA 1). O SKU real: cada sabor×peso tem SEU preço + estoque. O pedido referencia a variante; o estoque é DECREMENTADO transacionalmente na criação (UPDATE condicional stock_quantity >= qtd → out_of_stock se não decrementa). expiry_date é informativo (a IA não promete validade).';

create index idx_sup_variants_product on public.sup_variants (product_id) where active = true;
create index idx_sup_variants_company on public.sup_variants (company_id);
create unique index uniq_sup_variant_sku on public.sup_variants (company_id, sku) where sku is not null;

alter table public.sup_variants enable row level security;
alter table public.sup_variants force  row level security;

create policy sup_variants_select on public.sup_variants for select to authenticated using (company_id = app.company_id());
create policy sup_variants_insert on public.sup_variants for insert to authenticated with check (company_id = app.company_id());
create policy sup_variants_update on public.sup_variants for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy sup_variants_delete on public.sup_variants for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.sup_variants to authenticated;
grant all on public.sup_variants to service_role;

-- ---------------------------------------------------------------------------
-- sup_orders — pedidos (INSERT só backend; clone comida_orders + gate). SÓ ENTREGA.
-- ---------------------------------------------------------------------------
create table public.sup_orders (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  conversation_id    uuid        not null references public.conversations(id) on delete restrict,
  contact_id         uuid        not null references public.contacts(id) on delete restrict,
  status             text        not null default 'aguardando' check (status in
                       ('aguardando','em_preparo','saiu_entrega','entregue','recusado','cancelado')),
  subtotal_cents     integer     not null,
  delivery_fee_cents integer     not null default 0,
  total_cents        integer     not null,
  delivery_address   text        not null,   -- SÓ entrega nesta SM
  notes              text,
  rejection_reason   text,                    -- gate de aceite
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.sup_orders is
  'Pedidos do tenant suplementos (camada 8.24). INSERT pelo backend. Nasce ''aguardando'' (gate de aceite humano: a loja aceita→em_preparo ou recusa→recusado). SÓ entrega (delivery_address NOT NULL). Total materializado. Clone comida_orders.';

create index idx_sup_orders_company_status on public.sup_orders (company_id, status, created_at desc);
create index idx_sup_orders_conversation on public.sup_orders (conversation_id);

alter table public.sup_orders enable row level security;
alter table public.sup_orders force  row level security;

create policy sup_orders_select on public.sup_orders for select to authenticated using (company_id = app.company_id());
create policy sup_orders_update on public.sup_orders for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.sup_orders to authenticated;
grant all on public.sup_orders to service_role;

-- ---------------------------------------------------------------------------
-- sup_order_items — itens (snapshot produto+variante+preço). Clone comida_order_items + variante.
-- ---------------------------------------------------------------------------
create table public.sup_order_items (
  id                     uuid        primary key default gen_random_uuid(),
  order_id               uuid        not null references public.sup_orders(id) on delete cascade,
  product_id             uuid        not null references public.sup_products(id) on delete restrict,
  variant_id             uuid        not null references public.sup_variants(id) on delete restrict,
  qtd                    integer     not null check (qtd > 0),
  unit_price_cents       integer     not null,   -- snapshot do preço da VARIANTE
  product_name_snapshot  text        not null,
  variant_label_snapshot text        not null    -- "Chocolate 900g" (sabor+peso congelados)
);

comment on table public.sup_order_items is
  'Itens do pedido suplementos (camada 8.24) com SNAPSHOT de produto+variante+preço. variant_id on delete restrict (→ 409 variant_in_use). product_id on delete restrict (→ 409 product_in_use).';

create index idx_sup_order_items_order on public.sup_order_items (order_id);

alter table public.sup_order_items enable row level security;
alter table public.sup_order_items force  row level security;

create policy sup_order_items_select on public.sup_order_items
  for select to authenticated using (
    exists (select 1 from public.sup_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.sup_order_items to authenticated;
grant all on public.sup_order_items to service_role;
