-- =============================================================================
-- 40_profile_features.sql
-- Meada WhatsApp — Camada 9.0 (SM-L: Feature Flags por Nicho). INFRA DE PLATAFORMA.
-- Tabela de plataforma (não-tenant) onde o ROOT (super-admin) liga/desliga features por nicho
-- (profile_id). A PRIMEIRA feature é 'cms' (página pessoal por tenant) — esta SM NÃO implementa o
-- CMS, só a infra de flags + o gate (requireFeature) pronto pra SM-M plugar.
--
-- MODELO (cravado):
--   - Perfis permanecem HARDCODED (ProfileType). NÃO há tabela de perfis.
--   - Feature é HARDCODED (ProfileFeature enum Java ↔ profile-feature.ts, parity test). Membro
--     inicial: CMS (id 'cms').
--   - profile_features guarda SÓ os DESVIOS do default. Ausência de linha = feature OFF. O resolver
--     (ProfileFeatureService) trata ausência como false — default de toda feature é OFF (opt-in
--     explícito do root: tem nicho que não tem site, então off-pra-todos é o estado natural).
--   - SEM CHECK de profile_id (evita acoplar a tabela a cada perfil futuro). A validação de
--     profile_id ∈ ProfileType é APP-LEVEL no controller (→ 400 unknown_profile).
--   - CHECK de feature_key no conjunto conhecido É ok (features são poucas; cada nova feature é
--     migration + enum + parity deliberados).
--
-- RLS: espelha as tabelas de PLATAFORMA (ex.: public.plans, migration 28): enable+force + grant
-- só a service_role. O TENANT NÃO lê esta tabela direto — recebe o resolvido via backend
-- (GET /admin/me.features). O ROOT opera via Spring/service_role (fora do RLS).
-- =============================================================================

create table public.profile_features (
  profile_id  text        not null,   -- companies.profile_id (HARDCODED em ProfileType; sem FK/CHECK)
  feature_key text        not null check (feature_key in ('cms')),
  enabled     boolean     not null default false,
  updated_at  timestamptz not null default now(),
  updated_by  uuid,                   -- super-admin que mexeu (sem FK: super-admin não tem linha em users)
  primary key (profile_id, feature_key)
);

comment on table public.profile_features is
  'Feature flags por nicho (camada 9.0). Guarda só os desvios do default. Ausência de linha = OFF (o resolver trata como false). profile_id é HARDCODED em ProfileType (sem FK/CHECK — validação app-level). feature_key é HARDCODED em ProfileFeature (CHECK no conjunto conhecido). Tabela de PLATAFORMA: só service_role (root opera fora do RLS); o tenant recebe o resolvido via /admin/me.';

alter table public.profile_features enable row level security;
alter table public.profile_features force  row level security;

grant all on public.profile_features to service_role;
