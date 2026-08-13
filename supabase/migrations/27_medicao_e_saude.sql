-- =============================================================================
-- 27_medicao_e_saude.sql
-- Meada — Camada 6 (sub-maratona 2: Medição + Saúde). Migration única
-- consolidada das fases 6.2.5 (token tracking), 6.3 (métricas globais reais — só
-- consome o que a 6.2.5 grava, sem schema próprio) e 6.4 (saúde/jobs/erros).
--
-- COLUNAS NOVAS (6.2.5):
--   messages: tokens_in/tokens_out (int) + model (text). Todas NULLABLE — mensagens
--     antigas não têm dado retroativo (sem fake) e mensagens sintéticas (boas-vindas,
--     fora-de-horário) não passam pela IA → ficam NULL (distingue 'sem IA' de 'IA com
--     custo zero'). Gravadas pelo OutboundService a partir do AiResponse + gemini.model.
--
-- TABELAS NOVAS (6.4):
--   webhook_heartbeats — 1 row por evento recebido no webhook da Evolution (batimento
--     de que o canal inbound está vivo). Webhook está OFF no MVP (dry-run) → tabela
--     vazia até religar; a tela de saúde mostra "sem heartbeat".
--   scheduled_job_runs — registro de cada execução dos @Scheduled (ReminderJob,
--     ReactivationJob): running ao iniciar, success/failed ao terminar.
--   error_log — erros capturados (instrumentação cirúrgica: envio Evolution fatal e
--     falha fatal da IA). Não é catch-all; só os pontos cravados ganham .log().
--
-- RLS: as 3 tabelas são acessadas SÓ pelo backend (service_role) — escritas por
--   repos/loggers internos, lidas pelos controllers de saúde (super-admin via Spring,
--   fora do RLS). NÃO há policy 'authenticated' (nenhum tenant lê/escreve). enable +
--   force como defesa em profundidade (sem policy = só service_role/BYPASSRLS).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- messages — tokens + modelo (6.2.5)
-- ---------------------------------------------------------------------------
alter table public.messages
  add column tokens_in  integer,
  add column tokens_out integer,
  add column model      text;

comment on column public.messages.tokens_in is
  'Tokens do prompt (usageMetadata da IA). NULL quando a mensagem não passou pela IA (inbound, boas-vindas, fora-de-horário) — distingue ''sem IA'' de ''IA com custo zero''.';
comment on column public.messages.tokens_out is
  'Tokens da resposta da IA. NULL na mesma condição de tokens_in.';
comment on column public.messages.model is
  'Nome do modelo (gemini.model) que gerou a resposta. NULL quando não houve IA. Verdade temporal: troca de modelo futura preserva o histórico.';

-- ---------------------------------------------------------------------------
-- webhook_heartbeats — batimento do canal inbound (6.4)
-- ---------------------------------------------------------------------------
create table public.webhook_heartbeats (
  id          uuid        primary key default gen_random_uuid(),
  instance_id uuid,
  event_type  text        not null,
  received_at timestamptz not null default now()
);

comment on table public.webhook_heartbeats is
  'Batimento do webhook da Evolution (camada 6.4). 1 row por evento recebido. Webhook OFF no MVP (dry-run) → vazio até religar. instance_id nullable (nem todo payload resolve instância).';

create index idx_webhook_heartbeats_received on public.webhook_heartbeats (received_at desc);

alter table public.webhook_heartbeats enable row level security;
alter table public.webhook_heartbeats force  row level security;
grant all on public.webhook_heartbeats to service_role;

-- ---------------------------------------------------------------------------
-- scheduled_job_runs — registro de execução dos jobs @Scheduled (6.4)
-- ---------------------------------------------------------------------------
create table public.scheduled_job_runs (
  id            uuid        primary key default gen_random_uuid(),
  job_name      text        not null,
  started_at    timestamptz not null default now(),
  finished_at   timestamptz,
  status        text        not null default 'running'
                  check (status in ('running','success','failed')),
  error_message text
);

comment on table public.scheduled_job_runs is
  'Registro de cada execução dos jobs agendados (camada 6.4): running ao iniciar, success/failed ao terminar (try/finally). finished_at e error_message preenchidos no fim.';

create index idx_scheduled_job_runs_name on public.scheduled_job_runs (job_name, started_at desc);

alter table public.scheduled_job_runs enable row level security;
alter table public.scheduled_job_runs force  row level security;
grant all on public.scheduled_job_runs to service_role;

-- ---------------------------------------------------------------------------
-- error_log — erros capturados (instrumentação cirúrgica) (6.4)
-- ---------------------------------------------------------------------------
create table public.error_log (
  id          uuid        primary key default gen_random_uuid(),
  source      text        not null,
  message     text        not null,
  stack_trace text,
  context     jsonb,
  created_at  timestamptz not null default now()
);

comment on table public.error_log is
  'Erros capturados em pontos cravados (camada 6.4): envio Evolution fatal, falha fatal da IA. NÃO é catch-all — só os catches instrumentados gravam. Escrito via ErrorLogger (service_role).';

create index idx_error_log_created on public.error_log (created_at desc);
create index idx_error_log_source on public.error_log (source, created_at desc);

alter table public.error_log enable row level security;
alter table public.error_log force  row level security;
grant all on public.error_log to service_role;
