-- =============================================================================
-- 90_salon_onda1.sql
-- Meada — Onda Salon 1 (backlog docs/FEATURES_SUGERIDAS_SALON.md #1/#7).
--
--   #1 LEMBRETE DE VÉSPERA (SalonReminderJob): a maior sangria do salão é o no-show. Cron
--      diário varre os salon_appointments 'agendado'/'confirmado' com start_at AMANHÃ
--      (America/Sao_Paulo) e dispara mensagem FIXA pela conversa do agendamento ("seu horário
--      com {profissional} é amanhã às {hora} — confirma?"). A resposta cai no fluxo inbound
--      normal (a IA confirma/remarca pela tag <agendamento> como sempre). Idempotência por
--      (agendamento, start_at): reminded_start_at — REMARCAR rearma o lembrete. Toggle
--      reminder_enabled (default LIGADO). Sem canal (POST manual) → marca sem envio.
--
--   #7 AUTO-TRANSIÇÃO opt-in: confirmado com end_at no PASSADO → 'realizado' (transição
--      VÁLIDA da máquina; realizado é silencioso — não notifica). Só isso: 'agendado' passado
--      NÃO vira falta automaticamente (falta só existe a partir de confirmado na máquina, e
--      marcar falta é julgamento humano). Default DESLIGADO (mexer em status sozinho é
--      decisão consciente do tenant).
-- =============================================================================

alter table public.salon_config
  add column if not exists reminder_enabled boolean not null default true;
alter table public.salon_config
  add column if not exists auto_complete_enabled boolean not null default false;

comment on column public.salon_config.reminder_enabled is
  'Se true (default), o SalonReminderJob envia lembrete WhatsApp na VÉSPERA de cada agendamento agendado/confirmado. Ausência de linha de config = ligado.';
comment on column public.salon_config.auto_complete_enabled is
  'OPT-IN: confirmado com end_at no passado vira realizado automaticamente (silencioso). Default desligado.';

alter table public.salon_appointments
  add column if not exists reminded_start_at timestamptz;

comment on column public.salon_appointments.reminded_start_at is
  'start_at para o qual o lembrete de véspera JÁ foi enviado (idempotência por agendamento+horário — remarcar rearma). Espelho reminded_due_date do atelie.';

create index if not exists idx_salon_appts_reminder
  on public.salon_appointments (start_at)
  where status in ('agendado','confirmado');
