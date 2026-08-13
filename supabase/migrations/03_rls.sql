-- =============================================================================
-- 03_rls.sql
-- Meada — Row Level Security: isolamento por tenant em TODAS as tabelas.
--
-- Escopo deste arquivo:
--   - ENABLE + FORCE row level security nas 11 tabelas.
--   - Policies: o padrão é  company_id = app.company_id()  (definida em 01).
--   - Exceções conscientes: companies (é o próprio tenant), users (bootstrap),
--     messages/conversations (sem DELETE — histórico imutável).
--
-- Decisões que governam este arquivo (confirmadas):
--   - RBAC fino (owner/admin/agent) NÃO entra no RLS no MVP. Toda policy isola
--     só por tenant; quem-pode-o-quê dentro da empresa é regra do backend Spring.
--     Quando virar defesa-no-banco, entra um app.has_role() e policies extras.
--   - Ninguém deleta messages/conversations via authenticated: encerrar conversa
--     é UPDATE status='closed', não DELETE. Auditoria íntegra.
--   - service_role tem BYPASSRLS: o backend Spring ignora todas estas policies
--     por design e DEVE passar company_id explícito nas queries (ver Registro 1
--     em 01). FORCE RLS não afeta service_role.
--   - DECISÃO ARQUITETURAL — Spring é ESCRITOR ÚNICO de messages. A tabela
--     messages só tem policy de SELECT para authenticated; NÃO há INSERT/UPDATE/
--     DELETE via PostgREST. TODA escrita de mensagem — inclusive a intervenção
--     do agente humano feita no painel — passa por um endpoint do Spring que
--     grava via service_role e, no mesmo fluxo, dispara o envio pela Evolution.
--     O painel NUNCA insere mensagem direto via PostgREST. Razão: gravar no
--     banco sem garantir o envio no WhatsApp produziria histórico que não
--     espelha o que de fato trafegou — estado inconsistente inaceitável.
--
-- Sobre FORCE: ENABLE liga o RLS para roles comuns, mas o OWNER da tabela ainda
--   o ignoraria. FORCE estende as policies também ao owner — fecha o furo de um
--   job que rode como dono. service_role continua passando (BYPASSRLS).
--
-- Padrão de nomenclatura das policies: <tabela>_<ação>_<escopo>.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Liga RLS (ENABLE + FORCE) em todas as tabelas.
-- -----------------------------------------------------------------------------
alter table companies          enable row level security;
alter table companies          force  row level security;
alter table users              enable row level security;
alter table users              force  row level security;
alter table whatsapp_instances enable row level security;
alter table whatsapp_instances force  row level security;
alter table services           enable row level security;
alter table services           force  row level security;
alter table business_hours     enable row level security;
alter table business_hours     force  row level security;
alter table faqs               enable row level security;
alter table faqs               force  row level security;
alter table documents          enable row level security;
alter table documents          force  row level security;
alter table ai_settings        enable row level security;
alter table ai_settings        force  row level security;
alter table contacts           enable row level security;
alter table contacts           force  row level security;
alter table conversations      enable row level security;
alter table conversations      force  row level security;
alter table messages           enable row level security;
alter table messages           force  row level security;


-- =============================================================================
-- companies — caso especial: NÃO tem coluna company_id, ela É o tenant.
--   A comparação é  id = app.company_id().
--   authenticated lê e edita a própria empresa; NÃO cria nem apaga empresa
--   (onboarding/offboarding de tenant é via service_role).
-- =============================================================================
create policy companies_select_own on companies
  for select to authenticated
  using (id = app.company_id());

create policy companies_update_own on companies
  for update to authenticated
  using (id = app.company_id())
  with check (id = app.company_id());


-- =============================================================================
-- users — leitura por tenant; sem INSERT/DELETE por authenticated.
--   NÃO há policy "self" (id = auth.uid()): seria redundante. app.company_id()
--   é SECURITY DEFINER e lê public.users por design, sem depender de policy —
--   não há bootstrap a resolver. Qualquer SELECT via PostgREST com sessão
--   authenticated cai em users_select_tenant, e a linha do próprio usuário casa
--   porque carrega o mesmo company_id que a função retorna. Não existe caso em
--   que "tenant" não cubra "self": por definição a própria linha tem o company_id
--   do usuário.
--
--   INSERT/DELETE de users por authenticated: NÃO. Criar/remover operador passa
--   pelo backend (cria também o auth.users correspondente). Painel só lê/edita.
-- =============================================================================
create policy users_select_tenant on users
  for select to authenticated
  using (company_id = app.company_id());

create policy users_update_tenant on users
  for update to authenticated
  using (company_id = app.company_id())
  with check (company_id = app.company_id());


-- =============================================================================
-- Tabelas de domínio com padrão completo por tenant.
-- =============================================================================

-- ---- whatsapp_instances -----------------------------------------------------
-- Leitura da linha é por tenant; a coluna evolution_token é blindada no
--   04_grants.sql (column grant), não aqui — RLS filtra LINHAS, não COLUNAS.
-- SEM policy de INSERT/DELETE por authenticated (proposital): criar/conectar e
--   remover instância é operação de backend (service_role) — a instância nasce
--   quando o Spring conecta na Evolution e recebe o evolution_token. O painel só
--   lê e edita phone_number/status (UPDATE), nunca o token (column-grant no 04).
create policy wa_instances_select on whatsapp_instances
  for select to authenticated using (company_id = app.company_id());
create policy wa_instances_update on whatsapp_instances
  for update to authenticated
  using (company_id = app.company_id()) with check (company_id = app.company_id());
-- (sem wa_instances_insert e wa_instances_delete — proposital, via backend)

-- ---- services ---------------------------------------------------------------
create policy services_select on services
  for select to authenticated using (company_id = app.company_id());
create policy services_insert on services
  for insert to authenticated with check (company_id = app.company_id());
create policy services_update on services
  for update to authenticated
  using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy services_delete on services
  for delete to authenticated using (company_id = app.company_id());

-- ---- business_hours ---------------------------------------------------------
create policy business_hours_select on business_hours
  for select to authenticated using (company_id = app.company_id());
create policy business_hours_insert on business_hours
  for insert to authenticated with check (company_id = app.company_id());
create policy business_hours_update on business_hours
  for update to authenticated
  using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy business_hours_delete on business_hours
  for delete to authenticated using (company_id = app.company_id());

-- ---- faqs -------------------------------------------------------------------
create policy faqs_select on faqs
  for select to authenticated using (company_id = app.company_id());
create policy faqs_insert on faqs
  for insert to authenticated with check (company_id = app.company_id());
create policy faqs_update on faqs
  for update to authenticated
  using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy faqs_delete on faqs
  for delete to authenticated using (company_id = app.company_id());

-- ---- documents --------------------------------------------------------------
-- Metadados aqui; o binário e sua policy de acesso vivem no Storage (05).
create policy documents_select on documents
  for select to authenticated using (company_id = app.company_id());
create policy documents_insert on documents
  for insert to authenticated with check (company_id = app.company_id());
create policy documents_update on documents
  for update to authenticated
  using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy documents_delete on documents
  for delete to authenticated using (company_id = app.company_id());

-- ---- ai_settings ------------------------------------------------------------
-- 1 por empresa (UNIQUE(company_id) na tabela). INSERT livre por tenant: a
--   primeira gravação cria; tentativa de segunda esbarra no UNIQUE, não no RLS.
create policy ai_settings_select on ai_settings
  for select to authenticated using (company_id = app.company_id());
create policy ai_settings_insert on ai_settings
  for insert to authenticated with check (company_id = app.company_id());
create policy ai_settings_update on ai_settings
  for update to authenticated
  using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy ai_settings_delete on ai_settings
  for delete to authenticated using (company_id = app.company_id());

-- ---- contacts ---------------------------------------------------------------
create policy contacts_select on contacts
  for select to authenticated using (company_id = app.company_id());
create policy contacts_insert on contacts
  for insert to authenticated with check (company_id = app.company_id());
create policy contacts_update on contacts
  for update to authenticated
  using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy contacts_delete on contacts
  for delete to authenticated using (company_id = app.company_id());


-- =============================================================================
-- conversations / messages — SEM policy de DELETE (histórico imutável).
--   A ausência de policy de DELETE, com RLS ligado, significa que NENHUM DELETE
--   por authenticated passa. Encerrar conversa = UPDATE status='closed'.
--   service_role (BYPASSRLS) ainda pode deletar em rotinas administrativas.
-- =============================================================================

-- ---- conversations ----------------------------------------------------------
create policy conversations_select on conversations
  for select to authenticated using (company_id = app.company_id());
create policy conversations_insert on conversations
  for insert to authenticated with check (company_id = app.company_id());
create policy conversations_update on conversations
  for update to authenticated
  using (company_id = app.company_id()) with check (company_id = app.company_id());
-- (sem conversations_delete — proposital)

-- ---- messages ---------------------------------------------------------------
-- SOMENTE SELECT por authenticated. TODA escrita de mensagem é via backend
--   (service_role): contact (webhook), ai, e a intervenção humana — porque o
--   mesmo fluxo que grava a mensagem dispara o envio pela Evolution. Gravar no
--   banco sem garantir o envio no WhatsApp seria um estado inconsistente.
-- Sem INSERT/UPDATE/DELETE por authenticated — histórico imutável e sempre
--   espelhado no que de fato saiu/entrou pela Evolution.
create policy messages_select on messages
  for select to authenticated using (company_id = app.company_id());
-- (sem messages_insert/update/delete — proposital, escrita só via backend)
