-- =============================================================================
-- 63_escola.sql
-- Meada WhatsApp — Camada 8.19 (SM: perfil Escola / Educação infantil). Tabelas exclusivas do
-- perfil 'escola': config, turmas, alunos (sub-entidade do responsável), matrículas (assinaturas),
-- pagamentos manuais (mensalidade) e visitas agendadas.
--
-- CLONA o chassi de MATRÍCULA/ASSINATURA da ACADEMIA (camada 7.7): planos→TURMAS (série/turno/
-- capacity/mensalidade), matrícula = ASSINATURA (ativa/suspensa/cancelada), anti-dupla, capacity por
-- turma validado TRANSACIONALMENTE no INSERT, pagamento mensal MANUAL (UNIQUE por mês). DUAS escapadas
-- NOVAS que a academia não tem:
--
--   ESCAPADA 1 — O ALUNO É SUB-ENTIDADE DO RESPONSÁVEL (espelho pet_animals/nutri_patients): na
--     academia o "aluno" era o PRÓPRIO contato (snapshot). Aqui quem fala no WhatsApp é o RESPONSÁVEL
--     (pai/mãe = contact), e o ALUNO (filho) é uma SUB-ENTIDADE persistente (escola_students com
--     contact_id NOT NULL). UM responsável tem N alunos. A matrícula referencia student_id. Anti-dupla
--     passa a ser POR ALUNO POR TURMA (1 matrícula ativa do mesmo aluno na mesma turma), não por
--     contato — um irmão pode estar em outra turma; o mesmo aluno não pode ter 2 matrículas ativas na
--     MESMA turma.
--
--   ESCAPADA 2 — AGENDAMENTO DE VISITA À ESCOLA (agenda LEVE dia+período, espelho floricultura date+
--     period — NÃO slot fino): além da matrícula, a IA AGENDA uma VISITA da família — visit_date (>=
--     hoje) + period (manha|tarde). SEM conflito de capacidade (várias visitas no mesmo período são
--     OK), SEM slot fino. Entidade própria (escola_visits) com status próprio. A visita NÃO depende de
--     matrícula nem de aluno (student_id nullable — a família pode visitar antes de escolher).
--
-- Convenções (padrão das migrations 30-53; academia 36 + pet 37 são as referências diretas):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - escola_enrollments / escola_visits: INSERT pelo BACKEND (service_role) — criadas pela IA via
--     handler; tenant SELECT/UPDATE (status no painel / gate humano de confirmação e mensalidade).
--   - escola_payments: INSERT via service_role (registro pelo painel); tenant SELECT (espelho
--     academia_payments).
--   - end_date / status materializados; valores em centavos. NÃO colunas geradas.
--   - SNAPSHOTS: a matrícula congela student_name + responsible_name + class_name + class_grade +
--     class_shift + class_monthly_cents. Alterar a turma/aluno depois NÃO altera matrículas passadas.
--   - LGPD: notes (aluno/matrícula/visita) é administrativo, SEM dado pedagógico/de saúde.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'escola' (19º perfil real; 20º contando generic).
-- A lista ESPELHA a CHECK mais recente (53_adega, 18 perfis) e APENDA 'escola' — nenhum nicho some.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola'));

-- ---------------------------------------------------------------------------
-- escola_config — config 1:1 com company (nome da escola + horário de funcionamento).
-- ---------------------------------------------------------------------------
create table public.escola_config (
  company_id    uuid        primary key references public.companies(id) on delete cascade,
  business_name text,
  opens_at      time        not null default '07:00',
  closes_at     time        not null default '18:00',
  notes         text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.escola_config is
  'Config do tenant escola (camada 8.19): nome da escola + horário de funcionamento. 1:1 com company. Ausente → defaults (07:00/18:00).';

alter table public.escola_config enable row level security;
alter table public.escola_config force  row level security;

create policy escola_config_select on public.escola_config
  for select to authenticated using (company_id = app.company_id());
create policy escola_config_insert on public.escola_config
  for insert to authenticated with check (company_id = app.company_id());
create policy escola_config_update on public.escola_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.escola_config to authenticated;
grant all on public.escola_config to service_role;

-- ---------------------------------------------------------------------------
-- escola_classes — TURMAS (espelho academia_plans + campos de turma).
-- ---------------------------------------------------------------------------
create table public.escola_classes (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  name          text        not null check (length(trim(name)) between 1 and 200),
  grade         text        not null check (length(trim(grade)) between 1 and 100),  -- série/ano (texto livre informativo)
  shift         text        not null check (shift in ('manha','tarde','integral')),
  capacity      integer     not null check (capacity between 1 and 200),
  monthly_cents integer     not null check (monthly_cents >= 0),                      -- mensalidade
  year          integer     check (year between 2000 and 2100),                       -- ano letivo (opcional)
  description   text,
  active        boolean     not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.escola_classes is
  'Turmas do tenant escola (camada 8.19). shift = manha/tarde/integral; capacity = máx. de matrículas não-canceladas; monthly_cents = mensalidade (entra como SNAPSHOT na matrícula). grade = série/ano texto livre.';

create index idx_escola_classes_company_active on public.escola_classes (company_id, active)
  where active = true;

alter table public.escola_classes enable row level security;
alter table public.escola_classes force  row level security;

create policy escola_classes_select on public.escola_classes
  for select to authenticated using (company_id = app.company_id());
create policy escola_classes_insert on public.escola_classes
  for insert to authenticated with check (company_id = app.company_id());
create policy escola_classes_update on public.escola_classes
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy escola_classes_delete on public.escola_classes
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.escola_classes to authenticated;
grant all on public.escola_classes to service_role;

-- ---------------------------------------------------------------------------
-- escola_students — ALUNOS (sub-entidade do contact/responsável — espelho pet_animals).
-- ---------------------------------------------------------------------------
create table public.escola_students (
  id             uuid        primary key default gen_random_uuid(),
  company_id     uuid        not null references public.companies(id) on delete restrict,
  contact_id     uuid        not null references public.contacts(id) on delete restrict,  -- RESPONSÁVEL (pai/mãe)
  name           text        not null check (length(trim(name)) between 1 and 200),
  birth_date     date,
  intended_grade text,        -- série pretendida (informativo)
  notes          text,        -- administrativo (LGPD), SEM dado pedagógico/de saúde
  active         boolean     not null default true,  -- false = arquivado (não perde histórico)
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

comment on table public.escola_students is
  'Alunos do tenant escola (camada 8.19). SUB-ENTIDADE do contact (responsável/pai) — persiste entre conversas; um contact tem N alunos. active=false arquiva sem perder histórico. notes administrativo, SEM parecer/diagnóstico (LGPD).';

create index idx_escola_students_company_contact_active on public.escola_students (company_id, contact_id, active) where active = true;
create index idx_escola_students_company_name on public.escola_students (company_id, name);

alter table public.escola_students enable row level security;
alter table public.escola_students force  row level security;

create policy escola_students_select on public.escola_students for select to authenticated using (company_id = app.company_id());
create policy escola_students_insert on public.escola_students for insert to authenticated with check (company_id = app.company_id());
create policy escola_students_update on public.escola_students for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy escola_students_delete on public.escola_students for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.escola_students to authenticated;
grant all on public.escola_students to service_role;

-- ---------------------------------------------------------------------------
-- escola_enrollments — MATRÍCULAS (assinatura — espelho academia_memberships, MAS referencia student_id).
-- ---------------------------------------------------------------------------
create table public.escola_enrollments (
  id                  uuid        primary key default gen_random_uuid(),
  company_id          uuid        not null references public.companies(id) on delete restrict,
  class_id            uuid        not null references public.escola_classes(id) on delete restrict,
  student_id          uuid        not null references public.escola_students(id) on delete restrict,
  conversation_id     uuid        references public.conversations(id) on delete set null,
  contact_id          uuid        references public.contacts(id) on delete set null,         -- o responsável (snapshot/notify)
  student_name        text        not null,   -- snapshot
  responsible_name    text,                   -- snapshot opcional (nome do responsável)
  class_name          text        not null,   -- snapshot
  class_grade         text        not null,   -- snapshot
  class_shift         text        not null,   -- snapshot
  class_monthly_cents integer     not null,   -- snapshot
  start_date          date        not null default current_date,
  end_date            date,                   -- materializado em cancelada
  status              text        not null default 'ativa' check (status in ('ativa','suspensa','cancelada')),
  notes               text,
  created_at          timestamptz not null default now(),
  status_updated_at   timestamptz not null default now()
);

comment on table public.escola_enrollments is
  'Matrículas (assinaturas) do tenant escola (camada 8.19). RECORRÊNCIA INDEFINIDA: ativa-até-cancelar. Referencia student_id (o aluno sub-entidade). Capacity por turma validado DENTRO da transação no INSERT (defesa race). Anti-dupla = 1 matrícula ATIVA por (aluno, turma). end_date só em cancelada. snapshots de aluno + turma.';

create index idx_escola_enrollments_company_status on public.escola_enrollments (company_id, status, start_date desc);
create index idx_escola_enrollments_contact on public.escola_enrollments (contact_id, start_date desc)
  where contact_id is not null;
create index idx_escola_enrollments_student on public.escola_enrollments (student_id, start_date desc);
create index idx_escola_enrollments_class on public.escola_enrollments (class_id);
-- ANTI-DUPLA (a escapada): 1 matrícula ATIVA do MESMO aluno na MESMA turma. Um irmão pode estar em
-- outra turma; o mesmo aluno pode estar em turmas diferentes — a unicidade é por (aluno, turma).
create unique index uniq_active_enrollment_per_student_class on public.escola_enrollments (company_id, student_id, class_id)
  where status = 'ativa';

alter table public.escola_enrollments enable row level security;
alter table public.escola_enrollments force  row level security;

create policy escola_enrollments_select on public.escola_enrollments
  for select to authenticated using (company_id = app.company_id());
create policy escola_enrollments_update on public.escola_enrollments
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.escola_enrollments to authenticated;
grant all on public.escola_enrollments to service_role;

-- ---------------------------------------------------------------------------
-- escola_payments — MENSALIDADE manual (espelho academia_payments).
-- ---------------------------------------------------------------------------
create table public.escola_payments (
  id              uuid        primary key default gen_random_uuid(),
  company_id      uuid        not null references public.companies(id) on delete restrict,
  enrollment_id   uuid        not null references public.escola_enrollments(id) on delete restrict,
  reference_month date        not null,   -- sempre dia 01 do mês de referência
  paid_at         timestamptz not null default now(),
  amount_cents    integer     not null check (amount_cents >= 0),
  method          text,                   -- "dinheiro", "Pix", "transferência"
  notes           text,
  created_at      timestamptz not null default now(),
  unique (enrollment_id, reference_month)
);

comment on table public.escola_payments is
  'Mensalidades manuais do tenant escola (camada 8.19). UNIQUE (enrollment, reference_month) impede duplicidade no mês. SEM cobrança automática (Stripe é #50, futuro); SEM inadimplência/juros.';

create index idx_escola_payments_company_month on public.escola_payments (company_id, reference_month desc);

alter table public.escola_payments enable row level security;
alter table public.escola_payments force  row level security;

create policy escola_payments_select on public.escola_payments
  for select to authenticated using (company_id = app.company_id());

grant select on public.escola_payments to authenticated;
grant all on public.escola_payments to service_role;

-- ---------------------------------------------------------------------------
-- escola_visits — VISITA agendada (escapada 2; agenda LEVE dia+período — espelho floricultura).
-- ---------------------------------------------------------------------------
create table public.escola_visits (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  conversation_id   uuid        references public.conversations(id) on delete set null,
  contact_id        uuid        references public.contacts(id) on delete set null,            -- o responsável
  student_id        uuid        references public.escola_students(id) on delete set null,     -- nullable (pode visitar antes de escolher)
  visitor_name      text        not null,   -- snapshot (nome do responsável)
  visitor_phone     text,                   -- snapshot opcional
  visit_date        date        not null,
  period            text        not null check (period in ('manha','tarde')),
  num_people        integer     check (num_people >= 1),
  status            text        not null default 'agendada' check (status in ('agendada','realizada','cancelada')),
  notes             text,
  created_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.escola_visits is
  'Visitas agendadas à escola (camada 8.19, ESCAPADA 2). Agenda LEVE: visit_date + period (manha|tarde), SEM conflito de capacidade, SEM slot fino. Entidade própria com status próprio. A visita independe de matrícula e de aluno (student_id nullable). INSERT pelo backend via handler; tenant SELECT/UPDATE.';

create index idx_escola_visits_company_status_date on public.escola_visits (company_id, status, visit_date);

alter table public.escola_visits enable row level security;
alter table public.escola_visits force  row level security;

create policy escola_visits_select on public.escola_visits
  for select to authenticated using (company_id = app.company_id());
create policy escola_visits_update on public.escola_visits
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.escola_visits to authenticated;
grant all on public.escola_visits to service_role;
