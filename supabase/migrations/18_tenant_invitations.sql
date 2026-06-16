-- =============================================================================
-- 18_tenant_invitations.sql
-- Meada WhatsApp — Camada 5.16 (#6): convite de usuário extra (multi-user no tenant).
--
-- O admin de um tenant convida outra pessoa para administrar a MESMA empresa. Fluxo
-- (decisão de produto cravada): link manual gerado pelo backend (sem SMTP) — o admin
-- gera, copia e envia pelo canal dele. O convidado cria conta no Supabase Auth e aceita;
-- o accept INSERE a linha em public.users (role='admin') vinculada à empresa do convite.
--
-- Tabela tenant_invitations:
--   - token: segredo aleatório (SecureRandom base64-url no backend), UNIQUE. É o que viaja
--     na URL /invite/{token}. Busca por token no accept.
--   - email: para quem o convite foi emitido. O accept compara com o email do JWT do
--     convidado (case-insensitive) — impede que um link vaze e seja aceito por outra conta.
--   - invited_by / used_by: auditoria de quem convidou e quem aceitou (FK auth.users,
--     ON DELETE SET NULL — apagar a conta não apaga o histórico do convite).
--   - expires_at: validade (7 dias, calculado no backend). used_at: quando foi aceito
--     (null = ainda ativo). Cancelar = expirar imediatamente (expires_at = now()).
--
-- RLS por company_id (espelha services/faqs/tags): o admin só vê/cria/cancela convites da
-- PRÓPRIA empresa. O accept NÃO passa por estas policies — roda no backend via
-- service_role (o convidado ainda não tem company_id resolvido, app.company_id() seria
-- null). O endpoint público de leitura do convite (mostrar "você foi convidado pela X")
-- também é service_role no backend.
--
-- Audit: trg_tenant_invitations_audit (trigger genérico fase-5.3) — criar/cancelar/aceitar
-- convite é ação sensível (concede acesso ao painel). new.company_id existe → o trigger
-- funciona.
-- =============================================================================

create table public.tenant_invitations (
  id         uuid        primary key default gen_random_uuid(),
  company_id uuid        not null references public.companies(id) on delete cascade,
  email      text        not null check (email ~ '^[^@]+@[^@]+\.[^@]+$'),
  token      text        not null unique,
  invited_by uuid        references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  used_at    timestamptz,
  used_by    uuid        references auth.users(id) on delete set null
);

comment on table public.tenant_invitations is
  'Convites de admin extra por empresa (camada 5.16 #6). token na URL /invite/{token}; accept cria linha em public.users role=admin. RLS por company_id; accept via service_role.';

-- Index para a lista do painel (convites ativos da empresa).
create index idx_tenant_invitations_company
  on public.tenant_invitations (company_id) where used_at is null;

-- Index explícito por token para a busca do accept/lookup público (além do UNIQUE).
create index idx_tenant_invitations_token on public.tenant_invitations (token);


-- -----------------------------------------------------------------------------
-- RLS — isolamento por company_id (espelha services/faqs/tags)
-- -----------------------------------------------------------------------------
alter table public.tenant_invitations enable row level security;
alter table public.tenant_invitations force  row level security;

create policy tenant_invitations_select on public.tenant_invitations
  for select to authenticated
  using (company_id = app.company_id());

-- INSERT: além de company_id próprio, exige invited_by = auth.uid() (o admin não pode
-- forjar convite "em nome de" outro). O backend (controller admin) usa service_role, mas
-- a policy cobre o caso de escrita via SDK direto (defesa em profundidade).
create policy tenant_invitations_insert on public.tenant_invitations
  for insert to authenticated
  with check (company_id = app.company_id() and invited_by = auth.uid());

-- UPDATE: cancelar (set expires_at = now()). Só da própria empresa.
create policy tenant_invitations_update on public.tenant_invitations
  for update to authenticated
  using (company_id = app.company_id())
  with check (company_id = app.company_id());

-- Sem policy de DELETE para authenticated: cancelamento é via UPDATE (expirar), não DELETE.


-- -----------------------------------------------------------------------------
-- Grants — leitura/escrita por tenant; acesso total ao backend (accept via service_role)
-- -----------------------------------------------------------------------------
grant select, insert, update on public.tenant_invitations to authenticated;
grant all on public.tenant_invitations to service_role;


-- -----------------------------------------------------------------------------
-- Audit — entra no trigger genérico (fase-5.3)
-- -----------------------------------------------------------------------------
create trigger trg_tenant_invitations_audit after insert or update on public.tenant_invitations
  for each row execute function app.audit_trigger();
