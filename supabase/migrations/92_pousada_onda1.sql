-- =============================================================================
-- 92_pousada_onda1.sql
-- Meada — Onda Pousada 1 (backlog docs/FEATURES_SUGERIDAS_POUSADA.md #2/#4).
--
--   #2 LEMBRETE DE CHECK-IN D-1 + CONFIRMAÇÃO (PousadaReminderJob + <confirmacao_pousada>):
--      na véspera do check-in, o hóspede recebe "sua estadia começa amanhã, check-in a partir
--      das Xh — confirma?" pela conversa. A resposta cai no fluxo da IA, que emite a tag
--      <confirmacao_pousada>{reservation_id, decisao} — confirmado|cancelado — com BARREIRA DE
--      CONTATO e a máquina de status validando (clone do <confirmacao_reserva>/restaurant).
--      Cancelar antecipado LIBERA o quarto pra revenda. Idempotência por (reserva, data):
--      reminded_checkin_date — REMARCAR as datas rearma. Toggle reminder_enabled (default ON).
--
--   #4 AUTO-TRANSIÇÃO opt-in: confirmado com check_in_date passado (1 dia de folga, sem
--      check-in registrado) → no_show; checked_in com check_out_date passado → checked_out.
--      Ambas silenciosas (baseline: só confirmado/cancelado notificam). Default DESLIGADO —
--      marcar no_show sozinho pune hóspede se a equipe esqueceu de registrar o check-in;
--      ligar é decisão consciente do tenant.
-- =============================================================================

alter table public.pousada_config
  add column if not exists reminder_enabled boolean not null default true;
alter table public.pousada_config
  add column if not exists auto_transition_enabled boolean not null default false;

comment on column public.pousada_config.reminder_enabled is
  'Se true (default), o PousadaReminderJob envia o lembrete de check-in na véspera (reservado/confirmado). Ausência de linha = ligado.';
comment on column public.pousada_config.auto_transition_enabled is
  'OPT-IN: confirmado com check-in passado (1 dia de folga) vira no_show; checked_in com check-out passado vira checked_out. Default desligado.';

alter table public.pousada_reservations
  add column if not exists reminded_checkin_date date;

comment on column public.pousada_reservations.reminded_checkin_date is
  'check_in_date para o qual o lembrete de véspera JÁ foi enviado (idempotência por reserva+data — remarcar rearma).';

create index if not exists idx_pousada_reservations_reminder
  on public.pousada_reservations (check_in_date)
  where status in ('reservado','confirmado');
