-- =============================================================================
-- 29_company_profile.sql
-- Meada — Camada 7.0 (fundação multi-perfil). Adiciona companies.profile_id:
-- o discriminador vertical do tenant. Meada é um monolito que se apresenta como N
-- produtos verticais ("perfis"); o perfil é CRAVADO pelo root ao criar a empresa, o
-- tenant não escolhe.
--
-- Valores HARDCODED, em sync com:
--   - src/main/java/com/meada/profiles/ProfileType.java
--   - frontend/lib/profiles/profile-type.ts
-- O ProfileTypeParityTest garante a paridade Java↔TS; a CHECK constraint aqui garante
-- que o banco só aceita os 4 ids vigentes.
--
-- Migração das empresas existentes: o DEFAULT 'generic' já preenche toda linha existente
-- no momento do ALTER (NOT NULL DEFAULT aplica a todas), então TODAS as empresas atuais
-- (Alpha, Meada Delta, etc.) passam a 'generic' silenciosamente. O UPDATE explícito abaixo
-- é defensivo/idempotente (clareza — caso alguma linha já tivesse a coluna por replay).
--
-- NÃO cria tabela palettes (decisão SM3 mantida) nem tabela de perfis (perfis são
-- hardcoded — adicionar perfil = editar enum + const + esta CHECK, sem tabela).
-- =============================================================================

alter table public.companies
  add column profile_id text not null default 'generic'
    check (profile_id in ('generic','legal','dental','sushi'));

comment on column public.companies.profile_id is
  'Perfil vertical da empresa (camada 7.0). Cravado pelo root; tenant não escolhe. Valores hardcoded em sync com profiles/ProfileType.java e frontend/lib/profiles/profile-type.ts (testes garantem paridade).';

-- Defensivo/idempotente: garante 'generic' para qualquer linha sem perfil (o DEFAULT do
-- ALTER já cobre as existentes; isto é clareza/segurança contra replay parcial).
update public.companies set profile_id = 'generic' where profile_id is null;
