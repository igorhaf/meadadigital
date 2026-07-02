-- =============================================================================
-- 75_academia_day_passes.sql
-- Meada — Academia (camada 7.7): DAY-USE / AULA AVULSA (backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md #8).
--
-- Registro de um acesso pontual (day-use) ou aula avulsa: quem NÃO tem matrícula
-- (assinatura) paga um passe do dia. É só REGISTRO — a COBRANÇA real (link Pix/cartão)
-- espera o gateway #50; aqui entra o passe registrado + a marcação manual de pago.
--
-- Decisões cravadas:
--   - Aluno/visitante NÃO é entidade própria (igual matrícula da 7.7): guest_name/guest_phone
--     são snapshots; contact_id opcional liga ao contato do WhatsApp quando existir.
--   - class_id opcional: o passe pode ser "só day-use" (sem aula específica) ou apontar uma aula.
--     on delete set null — excluir a aula NÃO apaga o histórico de passes.
--   - price_cents preenchido pelo painel (sem tabela de preço de day-use nesta SM).
--   - paid default false: nasce NÃO pago; a marcação de pago é ação manual do tenant (PATCH).
--   - RLS enable+force + policies via app.company_id() + grants (espelha 36/72). INSERT pelo
--     BACKEND (service_role); o tenant SELECT/INSERT/UPDATE via authenticated.
--   - LGPD: sem dado de saúde; guest_* são de contato/identificação.
-- =============================================================================

create table public.academia_day_passes (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  contact_id  uuid        references public.contacts(id) on delete set null,
  guest_name  text        not null check (length(trim(guest_name)) between 1 and 200),
  guest_phone text,
  class_id    uuid        references public.academia_classes(id) on delete set null,
  pass_date   date        not null default current_date,
  price_cents integer     not null check (price_cents >= 0),
  paid        boolean     not null default false,
  created_at  timestamptz not null default now()
);

comment on table public.academia_day_passes is
  'Passes de day-use / aula avulsa do tenant academia (camada 7.7). Só REGISTRO; cobrança real é #50. Aluno não é entidade — guest_* são snapshots; contact_id/class_id opcionais.';

create index idx_academia_day_passes_company_date on public.academia_day_passes (company_id, pass_date desc);
create index idx_academia_day_passes_contact on public.academia_day_passes (contact_id, pass_date desc)
  where contact_id is not null;

alter table public.academia_day_passes enable row level security;
alter table public.academia_day_passes force  row level security;

create policy academia_day_passes_select on public.academia_day_passes
  for select to authenticated using (company_id = app.company_id());
create policy academia_day_passes_insert on public.academia_day_passes
  for insert to authenticated with check (company_id = app.company_id());
create policy academia_day_passes_update on public.academia_day_passes
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.academia_day_passes to authenticated;
grant all on public.academia_day_passes to service_role;
