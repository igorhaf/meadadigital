-- =============================================================================
-- 106_floricultura_onda1.sql
-- Meada — Onda Floricultura 1 (backlog docs/FEATURES_SUGERIDAS_FLORICULTURA.md #3/#4/#7/#8/#9/#13).
--
--   #3 RECOMPRA DE 1 CLIQUE: o comprador manda flor pras MESMAS pessoas — o contexto
--      da IA ganha o histórico de pedidos do contato (destinatário, itens, endereço)
--      com instrução de oferecer "repetir o buquê da Ana" (só código/prompt, sem DDL).
--   #4 UPSELL PROATIVO: itens do catálogo ganham flag suggestible — a IA sugere 1–2
--      adicionais RELEVANTES do próprio catálogo (nunca inventa item/preço).
--   #7 CUPOM (floricultura_coupons, motor comum — clone adega/lavanderia): campo
--      cupom na tag; backend valida e recalcula; inválido NÃO aborta.
--   #8 FIDELIDADE por contagem (floricultura_loyalty_config): a cada N pedidos
--      ENTREGUES do contato, o próximo ganha o reward ("a cada 5 buquês, brinde").
--   #9 CONFIRMAÇÃO D-1 DA ENTREGA: pedido aceito (em_preparo) com entrega amanhã →
--      aviso ao COMPRADOR confirmando endereço/período. 1x por data
--      (delivery_reminded_date; remarcar REARMA). Toggle default ON.
--   #13 PRESENTE SURPRESA (anonimato): flag anonymous no pedido — a entrega NÃO
--      revela o remetente; badge no painel pro entregador saber.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) floricultura_coupons — cupons de desconto (clone adega/lavanderia).
-- ---------------------------------------------------------------------------
create table public.floricultura_coupons (
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

comment on table public.floricultura_coupons is
  'Cupons do tenant floricultura (onda 1, backlog #7 — primeira compra / data comemorativa). A IA passa o code no campo cupom da tag <pedido_flor>; o backend valida e aplica com clamp ao subtotal; inválido NÃO aborta. uses incrementa na criação.';

create unique index uniq_flor_coupon_code on public.floricultura_coupons (company_id, lower(code));
create index idx_flor_coupons_company_active on public.floricultura_coupons (company_id, active) where active = true;

alter table public.floricultura_coupons enable row level security;
alter table public.floricultura_coupons force  row level security;

create policy flor_coupon_select on public.floricultura_coupons for select to authenticated using (company_id = app.company_id());
create policy flor_coupon_insert on public.floricultura_coupons for insert to authenticated with check (company_id = app.company_id());
create policy flor_coupon_update on public.floricultura_coupons for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy flor_coupon_delete on public.floricultura_coupons for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.floricultura_coupons to authenticated;
grant all on public.floricultura_coupons to service_role;

-- ---------------------------------------------------------------------------
-- 2) floricultura_loyalty_config — fidelidade por contagem (1:1 company).
-- ---------------------------------------------------------------------------
create table public.floricultura_loyalty_config (
  company_id       uuid        primary key references public.companies(id) on delete cascade,
  enabled          boolean     not null default false,
  threshold_orders integer     not null default 5 check (threshold_orders >= 1),
  reward_kind      text        not null default 'percent' check (reward_kind in ('percent','fixed')),
  reward_value     integer     not null default 0 check (reward_value >= 0),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  check (reward_kind <> 'percent' or reward_value between 0 and 100)
);

comment on table public.floricultura_loyalty_config is
  'Fidelidade por contagem do tenant floricultura (onda 1, backlog #8 — "a cada 5 buquês, 1 arranjo de brinde"). O backend conta os pedidos status=entregue do contato ANTES de inserir o novo; count > 0 e count % threshold == 0 → reward no pedido.';

alter table public.floricultura_loyalty_config enable row level security;
alter table public.floricultura_loyalty_config force  row level security;

create policy flor_loy_select on public.floricultura_loyalty_config for select to authenticated using (company_id = app.company_id());
create policy flor_loy_insert on public.floricultura_loyalty_config for insert to authenticated with check (company_id = app.company_id());
create policy flor_loy_update on public.floricultura_loyalty_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.floricultura_loyalty_config to authenticated;
grant all on public.floricultura_loyalty_config to service_role;

insert into public.floricultura_loyalty_config (company_id) select id from public.companies where profile_id = 'floricultura'
on conflict (company_id) do nothing;

-- ---------------------------------------------------------------------------
-- 3) floricultura_orders: desconto + anonimato + marker do lembrete D-1.
-- ---------------------------------------------------------------------------
alter table public.floricultura_orders
  add column discount_cents         integer not null default 0 check (discount_cents >= 0),
  add column coupon_id              uuid references public.floricultura_coupons(id) on delete set null,
  add column coupon_code_snapshot   text,
  add column loyalty_applied        boolean not null default false,
  add column anonymous              boolean not null default false,
  add column delivery_reminded_date date;

comment on column public.floricultura_orders.anonymous is
  'PRESENTE SURPRESA (onda 1, backlog #13): a entrega NÃO revela o remetente (cartão sem assinatura; entregador orientado pelo painel).';
comment on column public.floricultura_orders.delivery_reminded_date is
  'delivery_date quando o aviso D-1 ao comprador foi enviado (backlog #9). Remarcar a entrega REARMA (marker <> delivery_date).';

create index idx_flor_orders_delivery_due
  on public.floricultura_orders (delivery_date)
  where status = 'em_preparo';

-- ---------------------------------------------------------------------------
-- 4) floricultura_config: toggle do lembrete D-1.
-- ---------------------------------------------------------------------------
alter table public.floricultura_config
  add column delivery_reminder_enabled boolean not null default true;

comment on column public.floricultura_config.delivery_reminder_enabled is
  'Se true (default), o FloriculturaReminderJob avisa o COMPRADOR na véspera da entrega (confirma endereço/período — corta entrega furada).';

-- ---------------------------------------------------------------------------
-- 5) floricultura_catalog_items: flag de upsell.
-- ---------------------------------------------------------------------------
alter table public.floricultura_catalog_items
  add column suggestible boolean not null default false;

comment on column public.floricultura_catalog_items.suggestible is
  'Se true, a IA pode SUGERIR este item como adicional no fechamento do pedido (cartão especial, chocolate, vaso — onda 1, backlog #4).';
