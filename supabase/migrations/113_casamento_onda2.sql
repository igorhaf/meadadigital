-- =============================================================================
-- 113_casamento_onda2.sql
-- Meada — Onda Casamento 2 (backlog docs/FEATURES_SUGERIDAS_CASAMENTO.md #6/#8).
--
--   #6 PÓS-CASAMENTO (NPS + depoimento): ao entrar em REALIZADA (o grande dia
--      aconteceu), agradecimento emocionado + review_link + convite de indicação
--      — o auge da emoção é o melhor momento de coletar prova social. Toggle
--      post_event_enabled (default ON).
--   #8 REATIVAÇÃO DE LEAD FRIO: proposta ORCADA parada há follow_up_days sem
--      resposta do casal → 1 toque gentil por episódio (follow_up_sent_at vs
--      status_updated_at; re-orçar REARMA). Funil ativo → default ON.
-- =============================================================================

alter table public.wedding_config
  add column post_event_enabled boolean not null default true,
  add column review_link        text,
  add column follow_up_enabled  boolean not null default true,
  add column follow_up_days     integer not null default 5 check (follow_up_days between 1 and 60);

comment on column public.wedding_config.post_event_enabled is
  'Se true (default), REALIZADA encadeia agradecimento + review_link + convite de indicação (onda 2, backlog #6).';
comment on column public.wedding_config.follow_up_days is
  'Dias com a proposta parada em ORCADA até o follow-up gentil ao casal (onda 2, backlog #8).';

alter table public.wedding_proposals
  add column follow_up_sent_at timestamptz;

comment on column public.wedding_proposals.follow_up_sent_at is
  'Quando o follow-up de orçamento parado foi enviado — rearmado por episódio (marker < status_updated_at).';

create index idx_wedding_proposals_orcada_stale
  on public.wedding_proposals (status_updated_at)
  where status = 'orcada';
