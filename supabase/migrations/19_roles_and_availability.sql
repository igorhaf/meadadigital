-- =============================================================================
-- 19_roles_and_availability.sql
-- Meada — Camada 5.17 (Fase A): hierarquia de roles (#75) + slots (#61).
--
-- #75 HIERARQUIA DE ROLES (owner/admin/agent):
--   users.role já tem CHECK (owner|admin|agent) desde o schema base. Esta migration:
--   - adiciona companies.owner_id (FK users) — o dono da empresa (pode tudo, inclusive
--     deletar a empresa e, futuramente, billing).
--   - cria app.user_role() — helper SECURITY DEFINER que lê o role do usuário logado
--     (espelha app.company_id()), para guards de autorização no backend e em policies.
--   - backfill: owner_id = o user role='owner' da empresa, senão o admin/owner mais antigo.
--   Decisão de produto cravada: agent VÊ tudo (navegação não é restringida por role nesta
--   fase); o role governa CAPACIDADE em ações sensíveis (deletar empresa, billing), via
--   guards pontuais no backend — não esconde telas. Por isso NÃO refinamos as policies de
--   RLS por role aqui (continuam por company_id).
--
-- #61 SLOTS DE DISPONIBILIDADE:
--   availability_slots define janelas de atendimento agendável por dia da semana
--   (weekday 0=domingo..6=sábado), com duração de slot em minutos. Diferente de
--   business_hours (que é "a empresa está aberta?"), isto é "quais horários podem ser
--   AGENDADOS" — base para appointments (#59/#60). Sem conflito: o backend gera os slots
--   concretos a partir destas janelas e checa contra appointments existentes.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- #75 — companies.owner_id + app.user_role()
-- ---------------------------------------------------------------------------
alter table public.companies
  add column owner_id uuid references public.users(id) on delete set null;

-- helper: role do usuário logado (owner|admin|agent), ou null se sem linha em users.
create or replace function app.user_role() returns text
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select role from public.users where id = auth.uid()
$$;

grant execute on function app.user_role() to authenticated;

-- backfill owner_id: prefere o user role='owner'; senão o mais antigo (owner/admin).
update public.companies c set owner_id = (
  select u.id from public.users u
  where u.company_id = c.id
  order by (u.role = 'owner') desc, (u.role = 'admin') desc, u.created_at asc
  limit 1
) where owner_id is null;


-- ---------------------------------------------------------------------------
-- #61 — availability_slots (janelas agendáveis)
-- ---------------------------------------------------------------------------
create table public.availability_slots (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete cascade,
  weekday      smallint    not null check (weekday between 0 and 6),
  starts_at    time        not null,
  ends_at      time        not null,
  slot_minutes smallint    not null default 30 check (slot_minutes between 5 and 480),
  active       boolean     not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  check (ends_at > starts_at)
);

comment on table public.availability_slots is
  'Janelas agendáveis por dia (camada 5.17 #61). Base para os slots concretos de appointments. Diferente de business_hours (aberto?) — isto é "agendável?".';

create index idx_availability_slots_company on public.availability_slots (company_id) where active;

alter table public.availability_slots enable row level security;
alter table public.availability_slots force  row level security;

create policy availability_slots_select on public.availability_slots
  for select to authenticated using (company_id = app.company_id());
create policy availability_slots_insert on public.availability_slots
  for insert to authenticated with check (company_id = app.company_id());
create policy availability_slots_update on public.availability_slots
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy availability_slots_delete on public.availability_slots
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.availability_slots to authenticated;
grant all on public.availability_slots to service_role;

create trigger trg_availability_slots_audit after insert or update on public.availability_slots
  for each row execute function app.audit_trigger();
