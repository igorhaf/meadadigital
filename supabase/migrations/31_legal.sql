-- =============================================================================
-- 31_legal.sql
-- Meada WhatsApp — Camada 7.2 (SM-C: perfil Legal / ProcessoBot). Segundo perfil vertical
-- real, no mesmo padrão do sushi (SM-B). Tabelas exclusivas do perfil 'legal':
-- clientes do escritório, processos e andamentos.
--
-- Convenções (padrão das migrations anteriores + SM-B):
--   - RLS enable + force; policies do tenant via app.company_id(); grants authenticated +
--     service_role.
--   - legal_clients DESACOPLADO de contacts: name obrigatório; email/phone/document/contact_id
--     opcionais. contact_id (nullable, ON DELETE SET NULL) liga o cliente jurídico ao contato
--     do WhatsApp — a IA resolve contact → legal_client → processos.
--   - CNJ armazenado SEM máscara (20 dígitos), UNIQUE por (company_id, cnj_number). O frontend
--     formata para exibição. Validação mód 97 é no backend (LegalCnjValidator), não no banco.
--   - updated_at mantido pelos repositórios (set updated_at = now() no UPDATE).
--   - Status hardcoded (CHECK) em sync com LegalCaseStatus.java + legal-case-status.ts
--     (LegalCaseStatusParityTest garante a paridade Java↔TS).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- legal_clients — clientes do escritório (catálogo, desacoplado de contacts)
-- ---------------------------------------------------------------------------
create table public.legal_clients (
  id         uuid        primary key default gen_random_uuid(),
  company_id uuid        not null references public.companies(id) on delete restrict,
  name       text        not null check (length(trim(name)) between 1 and 200),
  email      text,
  phone      text,
  document   text,
  contact_id uuid        references public.contacts(id) on delete set null,
  notes      text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.legal_clients is
  'Clientes do escritório (camada 7.2). Desacoplado de contacts: o escritório cadastra antes do cliente mandar WhatsApp. contact_id (nullable) liga ao contato do WhatsApp — a IA resolve contact → legal_client → processos.';

create index idx_legal_clients_company_name on public.legal_clients (company_id, name);
create index idx_legal_clients_contact on public.legal_clients (company_id, contact_id)
  where contact_id is not null;

alter table public.legal_clients enable row level security;
alter table public.legal_clients force  row level security;

create policy legal_clients_select on public.legal_clients
  for select to authenticated using (company_id = app.company_id());
create policy legal_clients_insert on public.legal_clients
  for insert to authenticated with check (company_id = app.company_id());
create policy legal_clients_update on public.legal_clients
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy legal_clients_delete on public.legal_clients
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.legal_clients to authenticated;
grant all on public.legal_clients to service_role;

-- ---------------------------------------------------------------------------
-- legal_cases — processos
-- ---------------------------------------------------------------------------
create table public.legal_cases (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  legal_client_id   uuid        not null references public.legal_clients(id) on delete restrict,
  cnj_number        text        not null,
  title             text        not null check (length(trim(title)) between 1 and 200),
  description       text,
  court             text,
  forum             text,
  subject           text,
  status            text        not null default 'ativo' check (status in
                      ('ativo','suspenso','arquivado','encerrado')),
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now(),
  unique (company_id, cnj_number)
);

comment on table public.legal_cases is
  'Processos do tenant legal (camada 7.2). cnj_number sem máscara (20 dígitos), validado mód 97 no backend. Status com transição livre.';

create index idx_legal_cases_company_status on public.legal_cases (company_id, status, updated_at desc);
create index idx_legal_cases_client on public.legal_cases (legal_client_id);

alter table public.legal_cases enable row level security;
alter table public.legal_cases force  row level security;

create policy legal_cases_select on public.legal_cases
  for select to authenticated using (company_id = app.company_id());
create policy legal_cases_insert on public.legal_cases
  for insert to authenticated with check (company_id = app.company_id());
create policy legal_cases_update on public.legal_cases
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy legal_cases_delete on public.legal_cases
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.legal_cases to authenticated;
grant all on public.legal_cases to service_role;

-- ---------------------------------------------------------------------------
-- legal_case_updates — andamentos manuais (timeline)
-- ---------------------------------------------------------------------------
create table public.legal_case_updates (
  id            uuid        primary key default gen_random_uuid(),
  legal_case_id uuid        not null references public.legal_cases(id) on delete cascade,
  title         text        not null check (length(trim(title)) between 1 and 200),
  body          text,
  occurred_at   timestamptz not null,
  created_at    timestamptz not null default now()
);

comment on table public.legal_case_updates is
  'Andamentos manuais de um processo (camada 7.2). Registrados pelo advogado. occurred_at pode ser passado. NÃO disparam notificação automática.';

create index idx_legal_case_updates_case on public.legal_case_updates (legal_case_id, occurred_at desc);

alter table public.legal_case_updates enable row level security;
alter table public.legal_case_updates force  row level security;

-- Tenant SELECT/INSERT/DELETE só dos andamentos de processos da própria empresa (via join).
create policy legal_case_updates_select on public.legal_case_updates
  for select to authenticated using (
    exists (select 1 from public.legal_cases c
            where c.id = legal_case_id and c.company_id = app.company_id()));
create policy legal_case_updates_insert on public.legal_case_updates
  for insert to authenticated with check (
    exists (select 1 from public.legal_cases c
            where c.id = legal_case_id and c.company_id = app.company_id()));
create policy legal_case_updates_delete on public.legal_case_updates
  for delete to authenticated using (
    exists (select 1 from public.legal_cases c
            where c.id = legal_case_id and c.company_id = app.company_id()));

grant select, insert, delete on public.legal_case_updates to authenticated;
grant all on public.legal_case_updates to service_role;
