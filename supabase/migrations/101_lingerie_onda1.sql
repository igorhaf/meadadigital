-- =============================================================================
-- 101_lingerie_onda1.sql
-- Meada — Onda Lingerie 1 (backlog docs/FEATURES_SUGERIDAS_LINGERIE.md — cupom + avise-me).
--
--   CUPOM: clone do motor unificado (com.meada.common.coupons; espelho pizzaria mig 93):
--      lingerie_coupons (percent/fixed, mínimo, validade, max usos, UNIQUE lower(code)) +
--      desconto MATERIALIZADO no pedido. A IA passa SÓ o código no campo `cupom` da tag do
--      pedido; o backend valida e aplica; inválido NÃO aborta; uses incrementa na transação.
--
--   AVISE-ME QUANDO VOLTAR (lingerie_stock_alerts): variante esgotada hoje mata a venda sem
--      rastro. A IA registra o interesse (tag própria, contato da conversa + variant_id);
--      quando o tenant REPÕE o estoque no painel (0 → N), o backend dispara "voltou!" pra
--      fila pendente e marca notified_at (idempotente). Demanda reprimida visível é também
--      insight de reposição.
-- =============================================================================

create table public.lingerie_coupons (
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

create unique index uniq_lg_coupon_code on public.lingerie_coupons (company_id, lower(code));
create index idx_lg_coupons_company_active on public.lingerie_coupons (company_id, active) where active = true;

alter table public.lingerie_coupons enable row level security;
grant all on public.lingerie_coupons to service_role;

alter table public.lingerie_orders
  add column if not exists discount_cents integer not null default 0 check (discount_cents >= 0);
alter table public.lingerie_orders
  add column if not exists coupon_id uuid references public.lingerie_coupons(id) on delete set null;
alter table public.lingerie_orders
  add column if not exists coupon_code_snapshot text;

create table public.lingerie_stock_alerts (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete restrict,
  contact_id   uuid        not null references public.contacts(id) on delete cascade,
  variant_id   uuid        not null references public.lingerie_variants(id) on delete cascade,
  created_at   timestamptz not null default now(),
  notified_at  timestamptz
);

comment on table public.lingerie_stock_alerts is
  'Avise-me quando voltar (onda 1): interesse registrado pela IA em variante esgotada; a reposição no painel (0→N) notifica a fila pendente e marca notified_at.';

create unique index uniq_lg_alert_pending
  on public.lingerie_stock_alerts (contact_id, variant_id) where notified_at is null;
create index idx_lg_alerts_variant_pending
  on public.lingerie_stock_alerts (variant_id) where notified_at is null;

alter table public.lingerie_stock_alerts enable row level security;
grant all on public.lingerie_stock_alerts to service_role;
