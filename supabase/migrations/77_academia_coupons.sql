-- =============================================================================
-- 77_academia_coupons.sql
-- Meada — Academia (camada 7.7): CUPOM de desconto (backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md #10).
--
-- Espelho de sushi_coupons (migration 69): o tenant academia cadastra cupons de desconto
-- (percent 1..100 no subtotal, ou fixed em centavos), com pedido mínimo, limite de usos e
-- validade opcionais. O backend VALIDA (active + valid_until + min_cents + max_uses) e devolve
-- o desconto (com clamp ao subtotal). uses incrementa quando um cupom é efetivamente aplicado.
--
-- Convenções (padrão das migrations 36/72): RLS enable + force; policies via app.company_id();
-- grants authenticated (select/insert/update/delete) + service_role all. code único por company.
-- =============================================================================

create table public.academia_coupons (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  code        text        not null check (length(trim(code)) between 1 and 40),
  kind        text        not null check (kind in ('percent','fixed')),
  value       integer     not null check (value >= 0),
  min_cents   integer     not null default 0 check (min_cents >= 0),
  max_uses    integer     check (max_uses is null or max_uses >= 0),
  uses        integer     not null default 0 check (uses >= 0),
  valid_until date,
  active      boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  check (kind <> 'percent' or value between 1 and 100)
);

-- Código único por company CASE-INSENSITIVE (espelha uniq_sushi_coupon_code da migration 69):
-- o lookup do backend já é lower(code) = lower(?) — o UNIQUE tem de bater com ele, senão
-- "OFF10" e "off10" coexistem e a validação fica ambígua.
create unique index uniq_academia_coupon_code on public.academia_coupons (company_id, lower(code));

comment on table public.academia_coupons is
  'Cupons de desconto do tenant academia (camada 7.7). kind percent (value 1..100, aplicado no subtotal) ou fixed (value em centavos). O backend valida (active + valid_until + min_cents + max_uses) e devolve o desconto com clamp ao subtotal. uses incrementa quando aplicado. code único por company.';

create index idx_academia_coupons_company_active on public.academia_coupons (company_id, active)
  where active = true;

alter table public.academia_coupons enable row level security;
alter table public.academia_coupons force  row level security;

create policy academia_coupons_select on public.academia_coupons
  for select to authenticated using (company_id = app.company_id());
create policy academia_coupons_insert on public.academia_coupons
  for insert to authenticated with check (company_id = app.company_id());
create policy academia_coupons_update on public.academia_coupons
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy academia_coupons_delete on public.academia_coupons
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.academia_coupons to authenticated;
grant all on public.academia_coupons to service_role;
