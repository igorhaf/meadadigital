-- (Clone da onda adega, mig 80 — backlog pizzaria #1/#2; upsell #3 é só persona/cache, sem DDL.)
-- =============================================================================
-- 93_pizzaria_onda1.sql
-- Meada — Pizzaria (camada 8.9): CUPOM de desconto + FIDELIDADE por contagem
-- (backlog docs/FEATURES_SUGERIDAS_PIZZARIA.md #1 e #2). Clone do chassi do sushi
-- (69_sushi_funcional.sql §6/§7/§8): pizzaria_coupons + pizzaria_loyalty_config +
-- colunas de desconto em pizzaria_orders. Clone da onda adega (mig 80): cupom/fidelidade
-- só entram DEPOIS do age_confirmed, no recálculo do backend.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) pizzaria_coupons — cupons de desconto.
-- ---------------------------------------------------------------------------
create table public.pizzaria_coupons (
  id              uuid        primary key default gen_random_uuid(),
  company_id      uuid        not null references public.companies(id) on delete cascade,
  code            text        not null check (length(trim(code)) between 1 and 40),
  kind            text        not null check (kind in ('percent','fixed')),
  value           integer     not null check (value >= 0),
  min_order_cents integer     not null default 0 check (min_order_cents >= 0),
  max_uses        integer     check (max_uses is null or max_uses >= 0),
  uses            integer     not null default 0 check (uses >= 0),
  valid_until     date,
  active          boolean     not null default true,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  check (kind <> 'percent' or value between 1 and 100)
);

comment on table public.pizzaria_coupons is
  'Cupons de desconto do tenant pizzaria (camada 8.9, backlog FEATURES_SUGERIDAS_PIZZARIA #1). A IA passa o code no campo cupom da tag <pedido_pizzaria>; o backend VALIDA (active + valid_until + min_order + max_uses) e aplica (percent no subtotal ou fixed em centavos), com clamp ao subtotal; cupom inválido NÃO aborta o pedido (sai sem desconto). uses incrementa na criação. code único (case-insensitive) por company.';

create unique index uniq_pizzaria_coupon_code on public.pizzaria_coupons (company_id, lower(code));
create index idx_pizzaria_coupons_company_active on public.pizzaria_coupons (company_id, active) where active = true;

alter table public.pizzaria_coupons enable row level security;
alter table public.pizzaria_coupons force  row level security;

create policy pizzaria_coupon_select on public.pizzaria_coupons for select to authenticated using (company_id = app.company_id());
create policy pizzaria_coupon_insert on public.pizzaria_coupons for insert to authenticated with check (company_id = app.company_id());
create policy pizzaria_coupon_update on public.pizzaria_coupons for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy pizzaria_coupon_delete on public.pizzaria_coupons for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.pizzaria_coupons to authenticated;
grant all on public.pizzaria_coupons to service_role;

-- ---------------------------------------------------------------------------
-- 2) pizzaria_loyalty_config — fidelidade por contagem de pedidos entregues (1:1 company).
-- ---------------------------------------------------------------------------
create table public.pizzaria_loyalty_config (
  company_id       uuid        primary key references public.companies(id) on delete cascade,
  enabled          boolean     not null default false,
  threshold_orders integer     not null default 10 check (threshold_orders >= 1),
  reward_kind      text        not null default 'percent' check (reward_kind in ('percent','fixed')),
  reward_value     integer     not null default 0 check (reward_value >= 0),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  check (reward_kind <> 'percent' or reward_value between 0 and 100)
);

comment on table public.pizzaria_loyalty_config is
  'Config de fidelidade por contagem do tenant pizzaria (camada 8.9, backlog FEATURES_SUGERIDAS_PIZZARIA #2). enabled+threshold_orders+reward. O backend conta os pedidos status=entregue do contato ANTES de inserir o novo; quando count > 0 e count % threshold == 0, o pedido ganha o reward (percent no subtotal ou fixed em centavos). Sem pontos/saldo — é por contagem (a pizzaria vive de recompra).';

alter table public.pizzaria_loyalty_config enable row level security;
alter table public.pizzaria_loyalty_config force  row level security;

create policy pizzaria_loy_select on public.pizzaria_loyalty_config for select to authenticated using (company_id = app.company_id());
create policy pizzaria_loy_insert on public.pizzaria_loyalty_config for insert to authenticated with check (company_id = app.company_id());
create policy pizzaria_loy_update on public.pizzaria_loyalty_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.pizzaria_loyalty_config to authenticated;
grant all on public.pizzaria_loyalty_config to service_role;

-- ---------------------------------------------------------------------------
-- 3) pizzaria_orders: desconto (cupom + fidelidade), espelho do sushi (mig 69 §8).
-- ---------------------------------------------------------------------------
alter table public.pizzaria_orders
  add column discount_cents       integer not null default 0 check (discount_cents >= 0),
  add column coupon_id            uuid references public.pizzaria_coupons(id) on delete set null,
  add column coupon_code_snapshot text,
  add column loyalty_applied      boolean not null default false;

comment on column public.pizzaria_orders.discount_cents is
  'Desconto total aplicado (cupom + fidelidade), materializado. total = subtotal − discount + delivery_fee. Clampado ao subtotal.';

-- ---------------------------------------------------------------------------
-- 4) SEED idempotente da config de fidelidade p/ toda company pizzaria.
-- ---------------------------------------------------------------------------
insert into public.pizzaria_loyalty_config (company_id) select id from public.companies where profile_id = 'pizzaria'
on conflict (company_id) do nothing;
