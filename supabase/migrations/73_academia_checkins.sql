-- =============================================================================
-- 73_academia_checkins.sql
-- Meada — Academia (camada 7.7): CHECK-IN / FREQUÊNCIA (backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md #4).
--
-- Feature de OPERAÇÃO/RETENÇÃO: hoje o sistema sabe quem está MATRICULADO, mas não quem realmente
-- APARECE. Esta tabela registra a presença do aluno por aula (matrícula + aula + dia), servindo de
-- alicerce para reativação de inativo (#3), relatório de frequência e fidelidade por assiduidade (#12).
--
-- Decisões cravadas:
--   - checkin por (matrícula, aula) no DIA — UNIQUE (membership_id, class_id, checkin_date) impede
--     duplicata de presença no mesmo dia (idempotente; 2ª tentativa no dia → 409 duplicate_checkin).
--   - source: 'ia' (o aluno avisou "cheguei" pela IA) ou 'painel' (recepção marcou). Default 'painel'.
--   - checkin_date é a data (dia) da presença; checkin_at é o instante exato do registro.
--   - Convenções (padrão 36_academia.sql / 72): RLS enable+force; policies via app.company_id();
--     grants authenticated (select/insert) + service_role all. INSERT do painel via authenticated;
--     o registro pela IA (service_role) também funciona.
-- =============================================================================

create table public.academia_checkins (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  membership_id uuid        not null references public.academia_memberships(id) on delete cascade,
  class_id      uuid        not null references public.academia_classes(id) on delete cascade,
  checkin_date  date        not null default current_date,
  checkin_at    timestamptz not null default now(),
  source        text        not null default 'painel' check (source in ('ia','painel')),
  notes         text,
  created_at    timestamptz not null default now(),
  unique (membership_id, class_id, checkin_date)
);

comment on table public.academia_checkins is
  'Check-ins / frequência do tenant academia (camada 7.7, feature #4). UNIQUE (membership, class, checkin_date) impede presença duplicada no dia. source ia|painel. Alicerce de reativação/relatório de frequência.';

create index idx_academia_checkins_company_date on public.academia_checkins (company_id, checkin_date desc);
create index idx_academia_checkins_class_date on public.academia_checkins (class_id, checkin_date desc);
create index idx_academia_checkins_membership on public.academia_checkins (membership_id, checkin_date desc);

alter table public.academia_checkins enable row level security;
alter table public.academia_checkins force  row level security;

create policy academia_checkins_select on public.academia_checkins
  for select to authenticated using (company_id = app.company_id());
create policy academia_checkins_insert on public.academia_checkins
  for insert to authenticated with check (company_id = app.company_id());
create policy academia_checkins_update on public.academia_checkins
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy academia_checkins_delete on public.academia_checkins
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.academia_checkins to authenticated;
grant all on public.academia_checkins to service_role;
