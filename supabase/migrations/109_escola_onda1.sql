-- =============================================================================
-- 109_escola_onda1.sql
-- Meada — Onda Escola 1 (backlog docs/FEATURES_SUGERIDAS_ESCOLA.md #1/#2/#4/#10).
--
--   #1 LISTA DE ESPERA POR TURMA (escola_waitlist): turma cheia hoje evapora o lead.
--      Quando a tag <matricula_escola> bate em class_full, o handler ENFILEIRA (em vez
--      de descartar) — posição DERIVADA por count (espelho da fila do barbearia, sem
--      coluna position). A secretaria vê a fila no painel e, ao abrir vaga (matrícula
--      cancelada), um botão dispara o aviso ao 1º da fila (ação HUMANA — trava "nunca
--      promete vaga" intacta).
--   #2 LEMBRETE DE VISITA (D-1 e no dia): visita é o topo do funil de matrícula e o
--      no-show é alto. Lembrete na véspera + na manhã do dia; a resposta cai na IA
--      (remarcar = cancelar + agendar de novo). Markers re-armáveis por visit_date.
--   #10 AUTO-TRANSIÇÃO: visita agendada com data passada vira realizada (job diário,
--      silencioso; quem faltou a secretaria marca cancelada). Toggle default ON.
--   #4 RÉGUA DE MENSALIDADE EM ABERTO (versão sem gateway): matrícula ativa sem
--      pagamento do mês corrente após o dia de vencimento → 1 lembrete gentil por mês
--      (valor JÁ cadastrado da turma, sem multa/juros — a IA nunca inventa). Toggle
--      default OFF (cobrança em massa é decisão consciente).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) escola_waitlist — lista de espera por turma.
-- ---------------------------------------------------------------------------
create table public.escola_waitlist (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete restrict,
  class_id     uuid        not null references public.escola_classes(id) on delete cascade,
  contact_id   uuid        not null references public.contacts(id) on delete cascade,
  student_id   uuid        references public.escola_students(id) on delete set null,
  student_name text        not null,   -- snapshot (existente OU new_student que nem foi criado)
  status       text        not null default 'aguardando' check (status in ('aguardando','avisada','convertida','desistiu')),
  created_at   timestamptz not null default now(),
  notified_at  timestamptz,
  status_updated_at timestamptz not null default now()
);

comment on table public.escola_waitlist is
  'Lista de espera por turma (onda Escola 1, backlog #1). Enfileirada pelo handler quando a matrícula bate em class_full. Posição DERIVADA por count de aguardando mais antigos (sem coluna position). Avisar o 1º da fila é AÇÃO HUMANA no painel (botão) — a IA nunca promete vaga. convertida/desistiu são gestão da secretaria.';

create index idx_escola_waitlist_class_pending
  on public.escola_waitlist (company_id, class_id, created_at)
  where status = 'aguardando';
create unique index uniq_escola_waitlist_pending
  on public.escola_waitlist (class_id, contact_id, student_name)
  where status = 'aguardando';

alter table public.escola_waitlist enable row level security;
grant all on public.escola_waitlist to service_role;

-- ---------------------------------------------------------------------------
-- 2) escola_visits: markers dos lembretes D-1 e D0.
-- ---------------------------------------------------------------------------
alter table public.escola_visits
  add column reminded1_visit_date date,
  add column reminded0_visit_date date;

comment on column public.escola_visits.reminded1_visit_date is
  'visit_date quando o lembrete D-1 foi enviado — remarcar REARMA (marker <> visit_date).';
comment on column public.escola_visits.reminded0_visit_date is
  'visit_date quando o lembrete do DIA foi enviado — remarcar REARMA.';

-- ---------------------------------------------------------------------------
-- 3) escola_config: toggles das automações + vencimento da mensalidade.
-- ---------------------------------------------------------------------------
alter table public.escola_config
  add column visit_reminder_enabled  boolean not null default true,
  add column visit_auto_complete_enabled boolean not null default true,
  add column payment_reminder_enabled boolean not null default false,
  add column payment_due_day         integer not null default 10 check (payment_due_day between 1 and 28);

comment on column public.escola_config.visit_reminder_enabled is
  'Se true (default), o EscolaReminderJob lembra a família na véspera e na manhã do dia da visita.';
comment on column public.escola_config.payment_reminder_enabled is
  'Opt-in da régua de mensalidade em aberto (backlog #4). DESLIGADO por default: cobrança em massa é decisão consciente (lição Baileys).';
comment on column public.escola_config.payment_due_day is
  'Dia do mês a partir do qual a mensalidade sem registro conta como em aberto (default 10).';

-- ---------------------------------------------------------------------------
-- 4) escola_enrollments: marker da régua (1 lembrete por mês de referência).
-- ---------------------------------------------------------------------------
alter table public.escola_enrollments
  add column payment_reminded_month date;

comment on column public.escola_enrollments.payment_reminded_month is
  'Mês de referência (dia 01) do último lembrete de mensalidade em aberto — 1 toque por mês.';
