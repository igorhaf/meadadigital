-- =============================================================================
-- 105_fotografia_onda1.sql
-- Meada — Onda Fotografia 1 (backlog docs/FEATURES_SUGERIDAS_FOTOGRAFIA.md #2/#3/#4/#5).
--
--   #2 LEMBRETE D-2/D-1 + CONFIRMAÇÃO: sessão bloqueia fotógrafo por horas; falta é
--      prejuízo direto. O job lembra a cliente em D-2 e D-1 (pacote+fotógrafo+hora,
--      pedindo confirmação); a resposta cai na conversa e a IA emite
--      <confirmacao_foto> (agendada→confirmada ou →cancelada). Remarcar REARMA
--      (markers por janela = start_at lembrado). Auto-complete: confirmada vencida →
--      realizada (silencioso).
--   #3 ENTREGA NO PRAZO AUTOMATIZADA: no delivery_due_date, sessão realizada COM
--      delivery_link gravado é entregue automaticamente (link VERBATIM — nunca passa
--      pela IA) + transição → entregue + convite pós-entrega (fotos extras/álbum, SEM
--      preço — upsell no momento quente). Link ausente → o painel destaca o atraso.
--   #4 POLÍTICA DE CANCELAMENTO comunicada: config cancellation_policy_hours; a IA
--      COMUNICA a política ao agendar (retenção de sinal fica bloqueada pelo gateway #50).
--   #5 UPSELL PROATIVO: pacotes ganham flag suggestible; o contexto instrui a oferta
--      consultiva de upgrade — sempre nome+preço DO CATÁLOGO, nunca pressão/desconto.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) fotografia_config: toggles das automações + política de cancelamento.
-- ---------------------------------------------------------------------------
alter table public.fotografia_config
  add column reminder_enabled           boolean not null default true,
  add column auto_complete_enabled      boolean not null default true,
  add column auto_deliver_enabled       boolean not null default true,
  add column post_delivery_upsell_enabled boolean not null default true,
  add column cancellation_policy_hours  integer check (cancellation_policy_hours is null or cancellation_policy_hours between 1 and 720);

comment on column public.fotografia_config.reminder_enabled is
  'Se true (default), o FotografiaReminderJob lembra a cliente em D-2 e D-1 da sessão pedindo confirmação.';
comment on column public.fotografia_config.auto_deliver_enabled is
  'Se true (default), no delivery_due_date a sessão realizada COM delivery_link é entregue automaticamente (link verbatim) e vira entregue.';
comment on column public.fotografia_config.cancellation_policy_hours is
  'Janela de cancelamento sem custo, em horas antes da sessão (null = sem política comunicada). A IA COMUNICA ao agendar; retenção real de sinal depende do gateway (#50).';

-- ---------------------------------------------------------------------------
-- 2) fotografia_session_appointments: markers dos lembretes D-2/D-1.
-- ---------------------------------------------------------------------------
alter table public.fotografia_session_appointments
  add column reminded2_start_at timestamptz,
  add column reminded1_start_at timestamptz;

comment on column public.fotografia_session_appointments.reminded2_start_at is
  'start_at da sessão quando o lembrete D-2 foi enviado — remarcar REARMA (marker <> start_at).';
comment on column public.fotografia_session_appointments.reminded1_start_at is
  'start_at da sessão quando o lembrete D-1 foi enviado — remarcar REARMA (marker <> start_at).';

create index idx_foto_sess_deliver_due
  on public.fotografia_session_appointments (delivery_due_date)
  where status = 'realizada';

-- ---------------------------------------------------------------------------
-- 3) fotografia_packages: flag de upsell.
-- ---------------------------------------------------------------------------
alter table public.fotografia_packages
  add column suggestible boolean not null default false;

comment on column public.fotografia_packages.suggestible is
  'Se true, a IA pode SUGERIR este pacote como upgrade/add-on na conversa (oferta consultiva, nome+preço do catálogo — onda 1, backlog #5).';
