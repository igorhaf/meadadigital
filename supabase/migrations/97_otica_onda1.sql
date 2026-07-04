-- =============================================================================
-- 97_otica_onda1.sql
-- Meada — Onda Ótica 1 (backlog docs/FEATURES_SUGERIDAS_OTICA.md #1/#2).
--
--   #1 LEMBRETE + CONFIRMAÇÃO DE EXAME (OticaReminderJob + <confirmacao_exame>): a cadeira do
--      optometrista é o funil de entrada da venda de óculos — falta silenciosa perde o slot E
--      a venda. Cron diário lembra na véspera os exames agendado/confirmado; a resposta cai na
--      IA, que emite <confirmacao_exame>{exam_id, decisao} (confirmado|cancelado) com BARREIRA
--      DE CONTATO (clone restaurant/pousada/pet). Idempotência por (exame, start_at):
--      reminded_start_at — remarcar rearma. Toggle exam_reminder_enabled (default ON).
--      Trava intacta: só horário — nada de grau/conduta.
--
--   #2 FOLLOW-UP DE ÓCULOS PRONTO: pedido em 'pronto' há N dias (config, default 3) sem virar
--      'retirado' recebe UMA cutucada gentil por episódio (re-armado por status_updated_at).
--      Capital parado vira retirada. Toggle pickup_followup_enabled (default ON).
-- =============================================================================

alter table public.otica_config
  add column if not exists exam_reminder_enabled boolean not null default true;
alter table public.otica_config
  add column if not exists pickup_followup_enabled boolean not null default true;
alter table public.otica_config
  add column if not exists pickup_followup_days integer not null default 3
    check (pickup_followup_days between 1 and 30);

comment on column public.otica_config.exam_reminder_enabled is
  'Se true (default), o OticaReminderJob lembra na véspera os exames agendado/confirmado.';
comment on column public.otica_config.pickup_followup_enabled is
  'Se true (default), o job cutuca o pedido parado em pronto há pickup_followup_days.';
comment on column public.otica_config.pickup_followup_days is
  'Dias em pronto sem retirada até o follow-up (default 3).';

alter table public.otica_exam_appointments
  add column if not exists reminded_start_at timestamptz;

comment on column public.otica_exam_appointments.reminded_start_at is
  'start_at para o qual o lembrete de véspera JÁ foi enviado (remarcar rearma).';

alter table public.otica_orders
  add column if not exists pickup_followup_sent_at timestamptz;

comment on column public.otica_orders.pickup_followup_sent_at is
  'Quando o follow-up de retirada foi enviado. Re-armado por status_updated_at (voltar a pronto permite novo follow-up).';

create index if not exists idx_otica_exams_reminder
  on public.otica_exam_appointments (start_at)
  where status in ('agendado','confirmado');
create index if not exists idx_otica_orders_pronto
  on public.otica_orders (status_updated_at)
  where status = 'pronto';
