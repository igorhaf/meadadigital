-- =============================================================================
-- 114_comida_onda2.sql
-- Meada — Onda Comida 2 (backlog docs/FEATURES_SUGERIDAS_COMIDA.md #3/#5/#9/#12).
--
--   #3 RETIRADA NO BALCÃO: fulfillment entrega|retirada (espelho sushi) — retirada
--      dispensa endereço E taxa (nem zona nem flat); selo no Kanban.
--   #9 HORÁRIO DO DELIVERY: opens_at/closes_at próprios (nullable = sempre aberto).
--      A IA avisa fora do horário e NÃO fecha pedido (instrução no contexto — o
--      backend também valida com 422 outside_hours defensivo).
--   #12 PEDIDO PARADO: aguardando além de X minutos sem aceite → alerta interno no
--      painel (badge) + lembrete ao cliente? NÃO — o alerta é pro LOJISTA; sem canal
--      do lojista, o painel destaca (stale). Em preparo além de Y minutos → idem.
--      Aqui: markers de varredura ficam DERIVADOS (sem coluna) — o painel calcula
--      pelo status_updated_at; o job auto-CANCELA aguardando muito antigo? Não —
--      cancelamento é humano. Entrega: badge derivada no painel (sem DDL) + job de
--      auto-transição OPCIONAL saiu_entrega→entregue após N horas (toggle OFF).
--   #5 REATIVAÇÃO de inativo (comida_reactivation_log, clone sushi): opt-in OFF.
-- =============================================================================

alter table public.comida_orders
  add column fulfillment text not null default 'entrega' check (fulfillment in ('entrega','retirada'));

-- retirada dispensa endereço → a coluna deixa de ser NOT NULL (entrega segue validada no service).
alter table public.comida_orders
  alter column delivery_address drop not null;

comment on column public.comida_orders.fulfillment is
  'Entrega (endereço obrigatório + taxa) ou retirada no balcão (sem taxa, endereço dispensado) — onda 2, backlog #3.';

alter table public.comida_config
  add column opens_at time,
  add column closes_at time,
  add column auto_deliver_hours integer check (auto_deliver_hours is null or auto_deliver_hours between 1 and 24),
  add column reactivation_enabled     boolean not null default false,
  add column reactivation_days        integer not null default 30 check (reactivation_days between 7 and 365),
  add column reactivation_coupon_code text;

comment on column public.comida_config.opens_at is
  'Horário de abertura do delivery (null = sem janela — sempre aberto). A IA avisa fora do horário; o backend valida (422 outside_hours) — onda 2, backlog #9.';
comment on column public.comida_config.auto_deliver_hours is
  'Se preenchido, pedido em saiu_entrega há mais de N horas vira entregue automaticamente (silencioso) — onda 2, backlog #12. NULL = desligado.';
comment on column public.comida_config.reactivation_enabled is
  'Opt-in da reativação de inativos (onda 2, backlog #5). DESLIGADO por default (lição Baileys).';

create table public.comida_reactivation_log (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  contact_id  uuid        not null references public.contacts(id) on delete cascade,
  had_channel boolean     not null default true,
  sent_at     timestamptz not null default now()
);

create index idx_comida_reactivation_contact on public.comida_reactivation_log (company_id, contact_id, sent_at desc);
alter table public.comida_reactivation_log enable row level security;
grant all on public.comida_reactivation_log to service_role;
