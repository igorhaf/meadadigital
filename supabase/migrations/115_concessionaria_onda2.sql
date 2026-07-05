-- =============================================================================
-- 115_concessionaria_onda2.sql
-- Meada — Onda Concessionária 2 (backlog docs/FEATURES_SUGERIDAS_CONCESSIONARIA.md #5/#7/#12).
--
--   #5 TRADE-IN (proposta de usado na troca): fatia enorme só compra dando o carro
--      atual na troca. A IA COLETA os dados do usado via <troca_carro> (marca/modelo/
--      ano/km/estado/valor pretendido — SEM avaliar nem prometer valor: a avaliação é
--      HUMANA no painel). Vira aba na tela de Leads.
--   #7 PÓS-VENDA: quando o lead vira FECHADO (carro vendido), agradecimento +
--      review_link + convite de indicação. Toggle post_sale_enabled (default ON).
--   #12 REVISÃO PROGRAMADA / ANIVERSÁRIO DE COMPRA: N meses após o fechamento do
--      lead, convite de revisão/checape (1 toque por lead, marker) — traz o cliente
--      de volta pro serviço e futura recompra. Opt-in OFF (disparo à base).
-- =============================================================================

create table public.concessionaria_tradein_offers (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  contact_id         uuid        references public.contacts(id) on delete set null,
  conversation_id    uuid        references public.conversations(id) on delete set null,
  customer_name      text        not null,
  interest_vehicle_id uuid       references public.concessionaria_vehicles(id) on delete set null,
  used_brand         text        not null,
  used_model         text        not null,
  used_year          integer,
  used_km            integer,
  used_condition     text,        -- estado geral descrito pelo cliente (texto livre)
  asking_cents       integer,     -- valor pretendido DECLARADO pelo cliente (não é avaliação)
  status             text        not null default 'aberta' check (status in ('aberta','avaliada','aceita','recusada')),
  offer_cents        integer,     -- proposta de abatimento da LOJA (avaliação humana)
  notes              text,
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.concessionaria_tradein_offers is
  'Trade-in (onda 2, backlog #5): a IA COLETA os dados do usado (tag <troca_carro>) sem avaliar nem prometer valor — asking_cents é o valor DECLARADO pelo cliente; offer_cents é a avaliação HUMANA da loja no painel.';

create index idx_conc_tradein_company_status on public.concessionaria_tradein_offers (company_id, status, created_at desc);
alter table public.concessionaria_tradein_offers enable row level security;
grant all on public.concessionaria_tradein_offers to service_role;

alter table public.concessionaria_config
  add column post_sale_enabled     boolean not null default true,
  add column review_link           text,
  add column service_reminder_enabled boolean not null default false,
  add column service_reminder_months  integer not null default 12 check (service_reminder_months between 1 and 36);

comment on column public.concessionaria_config.post_sale_enabled is
  'Se true (default), lead FECHADO dispara agradecimento + review_link + convite de indicação (onda 2, backlog #7).';
comment on column public.concessionaria_config.service_reminder_enabled is
  'Opt-in da revisão programada (onda 2, backlog #12). DESLIGADO por default (disparo à base).';

alter table public.concessionaria_leads
  add column service_reminded_at timestamptz;

comment on column public.concessionaria_leads.service_reminded_at is
  'Quando o convite de revisão pós-compra foi enviado (1 toque por lead fechado).';
