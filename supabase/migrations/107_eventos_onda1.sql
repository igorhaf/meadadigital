-- =============================================================================
-- 107_eventos_onda1.sql
-- Meada — Onda Eventos 1 (backlog docs/FEATURES_SUGERIDAS_EVENTOS.md #2/#3/#6/#7/#8/#9).
--
--   #2 CATÁLOGO DE PACOTES/ADICIONAIS (event_packages): Prata/Ouro/Diamante + hora
--      extra/open bar — a equipe monta orçamento em segundos (autofill do editor de
--      itens; o item da proposta continua snapshot texto+preço) e a IA passa a
--      DESCREVER pacotes e sugerir adicionais CADASTRADOS (#9 — trava intacta: nunca
--      inventa item/valor, quem fecha preço é a equipe).
--   #3 AVISO DE DATA OCUPADA: consulta leve por event_date (proposta aprovada/
--      fechada/realizada na mesma data) → aviso NÃO bloqueante no painel (a casa
--      pode ter 2 salões); o contexto da IA lista as datas ocupadas — ela avisa
--      "essa data já está reservada" mas NUNCA afirma que uma data está livre.
--   #6 AUTO-REALIZADA: proposta fechada com event_date passada vira realizada no
--      dia seguinte (job diário, silencioso). Toggle auto_complete_enabled.
--   #7 PÓS-VENDA: ao entrar em realizada, mensagem de agradecimento + link de
--      avaliação (review_link) + convite de indicação. Toggle post_event_enabled.
--   #8 FOLLOW-UP DE ORÇAMENTO PARADO: proposta orcada há follow_up_days sem
--      resposta → 1 toque por episódio (marker vs status_updated_at). Funil ativo
--      (não é disparo em massa à base) → default ON.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) event_packages — catálogo de pacotes e adicionais.
-- ---------------------------------------------------------------------------
create table public.event_packages (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete restrict,
  name         text        not null check (length(trim(name)) between 1 and 200),
  kind         text        not null default 'pacote' check (kind in ('pacote','adicional')),
  description  text,
  price_cents  integer     not null check (price_cents >= 0),
  suggestible  boolean     not null default false,
  active       boolean     not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

comment on table public.event_packages is
  'Catálogo de pacotes/adicionais do tenant eventos (onda 1, backlog #2). Autofill do editor de orçamento (o item da proposta continua snapshot) + fonte do que a IA pode DESCREVER/sugerir (#9 — suggestible=true entra no upsell consultivo; a IA nunca inventa item/valor). active=false sai do autofill/IA.';

create index idx_event_packages_company_active on public.event_packages (company_id, active) where active = true;

alter table public.event_packages enable row level security;
grant all on public.event_packages to service_role;

-- ---------------------------------------------------------------------------
-- 2) event_config: toggles das automações + review link.
-- ---------------------------------------------------------------------------
alter table public.event_config
  add column auto_complete_enabled boolean not null default true,
  add column post_event_enabled    boolean not null default true,
  add column review_link           text,
  add column follow_up_enabled     boolean not null default true,
  add column follow_up_days        integer not null default 3 check (follow_up_days between 1 and 60);

comment on column public.event_config.auto_complete_enabled is
  'Se true (default), proposta FECHADA com event_date passada vira REALIZADA automaticamente (job diário) — dispara o pós-venda.';
comment on column public.event_config.post_event_enabled is
  'Se true (default), ao entrar em REALIZADA o cliente recebe agradecimento + review_link (se houver) + convite de indicação.';
comment on column public.event_config.follow_up_days is
  'Dias com a proposta parada em ORCADA até o follow-up gentil (1 toque por episódio).';

-- ---------------------------------------------------------------------------
-- 3) event_proposals: marker do follow-up.
-- ---------------------------------------------------------------------------
alter table public.event_proposals
  add column follow_up_sent_at timestamptz;

comment on column public.event_proposals.follow_up_sent_at is
  'Quando o follow-up de orçamento parado foi enviado (onda 1, backlog #8). Rearmado por episódio (marker < status_updated_at).';

create index idx_event_proposals_orcada_stale
  on public.event_proposals (status_updated_at)
  where status = 'orcada';
create index idx_event_proposals_event_date
  on public.event_proposals (company_id, event_date)
  where status in ('aprovada','fechada','realizada');
