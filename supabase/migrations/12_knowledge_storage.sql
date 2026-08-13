-- =============================================================================
-- 12_knowledge_storage.sql
-- Meada — Camada 5.13.b: bucket privado de documentos de conhecimento (RAG).
--
-- Espelha o padrão do 05_storage.sql: bucket privado + policies em storage.objects
-- que isolam por tenant pelo 1º segmento do path (= company_id em lowercase).
--
-- Convenção de path (contrato com knowledge_documents e com o backend):
--   storage_path = '<company_id>/<document_id>.pdf'   (UUIDs lowercase)
--
-- Upload é feito pelo BACKEND via service_role (trusted) — não passa por estas policies.
-- As policies cobrem leitura/SDK do tenant (defesa em profundidade): authenticated só
-- acessa objetos sob o prefixo da própria empresa.
-- =============================================================================

insert into storage.buckets (id, name, public)
values ('tenant-knowledge', 'tenant-knowledge', false)
on conflict (id) do nothing;

create policy knowledge_objects_select on storage.objects
  for select to authenticated
  using (
    bucket_id = 'tenant-knowledge'
    and (storage.foldername(name))[1] = app.company_id()::text
  );

create policy knowledge_objects_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'tenant-knowledge'
    and (storage.foldername(name))[1] = app.company_id()::text
  );

create policy knowledge_objects_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'tenant-knowledge'
    and (storage.foldername(name))[1] = app.company_id()::text
  );
