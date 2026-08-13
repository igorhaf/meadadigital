-- =============================================================================
-- 69_sushi_funcional.sql
-- Meada — torna FUNCIONAIS (gerenciáveis pelo tenant) várias coisas do perfil sushi que
-- antes eram HARDCODED, e adiciona cupom/fidelidade/retirada/agendamento. Migration ÚNICA e atômica
-- (tudo toca sushi_orders/config, então sobe junto). Blocos:
--   (1) CATEGORIAS do cardápio (era enum SushiCategory + CHECK) → tabela sushi_categories (CRUD).
--   (2) STATUS do pedido (era enum SushiOrderStatus + CHECK + matriz allowedNext) → sushi_order_statuses
--       (label/ordem/inicial/terminal editáveis; TRANSIÇÃO LIVRE entre não-terminais).
--   (3) NOTIFICAÇÕES WhatsApp de mudança de status (texto era fixo em notificationText()) → colunas
--       notify_enabled + notify_text na sushi_order_statuses, editáveis.
--   (6) CUPONS de desconto (sushi_coupons) — a IA passa o code; o backend valida e aplica.
--   (7) FIDELIDADE por contagem de pedidos entregues (sushi_loyalty_config) — desconto automático.
--   (8) DESCONTO no pedido: discount_cents + coupon_id/snapshot + loyalty_applied em sushi_orders.
--   (9) AGENDAMENTO: scheduling_enabled na config.
--  (10) RETIRADA × ENTREGA + agendamento (data+período) em sushi_orders.
--
-- Ambiente sem dados sushi reais (cardápios/pedidos vazios) → conversões diretas + seed dos defaults
-- (ex-enum) p/ toda company sushi. Convenções: RLS enable+force; app.company_id(); grants padrão.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) sushi_categories — categorias gerenciáveis do cardápio.
-- ---------------------------------------------------------------------------
create table public.sushi_categories (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  name        text        not null check (length(trim(name)) between 1 and 80),
  sort_order  integer     not null default 0,
  active      boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.sushi_categories is
  'Categorias do cardápio do tenant sushi (feature funcional — substitui o enum SushiCategory). CRUD pelo tenant. sushi_menu_items.category é FK p/ esta tabela (on delete restrict → 409 category_in_use).';

create unique index uniq_sushi_category_name on public.sushi_categories (company_id, lower(name));
create index idx_sushi_categories_company_active on public.sushi_categories (company_id, sort_order) where active = true;

alter table public.sushi_categories enable row level security;
alter table public.sushi_categories force  row level security;

create policy sushi_cat_select on public.sushi_categories for select to authenticated using (company_id = app.company_id());
create policy sushi_cat_insert on public.sushi_categories for insert to authenticated with check (company_id = app.company_id());
create policy sushi_cat_update on public.sushi_categories for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy sushi_cat_delete on public.sushi_categories for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.sushi_categories to authenticated;
grant all on public.sushi_categories to service_role;

-- ---------------------------------------------------------------------------
-- 2) sushi_order_statuses — estados do pedido + (3) notificações editáveis.
-- ---------------------------------------------------------------------------
create table public.sushi_order_statuses (
  id             uuid        primary key default gen_random_uuid(),
  company_id     uuid        not null references public.companies(id) on delete cascade,
  name           text        not null check (length(trim(name)) between 1 and 60),
  sort_order     integer     not null default 0,
  is_initial     boolean     not null default false,
  is_terminal    boolean     not null default false,
  notify_enabled boolean     not null default false,
  notify_text    text,
  color          text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

comment on table public.sushi_order_statuses is
  'Estados do pedido do tenant sushi + notificações WhatsApp editáveis (feature funcional — substitui o enum SushiOrderStatus + notificationText()). is_initial: 1 por company (pedido nasce aqui). Transição LIVRE entre não-terminais (sem matriz allowedNext). notify_enabled+notify_text: mensagem ao ENTRAR no estado.';

create unique index uniq_sushi_status_name on public.sushi_order_statuses (company_id, lower(name));
create unique index uniq_sushi_status_initial on public.sushi_order_statuses (company_id) where is_initial = true;
create index idx_sushi_statuses_company_sort on public.sushi_order_statuses (company_id, sort_order);

alter table public.sushi_order_statuses enable row level security;
alter table public.sushi_order_statuses force  row level security;

create policy sushi_st_select on public.sushi_order_statuses for select to authenticated using (company_id = app.company_id());
create policy sushi_st_insert on public.sushi_order_statuses for insert to authenticated with check (company_id = app.company_id());
create policy sushi_st_update on public.sushi_order_statuses for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy sushi_st_delete on public.sushi_order_statuses for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.sushi_order_statuses to authenticated;
grant all on public.sushi_order_statuses to service_role;

-- ---------------------------------------------------------------------------
-- 6) sushi_coupons — cupons de desconto.
-- ---------------------------------------------------------------------------
create table public.sushi_coupons (
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

comment on table public.sushi_coupons is
  'Cupons de desconto do tenant sushi (feature funcional). A IA passa o code na tag <pedido>; o backend VALIDA (active + valid_until + min_order + max_uses) e aplica (percent no subtotal ou fixed em centavos), com clamp ao subtotal. uses incrementa na criação. code único (case-insensitive) por company.';

create unique index uniq_sushi_coupon_code on public.sushi_coupons (company_id, lower(code));
create index idx_sushi_coupons_company_active on public.sushi_coupons (company_id, active) where active = true;

alter table public.sushi_coupons enable row level security;
alter table public.sushi_coupons force  row level security;

create policy sushi_coupon_select on public.sushi_coupons for select to authenticated using (company_id = app.company_id());
create policy sushi_coupon_insert on public.sushi_coupons for insert to authenticated with check (company_id = app.company_id());
create policy sushi_coupon_update on public.sushi_coupons for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy sushi_coupon_delete on public.sushi_coupons for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.sushi_coupons to authenticated;
grant all on public.sushi_coupons to service_role;

-- ---------------------------------------------------------------------------
-- 7) sushi_loyalty_config — fidelidade por contagem de pedidos entregues (1:1 company).
-- ---------------------------------------------------------------------------
create table public.sushi_loyalty_config (
  company_id       uuid        primary key references public.companies(id) on delete cascade,
  enabled          boolean     not null default false,
  threshold_orders integer     not null default 10 check (threshold_orders >= 1),
  reward_kind      text        not null default 'percent' check (reward_kind in ('percent','fixed')),
  reward_value     integer     not null default 0 check (reward_value >= 0),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  check (reward_kind <> 'percent' or reward_value between 0 and 100)
);

comment on table public.sushi_loyalty_config is
  'Config de fidelidade por contagem do tenant sushi (feature funcional). enabled+threshold_orders+reward. O backend conta os pedidos ENTREGUES do contato; quando count > 0 e count % threshold == 0, o próximo pedido ganha o reward (percent no subtotal ou fixed em centavos). Sem pontos/saldo — é por contagem.';

alter table public.sushi_loyalty_config enable row level security;
alter table public.sushi_loyalty_config force  row level security;

create policy sushi_loy_select on public.sushi_loyalty_config for select to authenticated using (company_id = app.company_id());
create policy sushi_loy_insert on public.sushi_loyalty_config for insert to authenticated with check (company_id = app.company_id());
create policy sushi_loy_update on public.sushi_loyalty_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.sushi_loyalty_config to authenticated;
grant all on public.sushi_loyalty_config to service_role;

-- ---------------------------------------------------------------------------
-- SEED dos defaults (ex-enum) p/ toda company sushi. Idempotente.
-- ---------------------------------------------------------------------------
insert into public.sushi_categories (company_id, name, sort_order)
select c.id, v.name, v.sort_order
from public.companies c
cross join (values
  ('Entradas', 0), ('Hot rolls', 1), ('Sashimi', 2),
  ('Combinados', 3), ('Bebidas', 4), ('Sobremesas', 5)
) as v(name, sort_order)
where c.profile_id = 'sushi'
on conflict (company_id, lower(name)) do nothing;

insert into public.sushi_order_statuses
  (company_id, name, sort_order, is_initial, is_terminal, notify_enabled, notify_text)
select c.id, v.name, v.sort_order, v.is_initial, v.is_terminal, v.notify_enabled, v.notify_text
from public.companies c
cross join (values
  ('Recebido',          0, true,  false, false, cast(null as text)),
  ('Em preparo',        1, false, false, true,  'Seu pedido entrou em preparo. Já já começa o sushi a aparecer.'),
  ('Saiu pra entrega',  2, false, false, true,  'Seu pedido saiu pra entrega. Em instantes chega aí.'),
  ('Entregue',          3, false, true,  true,  'Pedido entregue. Bom apetite e obrigado pela preferência.'),
  ('Cancelado',         4, false, true,  true,  'Seu pedido foi cancelado. Se quiser refazer, é só me chamar.')
) as v(name, sort_order, is_initial, is_terminal, notify_enabled, notify_text)
where c.profile_id = 'sushi'
on conflict (company_id, lower(name)) do nothing;

insert into public.sushi_loyalty_config (company_id) select id from public.companies where profile_id = 'sushi'
on conflict (company_id) do nothing;

-- ---------------------------------------------------------------------------
-- 4) sushi_menu_items.category: dropar CHECK fixo e religar como FK (nullable).
-- ---------------------------------------------------------------------------
alter table public.sushi_menu_items drop constraint if exists sushi_menu_items_category_check;

update public.sushi_menu_items set category = null
where category is not null
  and category !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

alter table public.sushi_menu_items
  alter column category drop not null,
  alter column category type uuid using (
    case when category ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
         then category::uuid else null end);

alter table public.sushi_menu_items
  add constraint sushi_menu_items_category_fk
  foreign key (category) references public.sushi_categories(id) on delete restrict;

create index if not exists idx_sushi_menu_items_category on public.sushi_menu_items (category);

-- ---------------------------------------------------------------------------
-- 5) sushi_orders.status: dropar CHECK fixo e religar como FK (NOT NULL).
-- ---------------------------------------------------------------------------
alter table public.sushi_orders drop constraint if exists sushi_orders_status_check;
alter table public.sushi_orders alter column status drop default;

update public.sushi_orders set status = null
where status is not null
  and status !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

alter table public.sushi_orders
  alter column status type uuid using (
    case when status ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
         then status::uuid else null end);

alter table public.sushi_orders
  add constraint sushi_orders_status_fk
  foreign key (status) references public.sushi_order_statuses(id) on delete restrict;

-- ---------------------------------------------------------------------------
-- 8) sushi_orders: desconto (cupom + fidelidade).
-- ---------------------------------------------------------------------------
alter table public.sushi_orders
  add column discount_cents       integer not null default 0 check (discount_cents >= 0),
  add column coupon_id            uuid references public.sushi_coupons(id) on delete set null,
  add column coupon_code_snapshot text,
  add column loyalty_applied      boolean not null default false;

comment on column public.sushi_orders.discount_cents is
  'Desconto total aplicado (cupom + fidelidade), materializado. total = subtotal − discount + delivery_fee (entrega) ou subtotal − discount (retirada). Clampado ao subtotal.';

-- ---------------------------------------------------------------------------
-- 9) sushi_restaurant_config: ligar agendamento.
-- ---------------------------------------------------------------------------
alter table public.sushi_restaurant_config
  add column scheduling_enabled boolean not null default false;

comment on column public.sushi_restaurant_config.scheduling_enabled is
  'Quando true, o tenant aceita pedidos AGENDADOS (a IA oferece data+período). false = só pedidos "para agora".';

-- ---------------------------------------------------------------------------
-- 10) sushi_orders: fulfillment (retirada×entrega) + agendamento (data+período).
-- ---------------------------------------------------------------------------
alter table public.sushi_orders
  add column fulfillment      text not null default 'entrega'
    check (fulfillment in ('entrega','retirada')),
  add column scheduled_date   date,
  add column scheduled_period text
    check (scheduled_period is null or scheduled_period in ('agora','manha','tarde','noite'));

comment on column public.sushi_orders.fulfillment is
  'entrega (default): delivery_address obrigatório + soma delivery_fee. retirada: balcão, sem taxa, endereço opcional. Validado no backend.';

-- entrega não tem mais NOT NULL no schema; a regra "entrega exige endereço" é do backend (422 address_required).
alter table public.sushi_orders alter column delivery_address drop not null;
