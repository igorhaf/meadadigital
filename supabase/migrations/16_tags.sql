-- =============================================================================
-- 16_tags.sql
-- Meada — Camada 5.14 (#22): tags/etiquetas livres em conversas.
--
-- Duas tabelas:
--   1. tags — etiquetas que o tenant cria (nome livre + cor de uma paleta fixa de 8).
--      Isolada por company_id via RLS (espelha services/faqs). Soft delete (deleted_at).
--   2. conversation_tags — junção N:N entre conversations e tags (uma tag pode estar em
--      várias conversas; uma conversa pode ter várias tags). PK composta. RLS via o
--      company_id da CONVERSA (o vínculo herda o isolamento da conversa).
--
-- Cores: enumeradas via CHECK (slate/red/orange/amber/green/blue/violet/pink) — 8 cores
-- predefinidas, sem hex livre. O frontend mostra a paleta visual; o banco valida.
--
-- Sem limite de tags por empresa nem por conversa (decisão de produto da 5.14).
--
-- Audit: tags entra no trigger genérico app.audit_trigger() (fase-5.3) — criar/editar/
-- desativar tag é ação do tenant que vale auditar. conversation_tags NÃO é auditada
-- (vínculo volátil, alto volume, baixo valor forense — espelha a decisão de não auditar
-- messages).
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Tabela tags
-- -----------------------------------------------------------------------------
create table public.tags (
  id         uuid        primary key default gen_random_uuid(),
  company_id uuid        not null references public.companies(id) on delete cascade,
  name       text        not null check (length(trim(name)) between 1 and 30),
  color      text        not null check (color in
               ('slate','red','orange','amber','green','blue','violet','pink')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

comment on table public.tags is
  'Etiquetas livres do tenant (camada 5.14 #22). Nome livre (1..30 chars), cor de paleta fixa de 8. Isolada por company_id (RLS). Soft delete.';

create index idx_tags_company on public.tags (company_id) where deleted_at is null;


-- -----------------------------------------------------------------------------
-- Tabela conversation_tags (junção N:N)
--   PK composta (conversation_id, tag_id) — impede vínculo duplicado.
--   ON DELETE CASCADE em ambas as FKs: apagar conversa ou tag limpa o vínculo.
-- -----------------------------------------------------------------------------
create table public.conversation_tags (
  conversation_id uuid        not null references public.conversations(id) on delete cascade,
  tag_id          uuid        not null references public.tags(id)          on delete cascade,
  created_at      timestamptz not null default now(),
  primary key (conversation_id, tag_id)
);

comment on table public.conversation_tags is
  'Vínculo N:N entre conversas e tags (camada 5.14 #22). RLS via company_id da conversa. CASCADE em ambas FKs.';

create index idx_conversation_tags_tag on public.conversation_tags (tag_id);


-- -----------------------------------------------------------------------------
-- RLS tags — isolamento por company_id (espelha services/faqs)
-- -----------------------------------------------------------------------------
alter table public.tags enable row level security;
alter table public.tags force  row level security;

create policy tags_select on public.tags
  for select to authenticated
  using (company_id = app.company_id());

create policy tags_insert on public.tags
  for insert to authenticated
  with check (company_id = app.company_id());

create policy tags_update on public.tags
  for update to authenticated
  using (company_id = app.company_id())
  with check (company_id = app.company_id());

-- Sem policy de DELETE: a UI faz soft delete (UPDATE deleted_at), não DELETE físico —
-- espelha services/faqs. O DELETE físico de tags só via service_role.


-- -----------------------------------------------------------------------------
-- RLS conversation_tags — isolamento herdado da conversa
--   O vínculo é visível/editável se a CONVERSA pertence ao tenant. Checamos via EXISTS
--   na conversations (cujo próprio RLS + o company_id = app.company_id() confirmam posse).
--   Tanto o lado da conversa quanto o da tag são do mesmo tenant (ambas RLS-isoladas),
--   então validar a conversa basta para o isolamento.
-- -----------------------------------------------------------------------------
alter table public.conversation_tags enable row level security;
alter table public.conversation_tags force  row level security;

create policy conversation_tags_select on public.conversation_tags
  for select to authenticated
  using (exists (
    select 1 from public.conversations c
    where c.id = conversation_tags.conversation_id
      and c.company_id = app.company_id()
  ));

create policy conversation_tags_insert on public.conversation_tags
  for insert to authenticated
  with check (exists (
    select 1 from public.conversations c
    where c.id = conversation_tags.conversation_id
      and c.company_id = app.company_id()
  ));

create policy conversation_tags_delete on public.conversation_tags
  for delete to authenticated
  using (exists (
    select 1 from public.conversations c
    where c.id = conversation_tags.conversation_id
      and c.company_id = app.company_id()
  ));


-- -----------------------------------------------------------------------------
-- Grants — leitura+escrita por tenant; acesso total ao backend
-- -----------------------------------------------------------------------------
grant select, insert, update on public.tags to authenticated;
grant all on public.tags to service_role;

grant select, insert, delete on public.conversation_tags to authenticated;
grant all on public.conversation_tags to service_role;


-- -----------------------------------------------------------------------------
-- Audit — tags entra no trigger genérico (fase-5.3)
--   conversation_tags fica de fora (vínculo volátil, espelha messages).
-- -----------------------------------------------------------------------------
create trigger trg_tags_audit after insert or update on public.tags
  for each row execute function app.audit_trigger();
