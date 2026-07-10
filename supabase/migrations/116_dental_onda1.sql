-- =============================================================================
-- 116_dental_onda1.sql
-- Meada — Onda Dental 1 (backlog docs/FEATURES_SUGERIDAS_DENTAL.md #1/#3/#5).
--
--   #1 LEMBRETE D-1 + CONFIRMAÇÃO: no-show de 15-30% é a maior sangria do
--      consultório. O job lembra na véspera pedindo SIM; a resposta fecha o loop
--      via <confirmacao_consulta> (SÓ confirmada — o CANCELAMENTO pela IA SEGUE
--      BLOQUEADO, trava original do dental: desmarcar é com o consultório).
--      Remarcar REARMA (marker = start_at lembrado). Toggle default ON.
--   #5 AUTO-REALIZADA: confirmada vencida → realizada (silenciosa, toggle ON).
--      A variante "agendada → falta" fica de fora (falta só de confirmada na
--      máquina atual; marcar falta segue humano).
--   #3 RECALL DE LIMPEZA/MANUTENÇÃO (opt-in OFF — lição Baileys): paciente sem
--      consulta REALIZADA há recall_months (default 6) e sem consulta futura →
--      1 convite por episódio (marker re-armado por consulta realizada nova).
-- =============================================================================

alter table public.dental_clinic_config
  add column reminder_enabled      boolean not null default true,
  add column auto_complete_enabled boolean not null default true,
  add column recall_enabled        boolean not null default false,
  add column recall_months         integer not null default 6 check (recall_months between 1 and 36);

comment on column public.dental_clinic_config.reminder_enabled is
  'Se true (default), o DentalReminderJob lembra o paciente na véspera pedindo confirmação (SIM). Desmarcar segue com o consultório (trava).';
comment on column public.dental_clinic_config.recall_enabled is
  'Opt-in do recall de manutenção/limpeza (backlog #3). DESLIGADO por default (lição Baileys).';

alter table public.dental_appointments
  add column reminded_start_at timestamptz;

comment on column public.dental_appointments.reminded_start_at is
  'start_at da consulta quando o lembrete D-1 foi enviado — remarcar REARMA (marker <> start_at).';

alter table public.dental_patients
  add column recall_reminded_at timestamptz;

comment on column public.dental_patients.recall_reminded_at is
  'Quando o recall tocou este paciente — re-armado por consulta REALIZADA mais nova que o marker.';
