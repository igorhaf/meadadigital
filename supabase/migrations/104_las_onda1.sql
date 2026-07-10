-- =============================================================================
-- 104_las_onda1.sql
-- Meada — Onda Lãs 1 (backlog docs/FEATURES_SUGERIDAS_LAS.md #1/#2/#5/#7).
--
--   #1 LISTA DE ESPERA DE DYE LOT (las_waitlist): a ruptura de lote é a maior venda
--      perdida do nicho (projeto grande não mistura tom). A IA registra o interesse
--      (tag <lista_espera_las>) por variante — com a ESCAPADA dye-lot-aware: o
--      interesse pode ser no LOTE exato ou em QUALQUER lote da cor (dye_lot null).
--      Quando o tenant repõe estoque no painel (0 → N), o backend notifica a fila
--      pendente da variante exata E a fila "qualquer lote" da mesma cor.
--   #2 CALCULADORA DE NOVELOS (las_yield_reference): referência de rendimento por
--      peça × fio, EDITADA PELO TENANT; a IA usa SEMPRE como estimativa explícita e,
--      sem referência cadastrada, diz que não tem (nunca inventa dimensionamento).
--   #5 CUPOM (las_coupons, motor comum com.meada.common.coupons — clone lingerie):
--      campo cupom na tag; backend valida e recalcula; inválido NÃO aborta.
--   #7 REATIVAÇÃO de inativo (las_reactivation_log + config, clone sushi/lavanderia):
--      opt-in DESLIGADO por default (lição Baileys); "chegaram lotes novos" com cupom
--      de retorno opcional.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) las_coupons — cupons de desconto (clone lingerie_coupons).
-- ---------------------------------------------------------------------------
create table public.las_coupons (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  code             text        not null check (length(trim(code)) between 1 and 40),
  kind             text        not null check (kind in ('percent','fixed')),
  value            integer     not null check (value >= 0),
  min_order_cents  integer     not null default 0 check (min_order_cents >= 0),
  max_uses         integer     check (max_uses is null or max_uses >= 0),
  uses             integer     not null default 0,
  valid_until      date,
  active           boolean     not null default true,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.las_coupons is
  'Cupons do tenant lãs (onda 1, backlog #5 — ex.: queima de lote antigo "LOTE-XYZ -20%"). A IA passa o code no campo cupom da tag <pedido_las>; o backend valida e aplica com clamp ao subtotal; inválido NÃO aborta. uses incrementa na criação.';

create unique index uniq_las_coupon_code on public.las_coupons (company_id, lower(code));
create index idx_las_coupons_company_active on public.las_coupons (company_id, active) where active = true;

alter table public.las_coupons enable row level security;
grant all on public.las_coupons to service_role;

alter table public.las_orders
  add column discount_cents integer not null default 0 check (discount_cents >= 0),
  add column coupon_id uuid references public.las_coupons(id) on delete set null,
  add column coupon_code_snapshot text;

-- ---------------------------------------------------------------------------
-- 2) las_waitlist — lista de espera de dye lot (escapada do nicho).
-- ---------------------------------------------------------------------------
create table public.las_waitlist (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete restrict,
  contact_id   uuid        not null references public.contacts(id) on delete cascade,
  product_id   uuid        not null references public.las_products(id) on delete cascade,
  color        text        not null,
  dye_lot      text,        -- NULL = qualquer lote da cor serve
  qty_desired  integer     check (qty_desired is null or qty_desired > 0),
  created_at   timestamptz not null default now(),
  notified_at  timestamptz
);

comment on table public.las_waitlist is
  'Lista de espera de dye lot (onda 1, backlog #1). Interesse registrado pela IA em (produto, cor, lote-ou-qualquer); a reposição no painel (0→N) notifica a fila pendente da variante exata E a fila dye_lot NULL da mesma cor, marcando notified_at (idempotente). Demanda reprimida visível = insight de reposição.';

create unique index uniq_las_waitlist_pending
  on public.las_waitlist (contact_id, product_id, color, coalesce(dye_lot, '')) where notified_at is null;
create index idx_las_waitlist_pending
  on public.las_waitlist (company_id, product_id, color) where notified_at is null;

alter table public.las_waitlist enable row level security;
grant all on public.las_waitlist to service_role;

-- ---------------------------------------------------------------------------
-- 3) las_yield_reference — calculadora de rendimento por peça (backlog #2).
-- ---------------------------------------------------------------------------
create table public.las_yield_reference (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  piece_type  text        not null check (length(trim(piece_type)) between 1 and 120),
  yarn_spec   text,        -- gramatura/metragem/categoria do fio (texto livre, nullable)
  skeins      integer     not null check (skeins between 1 and 200),
  notes       text,
  active      boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.las_yield_reference is
  'Referência de rendimento do tenant lãs (onda 1, backlog #2): peça × fio → novelos ESTIMADOS. A IA consulta e apresenta SEMPRE como estimativa ("em média X novelos"); sem referência pra peça, diz que não tem e sugere confirmar com a loja — nunca inventa dimensionamento.';

create index idx_las_yield_company_active on public.las_yield_reference (company_id, active) where active = true;

alter table public.las_yield_reference enable row level security;
grant all on public.las_yield_reference to service_role;

-- ---------------------------------------------------------------------------
-- 4) las_config: reativação (opt-in OFF, clone sushi/lavanderia).
-- ---------------------------------------------------------------------------
alter table public.las_config
  add column reactivation_enabled     boolean not null default false,
  add column reactivation_days        integer not null default 45 check (reactivation_days between 7 and 365),
  add column reactivation_coupon_code text;

comment on column public.las_config.reactivation_enabled is
  'Opt-in da reativação de inativos (backlog #7). DESLIGADO por default: ligar dispara pra base toda (lição do incidente Baileys).';

-- ---------------------------------------------------------------------------
-- 5) las_reactivation_log — idempotência da reativação.
-- ---------------------------------------------------------------------------
create table public.las_reactivation_log (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  contact_id  uuid        not null references public.contacts(id) on delete cascade,
  had_channel boolean     not null default true,
  sent_at     timestamptz not null default now()
);

comment on table public.las_reactivation_log is
  'Log de disparos de reativação lãs (onda 1, backlog #7). Cooldown por contato = a própria janela reactivation_days.';

create index idx_las_reactivation_contact on public.las_reactivation_log (company_id, contact_id, sent_at desc);

alter table public.las_reactivation_log enable row level security;
grant all on public.las_reactivation_log to service_role;
