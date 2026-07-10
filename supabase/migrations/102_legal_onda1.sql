-- =============================================================================
-- 102_legal_onda1.sql
-- Meada — Onda Legal 1 (backlog docs/FEATURES_SUGERIDAS_LEGAL.md #1/#3).
--
--   #1 AGENDA DE PRAZOS E AUDIÊNCIAS (legal_deadlines + LegalDeadlineReminderJob): prazo
--      perdido é o pior evento do escritório (dano ao cliente + responsabilidade civil).
--      Prazos/audiências vinculados ao processo (kind prazo|audiencia, due_date + due_time
--      opcional, status pendente|cumprido|perdido) com lembrete AUTOMÁTICO em D-3 e D-1 ao
--      cliente vinculado (via LegalCaseNotifier — texto fixo com data/local, SEM mérito;
--      trava jurídica intacta). Idempotência por (prazo, due_date) em cada janela — remarcar
--      REARMA. A IA lê os prazos pendentes do contexto e informa a DATA quando perguntada.
--
--   #3 PÓS-ENCERRAMENTO (legal_config): ao ENCERRAR o processo, além da notificação de status,
--      dispara agradecimento + pedido de avaliação (review_link da config) + convite de
--      indicação. Toggle post_closure_enabled (default ON; sem review_link a mensagem sai sem
--      o link).
-- =============================================================================

create table public.legal_config (
  company_id           uuid        primary key references public.companies(id) on delete restrict,
  review_link          text,
  post_closure_enabled boolean     not null default true,
  deadline_reminder_enabled boolean not null default true,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

comment on table public.legal_config is
  'Config 1:1 do tenant legal (onda 1): link de avaliação (Google) + toggles do pós-encerramento e do lembrete de prazos.';

alter table public.legal_config enable row level security;
grant all on public.legal_config to service_role;

create table public.legal_deadlines (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  case_id           uuid        not null references public.legal_cases(id) on delete cascade,
  kind              text        not null check (kind in ('prazo','audiencia')),
  title             text        not null check (length(trim(title)) between 1 and 200),
  due_date          date        not null,
  due_time          time,
  location          text,
  status            text        not null default 'pendente' check (status in ('pendente','cumprido','perdido')),
  notes             text,
  reminded3_due_date date,
  reminded1_due_date date,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.legal_deadlines is
  'Prazos/audiências do processo (onda Legal 1, backlog #1). Lembrete D-3/D-1 ao cliente vinculado (reminded3/1_due_date = idempotência por prazo+data; remarcar rearma). status é gestão do advogado.';

create index idx_legal_deadlines_case on public.legal_deadlines (case_id, due_date);
create index idx_legal_deadlines_due on public.legal_deadlines (due_date) where status = 'pendente';

alter table public.legal_deadlines enable row level security;
grant all on public.legal_deadlines to service_role;
