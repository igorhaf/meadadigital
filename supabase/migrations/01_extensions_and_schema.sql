-- =============================================================================
-- 01_extensions_and_schema.sql
-- Meada — fundação: extensões e o schema `app`.
--
-- Escopo deste arquivo (e SOMENTE este):
--   1. Extensões necessárias.
--   2. Schema `app` — lar dos helpers de aplicação (tenant, RBAC, audit).
--
-- A função app.company_id() NÃO está aqui: ela lê public.users e CREATE FUNCTION
--   em SQL valida o corpo na criação, então precisa rodar DEPOIS de 02_tables.sql
--   criar a tabela users. Por isso a função vive no fim do 02. (Ver comentário lá.)
--
-- Nenhuma tabela, policy de tabela ou grant de tabela aqui. Eles vivem em 02..05.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Extensões
-- -----------------------------------------------------------------------------
-- pgcrypto fornece gen_random_uuid(), usado como default de toda PK uuid.
-- No Supabase já costuma vir habilitada, mas declaramos para a migration ser
-- auto-suficiente (rodar do zero em qualquer projeto novo).
create extension if not exists pgcrypto;


-- -----------------------------------------------------------------------------
-- 2. Schema `app` — namespace dos helpers de aplicação
-- -----------------------------------------------------------------------------
-- Por que NÃO usar o schema `auth`:
--   O schema `auth` é reservado pela plataforma Supabase (tabelas internas,
--   auth.uid(), auth.jwt(), triggers de signup). Criar funções nossas lá
--   funciona, mas uma migration de plataforma poderia colidir. `app` é nosso,
--   convive com auth.uid() e dá lar a futuros app.is_admin(), app.has_role(),
--   app.* de audit — sem refator de policies depois.
create schema if not exists app;

comment on schema app is
  'Helpers de aplicação (tenant, RBAC, audit). Namespace próprio, fora do schema auth reservado pela Supabase.';

-- Quem enxerga o schema. EXECUTE nas funções é concedido individualmente (no 02,
-- junto da definição de app.company_id()).
grant usage on schema app to authenticated, service_role;
