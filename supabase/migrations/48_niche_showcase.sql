-- =============================================================================
-- 48_niche_showcase.sql
-- Meada WhatsApp — Vitrine de nichos (produtos do Meada institucional). INFRA DE PLATAFORMA.
-- O ROOT marca quais nichos são DESTAQUE (aparecem na home) e define a ORDEM (grid). A página
-- /produtos lista TODOS os nichos na ordem; a home mostra os marcados como destaque (até 6).
--
-- MODELO (cravado):
--   - Perfis permanecem HARDCODED (ProfileType). NÃO há tabela de perfis. Esta tabela guarda só
--     METADADO DE VITRINE por nicho (featured + ordem), não os nichos em si.
--   - profile_id é HARDCODED em ProfileType (sem FK/CHECK — validação app-level → 400 unknown_profile).
--   - Ausência de linha = não-destaque + ordem no fim. O resolver materializa a grade iterando
--     ProfileType e sobrepondo as linhas desta tabela (igual profile_features).
--   - O LIMITE de 6 destaques é regra de NEGÓCIO no service (não constraint — facilita reordenar).
--   - O conteúdo do card vem do próprio nicho (productName + paleta); esta tabela NÃO guarda
--     texto/imagem por nicho (o card é um template único do bloco niches_grid).
--
-- RLS: tabela de PLATAFORMA (espelha profile_features): enable+force + grant só service_role.
-- O público lê o RESOLVIDO via backend (GET /public/niches), nunca a tabela direto.
-- =============================================================================

create table public.niche_showcase (
  profile_id    text        not null,   -- companies.profile_id (HARDCODED em ProfileType; sem FK/CHECK)
  featured      boolean     not null default false,
  display_order integer     not null default 0,
  updated_at    timestamptz not null default now(),
  updated_by    uuid,                   -- super-admin que mexeu (sem FK: super-admin não tem linha em users)
  primary key (profile_id)
);

comment on table public.niche_showcase is
  'Vitrine de nichos (produtos do Meada). featured = aparece na home (até 6, regra no service); display_order = ordem no grid e na página /produtos. profile_id HARDCODED em ProfileType (sem FK/CHECK). Ausência de linha = não-destaque, ordem no fim. Tabela de PLATAFORMA: só service_role; público lê o resolvido via /public/niches.';

create index idx_niche_showcase_order on public.niche_showcase (display_order);

alter table public.niche_showcase enable row level security;
alter table public.niche_showcase force  row level security;

grant all on public.niche_showcase to service_role;
