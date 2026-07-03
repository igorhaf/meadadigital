-- =============================================================================
-- 85_comida_onda1.sql
-- Meada — Onda Comida (backlog docs/FEATURES_SUGERIDAS_COMIDA.md #1/#2/#4/#8/#10/#15).
--
-- Fecha as features executáveis do nicho sem bloqueador transversal E sem colidir com o WIP
-- não-commitado do working tree (drag-drop do Kanban nas telas de pedidos + extração de config):
-- por isso #3 (retirada no balcão — exige selo de fulfillment no Kanban) e #9 (horário do delivery
-- — toca o config em extração) ficam para quando esse WIP fechar. NPS/reativação esperam o motor
-- de campanha (Onda 3); pagamento online espera o #50; aniversário exige dado que o contato não tem.
--
--   #1 CUPOM (comida_coupons): clone do motor sushi/adega — a IA passa SÓ o código no campo
--      `cupom` da tag <pedido_comida>; o backend valida (active+validade+mínimo+usos) e aplica;
--      inválido NÃO aborta (sai sem desconto); uses incrementa na MESMA transação do pedido.
--   #2 FIDELIDADE por contagem (comida_loyalty_config, 1:1): conta os pedidos 'entregue' do
--      contato ANTES do insert; count > 0 && count % threshold == 0 → desconto automático.
--   #8 TAXA POR BAIRRO/ZONA (comida_delivery_zones): a taxa flat da config vira FALLBACK; a IA
--      pergunta o bairro, escolhe a zona da lista do contexto e passa `zona_id` na tag; o backend
--      resolve a taxa da zona (snapshot do nome no pedido). Zona ausente/inválida → taxa flat.
--   #4 UPSELL na persona (sem DDL): UMA sugestão de complemento do PRÓPRIO cardápio (bebida/
--      sobremesa/adicional) no fechamento, no máximo uma vez, sem insistir.
--   #10 ENDEREÇO SALVO (sem DDL): o contexto injeta o endereço do ÚLTIMO pedido do contato —
--      a IA oferece reusar ("mesmo endereço da última vez?") em vez de pedir pra digitar de novo.
--   #15 RELATÓRIOS (sem DDL): faturamento por mês, ticket médio, top itens e horário de pico
--      sobre os pedidos ENTREGUES (valor líquido).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- #1 — comida_coupons: clone do motor de cupom (sushi 69 / adega 80).
-- ---------------------------------------------------------------------------
create table public.comida_coupons (
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

comment on table public.comida_coupons is
  'Cupons de desconto do tenant comida (onda 1, backlog #1 — clone do motor sushi/adega). A IA passa o code no campo cupom da tag <pedido_comida>; o backend VALIDA (active + valid_until + min_order sobre o subtotal + max_uses) e aplica com clamp; cupom inválido NÃO aborta o pedido (sai sem desconto). uses incrementa na criação. code único (case-insensitive) por company.';

create unique index uniq_comida_coupon_code on public.comida_coupons (company_id, lower(code));
create index idx_comida_coupons_company_active on public.comida_coupons (company_id, active) where active = true;

alter table public.comida_coupons enable row level security;
alter table public.comida_coupons force  row level security;

create policy comida_coupon_select on public.comida_coupons for select to authenticated using (company_id = app.company_id());
create policy comida_coupon_insert on public.comida_coupons for insert to authenticated with check (company_id = app.company_id());
create policy comida_coupon_update on public.comida_coupons for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy comida_coupon_delete on public.comida_coupons for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.comida_coupons to authenticated;
grant all on public.comida_coupons to service_role;

-- ---------------------------------------------------------------------------
-- #2 — comida_loyalty_config: fidelidade por contagem de pedidos entregues (1:1 company).
-- ---------------------------------------------------------------------------
create table public.comida_loyalty_config (
  company_id       uuid        primary key references public.companies(id) on delete cascade,
  enabled          boolean     not null default false,
  threshold_orders integer     not null default 10 check (threshold_orders >= 1),
  reward_kind      text        not null default 'percent' check (reward_kind in ('percent','fixed')),
  reward_value     integer     not null default 0 check (reward_value >= 0),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  check (reward_kind <> 'percent' or reward_value between 0 and 100)
);

comment on table public.comida_loyalty_config is
  'Config de fidelidade por contagem do tenant comida (onda 1, backlog #2 — clone adega/sushi). enabled+threshold_orders+reward. O backend conta os pedidos status=entregue do contato ANTES de inserir o novo; quando count > 0 e count % threshold == 0, o pedido ganha o reward. Em delivery o cliente pede toda semana — fidelidade trava a migração pro concorrente.';

alter table public.comida_loyalty_config enable row level security;
alter table public.comida_loyalty_config force  row level security;

create policy comida_loy_select on public.comida_loyalty_config for select to authenticated using (company_id = app.company_id());
create policy comida_loy_insert on public.comida_loyalty_config for insert to authenticated with check (company_id = app.company_id());
create policy comida_loy_update on public.comida_loyalty_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.comida_loyalty_config to authenticated;
grant all on public.comida_loyalty_config to service_role;

-- ---------------------------------------------------------------------------
-- #8 — comida_delivery_zones: taxa de entrega por bairro/zona (flat da config vira fallback).
-- ---------------------------------------------------------------------------
create table public.comida_delivery_zones (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  name        text        not null check (length(trim(name)) between 1 and 120),  -- "Centro", "Zona Sul"...
  fee_cents   integer     not null check (fee_cents >= 0),
  active      boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.comida_delivery_zones is
  'Zonas de entrega com taxa própria do tenant comida (onda 1, backlog #8). A IA pergunta o bairro, escolhe a zona da lista do contexto (id EXATO) e passa zona_id na tag <pedido_comida>; o backend resolve a taxa e SNAPSHOTA o nome no pedido. Zona ausente/inválida/inativa → taxa FLAT da config (fallback — nunca aborta). Para de subsidiar entrega longe.';

create unique index uniq_comida_zone_name on public.comida_delivery_zones (company_id, lower(name));
create index idx_comida_zones_company_active on public.comida_delivery_zones (company_id, active) where active = true;

alter table public.comida_delivery_zones enable row level security;
alter table public.comida_delivery_zones force  row level security;

create policy comida_zone_select on public.comida_delivery_zones for select to authenticated using (company_id = app.company_id());
create policy comida_zone_insert on public.comida_delivery_zones for insert to authenticated with check (company_id = app.company_id());
create policy comida_zone_update on public.comida_delivery_zones for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy comida_zone_delete on public.comida_delivery_zones for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.comida_delivery_zones to authenticated;
grant all on public.comida_delivery_zones to service_role;

-- ---------------------------------------------------------------------------
-- Desconto + zona no pedido (espelho adega mig 80 §3, + snapshot de zona do #8).
-- ---------------------------------------------------------------------------
alter table public.comida_orders
  add column discount_cents       integer not null default 0 check (discount_cents >= 0),
  add column coupon_id            uuid references public.comida_coupons(id) on delete set null,
  add column coupon_code_snapshot text,
  add column loyalty_applied      boolean not null default false,
  add column zone_name_snapshot   text;

comment on column public.comida_orders.discount_cents is
  'Desconto total aplicado (cupom + fidelidade), materializado. total = subtotal − discount + delivery_fee. Clampado ao subtotal.';
comment on column public.comida_orders.zone_name_snapshot is
  'Nome da zona de entrega usada na taxa (onda 1, backlog #8) — snapshot; null = taxa flat da config.';

-- ---------------------------------------------------------------------------
-- SEED idempotente da config de fidelidade p/ toda company comida.
-- ---------------------------------------------------------------------------
insert into public.comida_loyalty_config (company_id) select id from public.companies where profile_id = 'comida'
on conflict (company_id) do nothing;
