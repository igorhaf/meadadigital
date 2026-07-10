-- =============================================================================
-- 94_pet_onda1.sql
-- Meada — Onda Pet 1 (backlog docs/FEATURES_SUGERIDAS_PET.md #1).
--
--   #1 LEMBRETE DE VÉSPERA + CONFIRMAÇÃO (PetReminderJob + <confirmacao_pet>): no-show em
--      banho/tosa/consulta é a maior sangria da agenda do pet shop. Cron diário varre os
--      pet_appointments agendado/confirmado com start_at AMANHÃ (America/Sao_Paulo) e dispara
--      mensagem carinhosa ("amanhã o Thor tem banho às 14h — confirma?") pela conversa do
--      tutor. A resposta cai no fluxo da IA, que emite <confirmacao_pet>{appointment_id,
--      decisao} — confirmado|cancelado — com BARREIRA DE CONTATO (tutor da conversa) e a
--      máquina de status validando (clone do restaurant/pousada, migs 91/92). Cancelar LIBERA
--      o slot do profissional. Idempotência por (agendamento, start_at): reminded_start_at —
--      REMARCAR rearma. Toggle reminder_enabled (default LIGADO). Sem canal → marca sem envio.
-- =============================================================================

alter table public.pet_config
  add column if not exists reminder_enabled boolean not null default true;

comment on column public.pet_config.reminder_enabled is
  'Se true (default), o PetReminderJob envia o lembrete de véspera dos agendamentos agendado/confirmado. Ausência de linha = ligado.';

alter table public.pet_appointments
  add column if not exists reminded_start_at timestamptz;

comment on column public.pet_appointments.reminded_start_at is
  'start_at para o qual o lembrete de véspera JÁ foi enviado (idempotência por agendamento+horário — remarcar rearma).';

create index if not exists idx_pet_appts_reminder
  on public.pet_appointments (start_at)
  where status in ('agendado','confirmado');
