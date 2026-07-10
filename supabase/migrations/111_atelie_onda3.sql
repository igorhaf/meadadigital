-- =============================================================================
-- 111_atelie_onda3.sql
-- Meada — Onda Ateliê 3 (backlog docs/FEATURES_SUGERIDAS_ATELIE.md #3/#6/#7).
--
--   #6 CONFIRMAÇÃO DE PROVA: fecha o loop do lembrete de véspera (onda 1) — o
--      cliente responde SIM e a IA emite <confirmacao_prova>; a prova ganha
--      confirmed_at (metadado — o status segue BINÁRIO pendente/realizada) e o
--      painel mostra quem confirmou. Pedido de remarcação segue pra equipe
--      (a IA não remarca prova — gestão é do painel).
--   #7 PÓS-ENTREGA: ao entrar em REALIZADA (peça entregue), agradecimento +
--      review_link (se houver) + convite de indicação. Toggle default ON.
--   #3 REATIVAÇÃO DE INATIVO (opt-in OFF — disparo à base, lição Baileys):
--      contato com última proposta REALIZADA há reactivation_days sem proposta
--      ativa → 1 convite por ciclo (log próprio, cooldown = a janela).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) atelie_fittings: confirmação pelo cliente (metadado, rearmada ao remarcar).
-- ---------------------------------------------------------------------------
alter table public.atelie_fittings
  add column confirmed_at timestamptz,
  add column confirmed_due_date date;

comment on column public.atelie_fittings.confirmed_at is
  'Quando o cliente confirmou presença na prova via <confirmacao_prova> (onda 3, backlog #6). Metadado — o status segue binário.';
comment on column public.atelie_fittings.confirmed_due_date is
  'due_date que o cliente confirmou — remarcar a prova invalida a confirmação (marker <> due_date).';

-- ---------------------------------------------------------------------------
-- 2) atelie_config: pós-entrega + reativação.
-- ---------------------------------------------------------------------------
alter table public.atelie_config
  add column post_delivery_enabled  boolean not null default true,
  add column review_link            text,
  add column reactivation_enabled   boolean not null default false,
  add column reactivation_days      integer not null default 90 check (reactivation_days between 7 and 730);

comment on column public.atelie_config.post_delivery_enabled is
  'Se true (default), ao entrar em REALIZADA o cliente recebe agradecimento + review_link (se houver) + convite de indicação (onda 3, backlog #7).';
comment on column public.atelie_config.reactivation_enabled is
  'Opt-in da reativação de inativos (onda 3, backlog #3). DESLIGADO por default: ligar dispara pra base (lição Baileys).';

-- ---------------------------------------------------------------------------
-- 3) atelie_reactivation_log — idempotência da reativação (clone sushi/lavanderia).
-- ---------------------------------------------------------------------------
create table public.atelie_reactivation_log (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  contact_id  uuid        not null references public.contacts(id) on delete cascade,
  had_channel boolean     not null default true,
  sent_at     timestamptz not null default now()
);

comment on table public.atelie_reactivation_log is
  'Log de disparos de reativação do ateliê (onda 3, backlog #3). Cooldown por contato = a própria janela reactivation_days.';

create index idx_atelie_reactivation_contact on public.atelie_reactivation_log (company_id, contact_id, sent_at desc);

alter table public.atelie_reactivation_log enable row level security;
grant all on public.atelie_reactivation_log to service_role;
