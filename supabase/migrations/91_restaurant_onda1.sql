-- =============================================================================
-- 91_restaurant_onda1.sql
-- Meada — Onda Restaurant 1 (backlog docs/FEATURES_SUGERIDAS_RESTAURANT.md #1/#3).
--
--   #1 LEMBRETE D-1 + CONFIRMAÇÃO SIM/NÃO (RestaurantReminderJob + <confirmacao_reserva>):
--      a reserva nasce 'pendente' e ninguém lembra o cliente — no-show é a maior perda do
--      restaurante. Cron diário varre as reservas pendente/confirmada de AMANHÃ
--      (America/Sao_Paulo) e pergunta "confirma? Responda SIM ou NÃO" pela conversa. A resposta
--      cai no fluxo da IA, que emite a tag <confirmacao_reserva>{reservation_id, decisao} —
--      confirmada|cancelada — com BARREIRA DE CONTATO e a máquina de status validando (clone
--      EXATO do <confirmacao_barbearia>, mig 83). Cancelar LIBERA o slot na hora.
--      reminded_24h = idempotência do lembrete. Toggle reminder_enabled (default LIGADO).
--
--   #3 AUTO-TRANSIÇÃO (mesmo job): reserva 'confirmada' cujo end_at passou há 2h+ vira
--      'realizada' via service (transição válida; realizada é SILENCIOSA — coerente com o
--      baseline "quem furou não recebe sermão"). no_show NÃO é automático (sem check-in no
--      modelo, marcar falta é julgamento humano). Toggle auto_complete_enabled (default LIGADO,
--      espelho barbearia).
-- =============================================================================

alter table public.restaurant_reservation_config
  add column if not exists reminder_enabled boolean not null default true;
alter table public.restaurant_reservation_config
  add column if not exists auto_complete_enabled boolean not null default true;

comment on column public.restaurant_reservation_config.reminder_enabled is
  'Se true (default), o RestaurantReminderJob pergunta "confirma? SIM/NÃO" na véspera das reservas pendente/confirmada. Ausência de linha = ligado.';
comment on column public.restaurant_reservation_config.auto_complete_enabled is
  'Se true (default), confirmada com end_at passado (2h de folga) vira realizada automaticamente (silencioso).';

alter table public.table_reservations
  add column if not exists reminded_24h boolean not null default false;

comment on column public.table_reservations.reminded_24h is
  'Lembrete de véspera JÁ enviado (idempotência do RestaurantReminderJob — espelho reminded_24h da barbearia, mig 83).';

create index if not exists idx_table_reservations_reminder
  on public.table_reservations (start_at)
  where status in ('pendente','confirmada') and reminded_24h = false;
