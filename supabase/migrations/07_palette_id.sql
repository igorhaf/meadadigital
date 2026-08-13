-- 07_palette_id.sql — Camada 5.0 (theming dinâmico por paleta).
--
-- Adiciona palette_id às empresas (companies) e aos usuários (users). É o identificador
-- (kebab-case) de uma das paletas versionadas em frontend/lib/themes/palettes.ts. O
-- frontend faz lookup do id no array; se não achar, cai para 'meada-default'.
--
-- Semântica por papel (decisão Opção A, camada 5.0):
--   - tenant-admin: a paleta efetiva vem de companies.palette_id (tema da empresa). A
--     coluna users.palette_id existe para preferência pessoal futura, mas a 5.0 lê a da
--     empresa no ThemeProvider do tenant.
--   - super-admin: não tem linha em public.users (vive na allowlist), então o backend
--     devolve 'meada-default' constante. A coluna users.palette_id NÃO é lida para ele.
--
-- NOT NULL DEFAULT 'meada-default': migração graciosa — toda linha pré-existente de
-- companies e users assume o verde-Meada padrão sem backfill manual. Novas linhas que
-- omitirem o campo idem.
--
-- text (não enum): o catálogo de paletas é versionado no frontend, não no banco. Um
-- ENUM no Postgres exigiria migration a cada paleta nova/removida e acoplaria o banco à
-- curadoria visual. text + validação na borda (frontend lookup + fallback) é o trade
-- certo aqui; o banco só persiste o identificador.

ALTER TABLE public.companies
  ADD COLUMN palette_id text NOT NULL DEFAULT 'meada-default';

ALTER TABLE public.users
  ADD COLUMN palette_id text NOT NULL DEFAULT 'meada-default';
