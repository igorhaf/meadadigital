-- =============================================================================
-- 76_academia_referrals.sql
-- Meada — Academia (camada 7.7): programa de INDICAÇÃO ("indique um amigo").
--
-- Feature de CRESCIMENTO (backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md #9): um aluno indica
-- um amigo; a academia gera um CÓDIGO único, entrega ao indicador, e quando a indicação
-- converte (o amigo matricula) o tenant marca a indicação como 'convertida' e concede um
-- DESCONTO LOCAL (reward_percent) — SEM cashback, SEM crédito financeiro, SEM gateway. A
-- concessão real do desconto é operação manual do tenant (registrar no pagamento); aqui
-- entra apenas o rastreio da indicação e do código.
--
-- Convenções (padrão das migrations 36/72):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - academia_referrals: INSERT/UPDATE via BACKEND (service_role) — a IA não cria/converte;
--     o tenant lê (SELECT) e o backend gera o código + converte. Espelha o padrão das outras
--     tabelas do nicho (SELECT/UPDATE ao tenant, mutação estrutural no backend).
--   - code UNIQUE por company (o indicador divulga o código; único no escopo do tenant).
--   - status: pendente (gerada) → convertida (amigo matriculou) | expirada (não converteu).
--   - referrer_contact_id nullable: o indicador pode ser um contato conhecido (aluno) ou
--     externo (nome/telefone livres). on delete set null preserva a indicação se o contato some.
-- =============================================================================

create table public.academia_referrals (
  id                  uuid        primary key default gen_random_uuid(),
  company_id          uuid        not null references public.companies(id) on delete restrict,
  referrer_contact_id uuid        references public.contacts(id) on delete set null,
  referred_name       text        not null check (length(trim(referred_name)) between 1 and 200),
  referred_phone      text,
  code                text        not null check (length(trim(code)) between 1 and 40),
  status              text        not null default 'pendente'
                        check (status in ('pendente','convertida','expirada')),
  reward_percent      integer     check (reward_percent is null or (reward_percent between 1 and 100)),
  created_at          timestamptz not null default now(),
  converted_at        timestamptz,
  unique (company_id, code)
);

comment on table public.academia_referrals is
  'Programa de indicação do tenant academia (camada 7.7). Código único por company; status pendente→convertida/expirada. reward_percent = desconto LOCAL concedido na conversão (sem cashback/gateway).';

create index idx_academia_referrals_company_status
  on public.academia_referrals (company_id, status, created_at desc);
create index idx_academia_referrals_referrer
  on public.academia_referrals (referrer_contact_id)
  where referrer_contact_id is not null;

alter table public.academia_referrals enable row level security;
alter table public.academia_referrals force  row level security;

create policy academia_referrals_select on public.academia_referrals
  for select to authenticated using (company_id = app.company_id());
create policy academia_referrals_insert on public.academia_referrals
  for insert to authenticated with check (company_id = app.company_id());
create policy academia_referrals_update on public.academia_referrals
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.academia_referrals to authenticated;
grant all on public.academia_referrals to service_role;
