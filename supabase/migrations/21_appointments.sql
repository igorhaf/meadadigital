-- =============================================================================
-- 21_appointments.sql
-- Meada WhatsApp — Camada 5.19 (Fase C): agendamento real (#59 #60 #63 #64).
--
-- Tabela appointments: um compromisso agendado de um contato para um serviço (ou livre),
-- num horário, com status do ciclo de vida. A IA cria (via sub-objeto confirmed_appointment
-- na resposta — #60), o painel exibe num calendário (#59), um job manda lembretes (#63), e
-- a IA remarca/cancela via intents (#64).
--
-- Decisões de produto cravadas:
--   - Slots em janelas (availability_slots da 5.17) com passo slot_minutes; sem conflito
--     permitido (UNIQUE parcial por (company_id, scheduled_at) entre os ativos).
--   - status: scheduled | completed | cancelled | no_show.
--   - service_id nullable (nem todo agendamento amarra a um serviço cadastrado).
--   - reminded_24h / reminded_2h: flags para o job de lembrete não reenviar (#63).
--
-- RLS por company_id (espelha o resto). service_role escreve (a IA agenda via backend).
-- Audit via trigger genérico.
-- =============================================================================

create table public.appointments (
  id              uuid        primary key default gen_random_uuid(),
  company_id      uuid        not null references public.companies(id) on delete cascade,
  contact_id      uuid        not null references public.contacts(id) on delete cascade,
  conversation_id uuid        references public.conversations(id) on delete set null,
  service_id      uuid        references public.services(id) on delete set null,
  scheduled_at    timestamptz not null,
  status          text        not null default 'scheduled'
                    check (status in ('scheduled', 'completed', 'cancelled', 'no_show')),
  notes           text,
  reminded_24h    boolean     not null default false,
  reminded_2h     boolean     not null default false,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

comment on table public.appointments is
  'Compromissos agendados (camada 5.19 #59/#60). A IA cria; painel exibe; job lembra; intents remarcam/cancelam.';

-- Sem dois agendamentos ATIVOS no mesmo horário pra mesma empresa (sem conflito).
create unique index uq_appointments_no_conflict
  on public.appointments (company_id, scheduled_at)
  where status = 'scheduled';

create index idx_appointments_company_time on public.appointments (company_id, scheduled_at);
create index idx_appointments_contact on public.appointments (contact_id);
-- p/ o job de lembrete varrer os agendados futuros próximos.
create index idx_appointments_reminders on public.appointments (scheduled_at)
  where status = 'scheduled' and (reminded_24h = false or reminded_2h = false);

alter table public.appointments enable row level security;
alter table public.appointments force  row level security;

create policy appointments_select on public.appointments
  for select to authenticated using (company_id = app.company_id());
create policy appointments_insert on public.appointments
  for insert to authenticated with check (company_id = app.company_id());
create policy appointments_update on public.appointments
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy appointments_delete on public.appointments
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.appointments to authenticated;
grant all on public.appointments to service_role;

create trigger trg_appointments_audit after insert or update on public.appointments
  for each row execute function app.audit_trigger();
