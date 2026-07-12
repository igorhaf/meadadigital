-- =============================================================================
-- 118_subscriptions.sql
-- Meada — Assinaturas do produto (pagamento RECORRENTE via Mercado Pago Preapproval).
-- Quando alguém assina o plano de um nicho (card de plano da página institucional), o backend
-- cria uma assinatura transparente no Mercado Pago (POST /preapproval, cobrança mensal) e
-- registra aqui. O webhook do MP atualiza o status a cada cobrança.
--
-- Tabela de PLATAFORMA (não-tenant): só service_role (o backend Spring gerencia; o público
-- interage via endpoints /public/**). RLS enable+force, sem grant a anon/authenticated.
--
-- profile_id é HARDCODED em ProfileType (sem FK/CHECK — validação app-level no controller).
-- =============================================================================

create table public.subscriptions (
  id                 uuid        primary key default gen_random_uuid(),
  mp_preapproval_id  text        unique,                          -- id da assinatura no Mercado Pago
  payer_email        text        not null,
  profile_id         text        not null,                       -- nicho assinado (ProfileType)
  amount_cents       integer     not null check (amount_cents > 0),
  currency           text        not null default 'BRL',
  -- pending: criada, aguardando 1ª cobrança; authorized: ativa; paused; cancelled.
  status             text        not null default 'pending'
                       check (status in ('pending', 'authorized', 'paused', 'cancelled')),
  external_reference text,                                        -- nossa referência idempotente
  last_charge_at     timestamptz,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.subscriptions is
  'Assinaturas do produto Meada (recorrência mensal via Mercado Pago Preapproval). Tabela de PLATAFORMA: só service_role. mp_preapproval_id é o id da assinatura no MP; status espelha o ciclo (pending/authorized/paused/cancelled) via webhook. profile_id é HARDCODED em ProfileType (validação app-level).';

create index subscriptions_email_idx on public.subscriptions (payer_email);
create index subscriptions_status_idx on public.subscriptions (status);

alter table public.subscriptions enable row level security;
alter table public.subscriptions force  row level security;

grant all on public.subscriptions to service_role;
