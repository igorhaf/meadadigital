-- =============================================================================
-- 103_lavanderia_onda1.sql
-- Meada — Onda Lavanderia 1 (backlog docs/FEATURES_SUGERIDAS_LAVANDERIA.md #2/#3/#5/#6/#7/#14).
--
--   #2 EXPRESS/24h com sobretaxa: flag express no pedido encurta o turnaround
--      (express_turnaround_days da config, substitui o MAX dos itens) e soma
--      express_surcharge_pct% do subtotal ao total materializado. A IA informa a
--      sobretaxa da config e emite express:true na tag — nunca inventa o valor.
--   #6 CUPOM (lavanderia_coupons, motor comum com.meada.common.coupons — clone adega):
--      a IA registra o código no campo "cupom" da tag; o backend valida e recalcula;
--      cupom inválido NÃO aborta (sai sem desconto).
--   #5 FIDELIDADE por contagem (lavanderia_loyalty_config, clone adega): a cada
--      threshold pedidos ENTREGUES do contato, o próximo ganha o reward.
--   #3 REATIVAÇÃO de inativo (lavanderia_reactivation_log, clone sushi): opt-in
--      DESLIGADO por default (lição Baileys); job diário chama de volta quem não
--      pede há reactivation_days, com cupom de retorno opcional.
--   #7 LEMBRETE DE COLETA D-1: pedido aguardando com coleta amanhã → "alguém em
--      casa?" 1x por data (collect_reminded_date; remarcar REARMA).
--   #14 LEMBRETE DE PRONTO PARADO: pedido em 'pronto' há ready_reminder_days →
--      cobra a combinação da entrega 1x por episódio (ready_reminded_at vs
--      status_updated_at).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) lavanderia_coupons — cupons de desconto (clone adega_coupons).
-- ---------------------------------------------------------------------------
create table public.lavanderia_coupons (
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

comment on table public.lavanderia_coupons is
  'Cupons de desconto do tenant lavanderia (onda 1, backlog #6). A IA passa o code no campo cupom da tag <pedido_lavanderia>; o backend VALIDA (active + valid_until + min_order + max_uses) e aplica (percent no subtotal ou fixed em centavos), com clamp ao subtotal; cupom inválido NÃO aborta o pedido (sai sem desconto). uses incrementa na criação. code único (case-insensitive) por company.';

create unique index uniq_lav_coupon_code on public.lavanderia_coupons (company_id, lower(code));
create index idx_lav_coupons_company_active on public.lavanderia_coupons (company_id, active) where active = true;

alter table public.lavanderia_coupons enable row level security;
alter table public.lavanderia_coupons force  row level security;

create policy lav_coupon_select on public.lavanderia_coupons for select to authenticated using (company_id = app.company_id());
create policy lav_coupon_insert on public.lavanderia_coupons for insert to authenticated with check (company_id = app.company_id());
create policy lav_coupon_update on public.lavanderia_coupons for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy lav_coupon_delete on public.lavanderia_coupons for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.lavanderia_coupons to authenticated;
grant all on public.lavanderia_coupons to service_role;

-- ---------------------------------------------------------------------------
-- 2) lavanderia_loyalty_config — fidelidade por contagem (clone adega, 1:1 company).
-- ---------------------------------------------------------------------------
create table public.lavanderia_loyalty_config (
  company_id       uuid        primary key references public.companies(id) on delete cascade,
  enabled          boolean     not null default false,
  threshold_orders integer     not null default 10 check (threshold_orders >= 1),
  reward_kind      text        not null default 'percent' check (reward_kind in ('percent','fixed')),
  reward_value     integer     not null default 0 check (reward_value >= 0),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  check (reward_kind <> 'percent' or reward_value between 0 and 100)
);

comment on table public.lavanderia_loyalty_config is
  'Fidelidade por contagem do tenant lavanderia (onda 1, backlog #5). O backend conta os pedidos status=entregue do contato ANTES de inserir o novo; quando count > 0 e count % threshold == 0, o pedido ganha o reward (percent no subtotal ou fixed em centavos). Sem pontos/saldo — é por contagem (a roupa suja volta toda semana).';

alter table public.lavanderia_loyalty_config enable row level security;
alter table public.lavanderia_loyalty_config force  row level security;

create policy lav_loy_select on public.lavanderia_loyalty_config for select to authenticated using (company_id = app.company_id());
create policy lav_loy_insert on public.lavanderia_loyalty_config for insert to authenticated with check (company_id = app.company_id());
create policy lav_loy_update on public.lavanderia_loyalty_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.lavanderia_loyalty_config to authenticated;
grant all on public.lavanderia_loyalty_config to service_role;

insert into public.lavanderia_loyalty_config (company_id) select id from public.companies where profile_id = 'lavanderia'
on conflict (company_id) do nothing;

-- ---------------------------------------------------------------------------
-- 3) lavanderia_orders: desconto + express + markers dos lembretes.
-- ---------------------------------------------------------------------------
alter table public.lavanderia_orders
  add column discount_cents          integer     not null default 0 check (discount_cents >= 0),
  add column coupon_id               uuid        references public.lavanderia_coupons(id) on delete set null,
  add column coupon_code_snapshot    text,
  add column loyalty_applied         boolean     not null default false,
  add column express                 boolean     not null default false,
  add column express_surcharge_cents integer     not null default 0 check (express_surcharge_cents >= 0),
  add column collect_reminded_date   date,
  add column ready_reminded_at       timestamptz;

comment on column public.lavanderia_orders.discount_cents is
  'Desconto total (cupom + fidelidade), materializado. total = subtotal − discount + delivery_fee + express_surcharge. Clampado ao subtotal.';
comment on column public.lavanderia_orders.express is
  'Pedido EXPRESS (onda 1, backlog #2): turnaround da config express_turnaround_days (substitui o MAX dos itens) + sobretaxa express_surcharge_cents materializada.';
comment on column public.lavanderia_orders.collect_reminded_date is
  'Data da coleta quando o lembrete D-1 foi enviado (backlog #7). Remarcar a coleta REARMA (marker <> collect_date).';
comment on column public.lavanderia_orders.ready_reminded_at is
  'Quando o lembrete de pronto-parado foi enviado (backlog #14). Rearmado por episódio (marker < status_updated_at).';

create index idx_lav_orders_collect_due
  on public.lavanderia_orders (collect_date)
  where status = 'aguardando';
create index idx_lav_orders_ready
  on public.lavanderia_orders (status_updated_at)
  where status = 'pronto';

-- ---------------------------------------------------------------------------
-- 4) lavanderia_config: express + toggles dos lembretes + reativação.
-- ---------------------------------------------------------------------------
alter table public.lavanderia_config
  add column express_enabled          boolean not null default true,
  add column express_surcharge_pct    integer not null default 50 check (express_surcharge_pct between 0 and 300),
  add column express_turnaround_days  integer not null default 1 check (express_turnaround_days between 0 and 30),
  add column collect_reminder_enabled boolean not null default true,
  add column ready_reminder_enabled   boolean not null default true,
  add column ready_reminder_days      integer not null default 2 check (ready_reminder_days between 1 and 30),
  add column reactivation_enabled     boolean not null default false,
  add column reactivation_days        integer not null default 30 check (reactivation_days between 7 and 365),
  add column reactivation_coupon_code text;

comment on column public.lavanderia_config.express_enabled is
  'Se true (default), a IA pode oferecer o serviço EXPRESS (turnaround curto + sobretaxa) — backlog #2.';
comment on column public.lavanderia_config.reactivation_enabled is
  'Opt-in da reativação de inativos (backlog #3). DESLIGADO por default: ligar dispara pra base toda (lição do incidente Baileys).';
comment on column public.lavanderia_config.reactivation_coupon_code is
  'Cupom de retorno opcional mencionado na mensagem de reativação (só quando ativo/válido).';

-- ---------------------------------------------------------------------------
-- 5) lavanderia_reactivation_log — idempotência da reativação (clone sushi).
-- ---------------------------------------------------------------------------
create table public.lavanderia_reactivation_log (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  contact_id  uuid        not null references public.contacts(id) on delete cascade,
  had_channel boolean     not null default true,
  sent_at     timestamptz not null default now()
);

comment on table public.lavanderia_reactivation_log is
  'Log de disparos de reativação lavanderia (onda 1, backlog #3). Cooldown por contato = a própria janela reactivation_days. had_channel=false quando o contato não tinha conversa resolúvel (marcado sem envio).';

create index idx_lav_reactivation_contact on public.lavanderia_reactivation_log (company_id, contact_id, sent_at desc);

alter table public.lavanderia_reactivation_log enable row level security;
grant all on public.lavanderia_reactivation_log to service_role;
