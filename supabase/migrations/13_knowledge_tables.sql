-- =============================================================================
-- 13_knowledge_tables.sql
-- Meada — Camada 5.13.b: tabelas do RAG (documentos + chunks com embeddings).
--
-- pgvector: a extensão 'vector' é habilitada aqui (idempotente). knowledge_chunks.embedding
-- é vector(384) — dimensão do modelo intfloat/multilingual-e5-small (sidecar 5.13.a).
--
-- knowledge_documents: metadados de um PDF enviado pelo tenant. status acompanha o
--   processamento síncrono (processing → ready | failed). storage_path aponta pro objeto
--   no bucket tenant-knowledge ('<company_id>/<document_id>.pdf').
-- knowledge_chunks: os pedaços de texto extraídos + o embedding de cada um. FK ON DELETE
--   CASCADE: apagar o documento apaga os chunks. company_id redundante (vem do documento)
--   para o RLS e a RPC filtrarem direto sem JOIN.
-- =============================================================================

create extension if not exists vector;


-- -----------------------------------------------------------------------------
-- knowledge_documents
-- -----------------------------------------------------------------------------
create table public.knowledge_documents (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  title         text        not null,
  storage_path  text        not null,
  status        text        not null default 'processing'
                  check (status in ('processing', 'ready', 'failed')),
  error_message text,
  char_count    int         not null default 0,
  chunk_count   int         not null default 0,
  active        boolean     not null default true,
  deleted_at    timestamptz,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create index idx_knowledge_documents_company on public.knowledge_documents (company_id)
  where deleted_at is null;


-- -----------------------------------------------------------------------------
-- knowledge_chunks
-- -----------------------------------------------------------------------------
create table public.knowledge_chunks (
  id          uuid        primary key default gen_random_uuid(),
  document_id uuid        not null references public.knowledge_documents(id) on delete cascade,
  company_id  uuid        not null references public.companies(id) on delete restrict,
  chunk_index int         not null,
  content     text        not null,
  embedding   vector(384) not null,
  created_at  timestamptz not null default now()
);

-- ivfflat para busca aproximada por cosine. lists=100 é adequado para volumes de MVP;
-- ANALYZE após carga melhora o planner. cosine_ops casa com embeddings normalizados.
create index idx_knowledge_chunks_embedding on public.knowledge_chunks
  using ivfflat (embedding vector_cosine_ops) with (lists = 100);

create index idx_knowledge_chunks_document on public.knowledge_chunks (document_id);


-- -----------------------------------------------------------------------------
-- RLS — tenant lê/escreve só os próprios documentos/chunks. Escrita real é via
--   backend (service_role, BYPASSRLS); as policies cobrem leitura via SDK.
-- -----------------------------------------------------------------------------
alter table public.knowledge_documents enable row level security;
alter table public.knowledge_documents force  row level security;
alter table public.knowledge_chunks    enable row level security;
alter table public.knowledge_chunks    force  row level security;

create policy knowledge_documents_select on public.knowledge_documents
  for select to authenticated using (company_id = app.company_id());
create policy knowledge_documents_update on public.knowledge_documents
  for update to authenticated
  using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy knowledge_documents_delete on public.knowledge_documents
  for delete to authenticated using (company_id = app.company_id());

create policy knowledge_chunks_select on public.knowledge_chunks
  for select to authenticated using (company_id = app.company_id());


-- -----------------------------------------------------------------------------
-- Grants (espelham o RLS). service_role tem acesso total (backend escreve).
-- -----------------------------------------------------------------------------
grant select, update, delete on public.knowledge_documents to authenticated;
grant select                 on public.knowledge_chunks    to authenticated;
grant all on public.knowledge_documents to service_role;
grant all on public.knowledge_chunks    to service_role;


-- -----------------------------------------------------------------------------
-- Audit: edição/criação de documento de conhecimento é ação sensível (afeta o que
--   a IA sabe). trg via app.audit_trigger genérico (fase-5.3). Só em documents
--   (chunks são derivados — auditar cada chunk poluiria o log).
-- -----------------------------------------------------------------------------
create trigger trg_knowledge_documents_audit
  after insert or update on public.knowledge_documents
  for each row execute function app.audit_trigger();
