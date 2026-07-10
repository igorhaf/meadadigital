-- =============================================================================
-- 112_barbearia_onda2.sql
-- Meada — Onda Barbearia 2 (backlog docs/FEATURES_SUGERIDAS_BARBEARIA.md #2/#8/#9).
--
--   #2 REATIVAÇÃO DE INATIVO (opt-in OFF — lição Baileys): cliente em ciclo de
--      corte que sumiu há reactivation_days → 1 convite por ciclo (log próprio,
--      cooldown = janela), com cupom de retorno opcional (motor da onda 1).
--   #8 CHAMAR O PRÓXIMO → ATENDIMENTO: o ticket 'chamado' da fila vira um
--      agendamento IMEDIATO do barbeiro com 1 clique (start=agora, snapshots do
--      ticket, conflito re-verificado) e o ticket muta pra 'atendido' — une fila
--      e agenda; o corte entra no funil (fidelidade/relatórios contam).
--   #9 PÓS-CORTE (avaliação): quando o atendimento vira REALIZADO, agradecimento
--      + review_link — com COOLDOWN por contato (review_request_log) pra não
--      spammar o cliente semanal. Toggle OFF por default (dono decide).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) barber_config: reativação + pós-corte.
-- ---------------------------------------------------------------------------
alter table public.barber_config
  add column reactivation_enabled     boolean not null default false,
  add column reactivation_days        integer not null default 45 check (reactivation_days between 7 and 365),
  add column reactivation_coupon_code text,
  add column post_review_enabled      boolean not null default false,
  add column review_link              text,
  add column review_cooldown_days     integer not null default 90 check (review_cooldown_days between 7 and 365);

comment on column public.barber_config.reactivation_enabled is
  'Opt-in da reativação de inativos (onda 2, backlog #2). DESLIGADO por default: ligar dispara pra base (lição Baileys).';
comment on column public.barber_config.post_review_enabled is
  'Se true, corte REALIZADO dispara agradecimento + review_link com cooldown por contato (onda 2, backlog #9). OFF por default.';

-- ---------------------------------------------------------------------------
-- 2) barber_reactivation_log — idempotência da reativação (clone sushi).
-- ---------------------------------------------------------------------------
create table public.barber_reactivation_log (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  contact_id  uuid        not null references public.contacts(id) on delete cascade,
  had_channel boolean     not null default true,
  sent_at     timestamptz not null default now()
);

create index idx_barber_reactivation_contact on public.barber_reactivation_log (company_id, contact_id, sent_at desc);
alter table public.barber_reactivation_log enable row level security;
grant all on public.barber_reactivation_log to service_role;

-- ---------------------------------------------------------------------------
-- 3) barber_review_log — cooldown do pedido de avaliação por contato.
-- ---------------------------------------------------------------------------
create table public.barber_review_log (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  contact_id  uuid        not null references public.contacts(id) on delete cascade,
  sent_at     timestamptz not null default now()
);

comment on table public.barber_review_log is
  'Cooldown do pedido de avaliação pós-corte (onda 2, backlog #9): 1 pedido por contato a cada review_cooldown_days — cliente semanal não vira spam.';

create index idx_barber_review_contact on public.barber_review_log (company_id, contact_id, sent_at desc);
alter table public.barber_review_log enable row level security;
grant all on public.barber_review_log to service_role;
