-- =============================================================================
-- 83_barbearia_onda1.sql
-- Meada — Onda Barbearia (backlog docs/FEATURES_SUGERIDAS_BARBEARIA.md #1/#3/#4/#7/#12/#15).
--
-- Features EXECUTÁVEIS do nicho barbearia (camada 8.1) sem bloqueador transversal. O #2
-- (reativação de inativo) JÁ É COBERTO pelo ReactivationJob genérico do core (camada 5.21 —
-- ai_settings.reactivation_days + reactivation_message); #5/#6/#14 esperam o gateway #50;
-- #10/#11 esperam o motor de campanha (Onda 3); #13 é o CMS (flag por nicho); #16 é fase própria.
--
--   #1  LEMBRETE + CONFIRMAÇÃO SIM/CANCELAR (BarberReminderJob + tag <confirmacao_barbearia>):
--       o job varre agendamentos 'agendado' nas próximas 24h e pergunta "confirma? SIM/CANCELAR";
--       a resposta do cliente vira a tag (a IA só REFLETE a decisão — nunca decide) e o handler
--       move pra confirmado/cancelado com BARREIRA DE CONTATO. reminded_24h = idempotência.
--   #3  FIDELIDADE "A CADA N CORTES, 1 GRÁTIS" (barber_loyalty_config): conta os agendamentos
--       REALIZADOS do contato antes de criar; count > 0 && count % N == 0 → o novo agendamento sai
--       grátis (desconto = preço, loyalty_applied=true). Materializado no backend, nunca pela IA.
--   #4  UPSELL CONTROLADO (barber_config.upsell_enabled, default OFF): quando LIGADO, a IA pode
--       sugerir UMA vez um serviço complementar DO CATÁLOGO no fechamento (persona condicionada).
--   #7  AUTO-TRANSIÇÃO (BarberAutoTransitionJob): confirmado com end_at passado (+2h de folga) →
--       realizado (silencioso; alimenta fidelidade/relatório); ticket 'aguardando' de dia anterior
--       → expirado. Toggle auto_complete_enabled (default ON).
--   #12 CUPOM VALIDÁVEL NA CONVERSA (barber_coupons): clone do motor adega; a IA passa "cupom" na
--       tag <agendamento_barbearia>; o backend valida e aplica sobre o preço snapshot; inválido NÃO
--       aborta (agendamento sai sem desconto).
--   #15 RELATÓRIOS: sem DDL — agregações (faturamento líquido por mês/barbeiro/serviço + faltas).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- #3 — barber_loyalty_config (1:1 company): a cada N cortes realizados, 1 grátis.
-- ---------------------------------------------------------------------------
create table public.barber_loyalty_config (
  company_id     uuid        primary key references public.companies(id) on delete cascade,
  enabled        boolean     not null default false,
  threshold_cuts integer     not null default 10 check (threshold_cuts >= 1),
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

comment on table public.barber_loyalty_config is
  'Fidelidade por contagem do tenant barbearia (onda 1, backlog #3): a cada threshold_cuts agendamentos REALIZADOS do contato, o próximo sai GRÁTIS (desconto = preço snapshot, loyalty_applied=true), materializado no backend na criação. A IA só INFORMA o saldo (contexto) — nunca aplica por conta própria.';

alter table public.barber_loyalty_config enable row level security;
alter table public.barber_loyalty_config force  row level security;

create policy barber_loy_select on public.barber_loyalty_config for select to authenticated using (company_id = app.company_id());
create policy barber_loy_insert on public.barber_loyalty_config for insert to authenticated with check (company_id = app.company_id());
create policy barber_loy_update on public.barber_loyalty_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.barber_loyalty_config to authenticated;
grant all on public.barber_loyalty_config to service_role;

-- Seed idempotente p/ toda company barbearia (config desligada por default).
insert into public.barber_loyalty_config (company_id) select id from public.companies where profile_id = 'barbearia'
on conflict (company_id) do nothing;

-- ---------------------------------------------------------------------------
-- #12 — barber_coupons: clone do motor de cupom (sushi 69 / academia 77 / adega 80 / atelie 82).
-- ---------------------------------------------------------------------------
create table public.barber_coupons (
  id              uuid        primary key default gen_random_uuid(),
  company_id      uuid        not null references public.companies(id) on delete cascade,
  code            text        not null check (length(trim(code)) between 1 and 40),
  kind            text        not null check (kind in ('percent','fixed')),
  value           integer     not null check (value >= 0),
  min_order_cents integer     not null default 0 check (min_order_cents >= 0),
  max_uses        integer     check (max_uses is null or max_uses >= 0),
  uses            integer     not null default 0 check (uses >= 0),
  valid_until     date,
  active          boolean     not null default true,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  check (kind <> 'percent' or value between 1 and 100)
);

comment on table public.barber_coupons is
  'Cupons de desconto do tenant barbearia (onda 1, backlog #12 — clone do motor adega). A IA passa o code no campo cupom da tag <agendamento_barbearia>; o backend VALIDA (active + valid_until + mínimo sobre o preço do serviço + max_uses) e aplica com clamp ao preço; cupom inválido NÃO aborta (agendamento sai sem desconto). uses incrementa na criação. code único (case-insensitive) por company.';

create unique index uniq_barber_coupon_code on public.barber_coupons (company_id, lower(code));
create index idx_barber_coupons_company_active on public.barber_coupons (company_id, active) where active = true;

alter table public.barber_coupons enable row level security;
alter table public.barber_coupons force  row level security;

create policy barber_coupon_select on public.barber_coupons for select to authenticated using (company_id = app.company_id());
create policy barber_coupon_insert on public.barber_coupons for insert to authenticated with check (company_id = app.company_id());
create policy barber_coupon_update on public.barber_coupons for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy barber_coupon_delete on public.barber_coupons for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.barber_coupons to authenticated;
grant all on public.barber_coupons to service_role;

-- ---------------------------------------------------------------------------
-- #3/#12 — desconto materializado + #1 idempotência do lembrete no agendamento.
-- ---------------------------------------------------------------------------
alter table public.barber_appointments
  add column discount_cents       integer not null default 0 check (discount_cents >= 0),
  add column coupon_id            uuid references public.barber_coupons(id) on delete set null,
  add column coupon_code_snapshot text,
  add column loyalty_applied      boolean not null default false,
  add column reminded_24h         boolean not null default false;

comment on column public.barber_appointments.discount_cents is
  'Desconto aplicado (cupom e/ou fidelidade), materializado na criação e clampado ao preço snapshot. Valor a cobrar = price_cents − discount_cents (quando há preço).';
comment on column public.barber_appointments.loyalty_applied is
  'true = este agendamento saiu GRÁTIS pela fidelidade (a cada N cortes realizados, 1 grátis — backlog #3).';
comment on column public.barber_appointments.reminded_24h is
  'Idempotência do lembrete de confirmação (backlog #1): true = o "confirma? SIM/CANCELAR" das próximas 24h já foi enviado.';

-- Varreduras dos jobs (parciais e baratas).
create index idx_barber_appt_reminder on public.barber_appointments (start_at)
  where status = 'agendado' and reminded_24h = false;
create index idx_barber_appt_confirmed_past on public.barber_appointments (end_at)
  where status = 'confirmado';

-- ---------------------------------------------------------------------------
-- #1/#4/#7 — toggles por tenant na barber_config.
-- ---------------------------------------------------------------------------
alter table public.barber_config
  add column reminder_enabled boolean not null default true,
  add column auto_complete_enabled boolean not null default true,
  add column upsell_enabled boolean not null default false;

comment on column public.barber_config.reminder_enabled is
  'Se true (default), o BarberReminderJob envia o "confirma seu horário? SIM/CANCELAR" nas 24h que antecedem agendamentos ainda em ''agendado''.';
comment on column public.barber_config.auto_complete_enabled is
  'Se true (default), o BarberAutoTransitionJob move confirmado com end_at passado (+2h) → realizado e expira tickets de fila de dias anteriores.';
comment on column public.barber_config.upsell_enabled is
  'Se true (default FALSE — opt-in), a IA pode sugerir UMA única vez um serviço complementar DO CATÁLOGO no fechamento do agendamento (backlog #4). Desligado = persona segue sem sugestão.';
