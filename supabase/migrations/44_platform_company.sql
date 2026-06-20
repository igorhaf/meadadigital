-- =============================================================================
-- 44_platform_company.sql
-- Meada WhatsApp — Camada 9.x (CMS do ROOT). Cria a COMPANY-ÂNCORA canônica que representa a
-- própria plataforma ("Meada"), para que o site institucional (servido no domínio-base) seja
-- editável pelo super-admin no painel root via o MESMO CMS multi-página dos tenants.
--
-- DECISÃO (caminho B1): o root (super-admin da allowlist) NÃO tem company. O CMS é modelado por
-- company (cms_sites.company_id NOT NULL). Em vez de duplicar o CMS para um caso só, ancoramos o
-- site do Meada numa company-âncora ÚNICA marcada com is_platform=true. O super-admin, ao abrir o
-- CMS no painel, opera sobre essa âncora (resolução no CmsTenantController). O CMS é EMBUTIDO pro
-- root — sempre ligado, sem passar pela grade de feature flags (coerente com a decisão da SM-L de o
-- generic ficar fora da grade).
--
-- A âncora tem profile_id='generic' (o produto do próprio admin). NÃO é um tenant-cliente: não há
-- usuário em public.users apontando pra ela, e ela não aparece na lista de empresas como tenant
-- normal (continua sendo uma company, mas marcada is_platform).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.is_platform — marca a company-âncora da plataforma. Default false (todo tenant normal).
-- ---------------------------------------------------------------------------
alter table public.companies add column if not exists is_platform boolean not null default false;

comment on column public.companies.is_platform is
  'TRUE só na company-âncora da plataforma (o "Meada" do root). O CMS dela é o site institucional, editável pelo super-admin no painel; CMS embutido (sempre on, fora da grade de feature flags). Único (índice parcial).';

-- Garante UMA só company de plataforma.
create unique index if not exists uniq_platform_company on public.companies (is_platform)
  where is_platform = true;

-- ---------------------------------------------------------------------------
-- A company-âncora canônica. id e slug fixos e bem-conhecidos (o backend resolve por is_platform,
-- mas o id estável facilita o seed/operação). profile_id='generic'.
-- ---------------------------------------------------------------------------
insert into public.companies (id, name, slug, profile_id, status, is_platform)
values ('00000000-0000-0000-0000-000000000000', 'Meada', 'meada', 'generic', 'active', true)
on conflict (id) do update set is_platform = true;
