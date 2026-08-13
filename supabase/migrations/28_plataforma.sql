-- =============================================================================
-- 28_plataforma.sql
-- Meada — Camada 6 (sub-maratona 3: Plataforma). Migration única consolidada
-- das fases 6.7 (anúncios cross-tenant) e 6.8 (planos). Paletas (6.8) NÃO têm migration —
-- são catálogo hardcoded no frontend (lib/themes/palettes.ts), leitura pura.
--
-- TABELAS NOVAS:
--   announcements — avisos do super-admin para TODOS os tenants (info/warning/critical).
--     Fim de vida = expires_at (soft "delete" = expires_at=now(), preserva o histórico de
--     dismissals; sem coluna deleted_at).
--   announcement_dismissals — qual usuário dispensou qual anúncio (1 row por par, unique).
--   plans — catálogo de planos (CRUD do super-admin). companies.plan_id (integração
--     plano→empresa) é fase FUTURA — esta migration só cria a tabela isolada + seed.
--
-- RLS:
--   announcements — tenant SELECT só de publicados não-expirados (banner). super_admin via
--     service_role (gestão fora do RLS). announcement_dismissals — tenant SELECT/INSERT só
--     do próprio user_id (auth.uid()). plans — só-backend (sem policy authenticated;
--     grant service_role), o super-admin gerencia via Spring.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- announcements — avisos cross-tenant do super-admin (6.7)
-- ---------------------------------------------------------------------------
create table public.announcements (
  id           uuid        primary key default gen_random_uuid(),
  title        text        not null check (length(trim(title)) between 1 and 200),
  body         text        not null check (length(trim(body)) between 1 and 5000),
  severity     text        not null default 'info' check (severity in ('info','warning','critical')),
  published_at timestamptz not null default now(),
  expires_at   timestamptz,
  created_by   uuid,
  dismissable  boolean     not null default true
);

comment on table public.announcements is
  'Avisos do super-admin para todos os tenants (camada 6.7). Fim de vida = expires_at (soft delete = expires_at=now()). created_by = auth.users.id do super-admin (sem FK; super-admin não tem linha em public.users).';

create index idx_announcements_active on public.announcements (published_at desc);

alter table public.announcements enable row level security;
alter table public.announcements force  row level security;

-- Tenant (authenticated): SELECT só dos publicados não-expirados (o banner do AppShell).
create policy announcements_select_active on public.announcements
  for select to authenticated
  using (published_at <= now() and (expires_at is null or expires_at > now()));

grant select on public.announcements to authenticated;
grant all    on public.announcements to service_role;

-- ---------------------------------------------------------------------------
-- announcement_dismissals — quem dispensou o quê (6.7)
-- ---------------------------------------------------------------------------
create table public.announcement_dismissals (
  id              uuid        primary key default gen_random_uuid(),
  announcement_id uuid        not null references public.announcements(id) on delete cascade,
  user_id         uuid        not null,
  dismissed_at    timestamptz not null default now(),
  unique (announcement_id, user_id)
);

comment on table public.announcement_dismissals is
  'Dispensa de anúncio por usuário (camada 6.7). 1 row por par (announcement, user). user_id = auth.uid().';

create index idx_dismissals_user on public.announcement_dismissals (user_id);

alter table public.announcement_dismissals enable row level security;
alter table public.announcement_dismissals force  row level security;

-- Tenant: SELECT/INSERT só das PRÓPRIAS dispensas (user_id = auth.uid()).
create policy dismissals_select_own on public.announcement_dismissals
  for select to authenticated
  using (user_id = auth.uid());
create policy dismissals_insert_own on public.announcement_dismissals
  for insert to authenticated
  with check (user_id = auth.uid());

grant select, insert on public.announcement_dismissals to authenticated;
grant all           on public.announcement_dismissals to service_role;

-- ---------------------------------------------------------------------------
-- plans — catálogo de planos (6.8). Só-backend; super-admin gerencia via Spring.
-- ---------------------------------------------------------------------------
create table public.plans (
  id                       uuid        primary key default gen_random_uuid(),
  name                     text        not null unique,
  slug                     text        not null unique,
  monthly_price_cents      integer     not null default 0,
  max_admins               integer,
  max_faqs                 integer,
  max_conversations_month  integer,
  max_users                integer,
  features                 jsonb       not null default '{}'::jsonb,
  active                   boolean     not null default true,
  created_at               timestamptz not null default now(),
  updated_at               timestamptz not null default now()
);

comment on table public.plans is
  'Catálogo de planos (camada 6.8). CRUD do super-admin. Limites nullable = ilimitado. Soft delete = active=false. companies.plan_id (integração plano→empresa) é fase futura.';

alter table public.plans enable row level security;
alter table public.plans force  row level security;
grant all on public.plans to service_role;

-- Seed dos 4 planos default. enterprise: price 0 (custom) + limites null (ilimitado).
insert into public.plans (name, slug, monthly_price_cents, max_admins, max_faqs, max_conversations_month, max_users) values
  ('Free',       'free',       0,      1,    10,   100,   1),
  ('Starter',    'starter',    29900,  3,    50,   1000,  3),
  ('Pro',        'pro',        49900,  10,   200,  10000, 10),
  ('Enterprise', 'enterprise', 0,      null, null, null,  null);
