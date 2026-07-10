-- =============================================================================
-- 110_dermatologia_onda1.sql
-- Meada — Onda Dermatologia 1 (backlog docs/FEATURES_SUGERIDAS_DERMATOLOGIA.md #1/#2/#5).
--
--   #1 LEMBRETE D-1 + CONFIRMAÇÃO + PREPARO: dermatologia tem no-show alto (eletivo,
--      agenda longa). O job lembra na véspera pedindo SIM e, quando o tipo de
--      atendimento tem prep_instructions, envia a nota de preparo JUNTO (verbatim, a
--      mesma garantia do EntregaPreparoHandler — preparo mal feito queima dois slots).
--      A resposta fecha o loop via <confirmacao_derma> (confirmada|cancelada, barreira
--      de contato). Remarcar REARMA. Trava clínica intacta (texto administrativo).
--   #5 AUTO-REALIZADA: confirmada vencida → realizada (silenciosa, toggle). A variante
--      "→ falta" do backlog ficou de fora por segurança (consulta atendida sem baixa
--      viraria falta indevida) — falta continua ação humana.
--   #2 RECALL DE RETORNO (opt-in OFF — disparo à base, lição Baileys): paciente sem
--      consulta REALIZADA há recall_months meses e sem agendamento futuro → 1 convite
--      por episódio (marker re-armado quando nova consulta realizada). Convite
--      administrativo, sem conduta clínica.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) dermatologia_config: toggles + janela do recall.
-- ---------------------------------------------------------------------------
alter table public.dermatologia_config
  add column reminder_enabled      boolean not null default true,
  add column auto_complete_enabled boolean not null default true,
  add column recall_enabled        boolean not null default false,
  add column recall_months         integer not null default 6 check (recall_months between 1 and 36);

comment on column public.dermatologia_config.reminder_enabled is
  'Se true (default), o DermatologiaReminderJob lembra o paciente na véspera (com a nota de preparo quando o tipo tem).';
comment on column public.dermatologia_config.recall_enabled is
  'Opt-in do recall de retorno (backlog #2). DESLIGADO por default: ligar dispara pra base (lição Baileys).';

-- ---------------------------------------------------------------------------
-- 2) dermatologia_appointments: marker do lembrete.
-- ---------------------------------------------------------------------------
alter table public.dermatologia_appointments
  add column reminded_start_at timestamptz;

comment on column public.dermatologia_appointments.reminded_start_at is
  'start_at da consulta quando o lembrete D-1 foi enviado — remarcar REARMA (marker <> start_at).';

-- ---------------------------------------------------------------------------
-- 3) dermatologia_patients: marker do recall (1 convite por episódio).
-- ---------------------------------------------------------------------------
alter table public.dermatologia_patients
  add column recall_reminded_at timestamptz;

comment on column public.dermatologia_patients.recall_reminded_at is
  'Quando o recall de retorno tocou este paciente — re-armado quando uma consulta REALIZADA mais nova que o marker existe (episódio).';
