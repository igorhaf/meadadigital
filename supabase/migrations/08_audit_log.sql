-- =============================================================================
-- 08_audit_log.sql
-- Meada — Camada 5.3: audit log enxuto (persistência sem tela).
--
-- Registra ações sensíveis por tenant. Duas vias de escrita:
--   1. Backend Spring (service_role) via AuditLogger Java — ex.: company.created
--      (o super-admin cria empresa por endpoint REST, fora do SDK do tenant).
--   2. Trigger Postgres app.audit_trigger() (SECURITY DEFINER) — mutations que o
--      TENANT faz via SDK direto (services/faqs/ai_settings/conversations).
--
-- Por que SECURITY DEFINER no trigger (decisão cravada): o tenant (role
--   authenticated) tem GRANT INSERT/UPDATE em services/faqs/etc., mas NÃO tem
--   GRANT INSERT em audit_log (escrita protegida — só service_role/trigger). Um
--   trigger normal rodaria com os privilégios de authenticated e falharia em
--   "permission denied" ao inserir em audit_log. SECURITY DEFINER faz a função
--   rodar com os privilégios do DONO (postgres no Supabase — superuser, BYPASSRLS
--   e dono de audit_log), então o INSERT passa. É bypass LEGÍTIMO e contido: a
--   função só insere em audit_log com dados derivados do NEW da própria operação
--   que o tenant já tinha permissão de fazer.
--
-- RLS: tenant LÊ só logs da própria empresa (audit_log_select_own). NÃO há policy
--   de INSERT/UPDATE/DELETE para authenticated — escrita só via service_role
--   (Java) ou via o trigger SECURITY DEFINER. Espelha o padrão de messages
--   (escrita protegida, leitura por tenant).
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Tabela
-- -----------------------------------------------------------------------------
create table public.audit_log (
  id         uuid        primary key default gen_random_uuid(),
  company_id uuid        not null references public.companies(id) on delete restrict,
  user_id    uuid        references auth.users(id) on delete set null,
  action     text        not null,
  entity     text        not null,
  entity_id  uuid,
  metadata   jsonb       not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

comment on table public.audit_log is
  'Log de ações sensíveis por tenant (camada 5.3). Escrita via service_role (Java) ou trigger SECURITY DEFINER (SDK do tenant); leitura por tenant via RLS. Sem tela — consulta via psql/REST futuro.';

create index idx_audit_log_company_created on public.audit_log (company_id, created_at desc);


-- -----------------------------------------------------------------------------
-- RLS — tenant lê só os próprios; escrita protegida (sem policy de INSERT)
-- -----------------------------------------------------------------------------
alter table public.audit_log enable row level security;
alter table public.audit_log force  row level security;

create policy audit_log_select_own on public.audit_log
  for select to authenticated
  using (company_id = app.company_id());
-- (sem policy de INSERT/UPDATE/DELETE para authenticated — proposital:
--  escrita só via service_role ou via app.audit_trigger() SECURITY DEFINER)


-- -----------------------------------------------------------------------------
-- Grants — leitura por tenant; acesso total ao backend
-- -----------------------------------------------------------------------------
grant select on public.audit_log to authenticated;
grant all    on public.audit_log to service_role;


-- -----------------------------------------------------------------------------
-- Função de trigger — SECURITY DEFINER (roda como dono, bypassa RLS de INSERT)
--   search_path fixo (public, app) por segurança: SECURITY DEFINER sem search_path
--   travado é vetor de hijack (objeto malicioso num schema do path do caller).
-- -----------------------------------------------------------------------------
create or replace function app.audit_trigger() returns trigger
language plpgsql
security definer
set search_path = public, app
as $$
begin
  insert into public.audit_log (company_id, user_id, action, entity, entity_id, metadata)
  values (
    new.company_id,
    auth.uid(),                          -- nullable; o schema permite user_id null
    lower(tg_op),                        -- 'insert' | 'update'
    tg_table_name,                       -- 'services' | 'faqs' | 'ai_settings' | 'conversations'
    new.id,
    to_jsonb(new) - 'company_id' - 'id'  -- metadata sem repetir as colunas já estruturadas
  );
  return new;
end $$;

grant execute on function app.audit_trigger() to authenticated;


-- -----------------------------------------------------------------------------
-- Triggers — AFTER INSERT OR UPDATE nas 4 tabelas de domínio do tenant
-- -----------------------------------------------------------------------------
create trigger trg_services_audit      after insert or update on public.services
  for each row execute function app.audit_trigger();
create trigger trg_faqs_audit          after insert or update on public.faqs
  for each row execute function app.audit_trigger();
create trigger trg_ai_settings_audit   after insert or update on public.ai_settings
  for each row execute function app.audit_trigger();
create trigger trg_conversations_audit after insert or update on public.conversations
  for each row execute function app.audit_trigger();
