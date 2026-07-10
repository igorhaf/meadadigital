-- =============================================================================
-- 99_nutri_onda1.sql
-- Meada — Onda Nutri 1 (backlog docs/FEATURES_SUGERIDAS_NUTRI.md #1/#2/#5).
--
--   #1 LEMBRETE + CONFIRMAÇÃO DE CONSULTA (NutriReminderJob + <confirmacao_nutri>): consulta
--      faltada é o maior ralo do consultório. Véspera → "sua consulta com {profissional} é
--      amanhã às {hora} — confirma?"; a resposta cai na IA, que emite a tag (confirmado|
--      cancelado) com BARREIRA DE CONTATO (clone pet/otica). reminded_start_at = idempotência
--      por consulta+horário (remarcar rearma). Toggle reminder_enabled (default ON). É
--      logística — a trava clínica segue intacta.
--
--   #2 RÉGUA DE REENGAJAMENTO (opt-in, default OFF — lição Baileys: ligar dispara pra base
--      inteira de uma vez): paciente ATIVO sem consulta futura e com a última REALIZADA há
--      reengagement_days (default 30) recebe UM convite de retomada. 1 toque por ciclo:
--      reengagement_sent_at no paciente, re-armado por consulta realizada posterior.
--
--   #5 AUTO-TRANSIÇÃO (default ON): confirmado com end_at passado (folga 2h) vira 'realizado'
--      (transição válida; silencioso). Destrava régua/relatórios sem toque manual. 'agendado'
--      passado NÃO vira falta (julgamento humano).
-- =============================================================================

alter table public.nutri_config
  add column if not exists reminder_enabled boolean not null default true;
alter table public.nutri_config
  add column if not exists auto_complete_enabled boolean not null default true;
alter table public.nutri_config
  add column if not exists reengagement_enabled boolean not null default false;
alter table public.nutri_config
  add column if not exists reengagement_days integer not null default 30
    check (reengagement_days between 7 and 365);

comment on column public.nutri_config.reminder_enabled is
  'Se true (default), o NutriReminderJob lembra na véspera as consultas agendado/confirmado.';
comment on column public.nutri_config.auto_complete_enabled is
  'Se true (default), confirmado com end_at passado (2h de folga) vira realizado (silencioso).';
comment on column public.nutri_config.reengagement_enabled is
  'OPT-IN da régua de retomada (default OFF — ligar pode disparar pra base inteira; decisão consciente).';
comment on column public.nutri_config.reengagement_days is
  'Dias desde a última consulta REALIZADA até o convite de retomada (default 30).';

alter table public.nutri_appointments
  add column if not exists reminded_start_at timestamptz;

comment on column public.nutri_appointments.reminded_start_at is
  'start_at para o qual o lembrete de véspera JÁ foi enviado (remarcar rearma).';

alter table public.nutri_patients
  add column if not exists reengagement_sent_at timestamptz;

comment on column public.nutri_patients.reengagement_sent_at is
  'Quando o convite de retomada foi enviado (1 toque por ciclo — nova consulta realizada depois rearma).';

create index if not exists idx_nutri_appts_reminder
  on public.nutri_appointments (start_at)
  where status in ('agendado','confirmado');
