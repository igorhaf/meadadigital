-- =============================================================================
-- 05_storage.sql
-- Meada — bucket privado de documentos com isolamento por tenant.
--
-- Escopo deste arquivo:
--   - Cria o bucket privado 'documents'.
--   - Policies em storage.objects que espelham o RLS da tabela documents:
--     authenticated só acessa objetos cujo 1º segmento do path = seu company_id.
--   (Critério 6: upload/leitura sob prefixo de OUTRA empresa é negado.)
--
-- Convenção de path (contrato com a tabela documents e com o backend):
--   storage_path = '<company_id>/<qualquer-coisa>'
--   O 1º segmento é SEMPRE o company_id (uuid em texto). É isso que a policy lê.
--
-- Mecanismo: storage.foldername(name) devolve os segmentos do path como array
--   (1-indexed). (storage.foldername(name))[1] é o primeiro segmento. Comparamos
--   com app.company_id()::text (a função de 01 retorna uuid; o path é texto).
--
-- ⚠ OBRIGAÇÃO DO BACKEND — UUID LOWERCASE no path. A comparação acima é textual
--   e case-SENSITIVE. O Postgres emite uuid::text SEMPRE em lowercase
--   (ex.: 'a1b2...'). Se o Spring montar o storage_path com o UUID em UPPERCASE
--   (ex.: Java UUID.toString() já é lowercase, mas qualquer normalização manual
--   pode subverter), o 1º segmento NÃO casará com app.company_id()::text e a
--   policy NEGARÁ o acesso silenciosamente — ou, pior, o CHECK chk_documents_path_tenant
--   (em 02), que também usa company_id::text lowercase, rejeitará o INSERT.
--   REGRA: o Spring SEMPRE constrói o prefixo com o UUID em lowercase, idêntico
--   ao que company_id::text produz. Nunca uppercase, nunca recebido do cliente.
--
-- Por que bucket PRIVADO: bucket público serve binário por URL sem checar tenant
--   — fura o isolamento. Privado obriga signed URL (gerada pelo backend após
--   validar o tenant) ou acesso autenticado sujeito às policies abaixo.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Bucket privado 'documents' (idempotente).
-- -----------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('documents', 'documents', false)
on conflict (id) do nothing;


-- -----------------------------------------------------------------------------
-- 2. Policies em storage.objects, restritas ao bucket 'documents'.
-- -----------------------------------------------------------------------------
-- storage.objects já tem RLS ligado por padrão no Supabase. Adicionamos policies
-- específicas do nosso bucket. Cada uma checa DUAS coisas:
--   a) bucket_id = 'documents'  — não vaza para outros buckets.
--   b) 1º segmento do path = company_id do usuário — isolamento por tenant.

-- ---- leitura (download / listagem) ------------------------------------------
create policy documents_objects_select on storage.objects
  for select to authenticated
  using (
    bucket_id = 'documents'
    and (storage.foldername(name))[1] = app.company_id()::text
  );

-- ---- upload -----------------------------------------------------------------
-- WITH CHECK no INSERT impede subir arquivo sob o prefixo de outra empresa
-- (critério 6): um usuário de A tentando gravar em 'companyB/...' é barrado.
create policy documents_objects_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'documents'
    and (storage.foldername(name))[1] = app.company_id()::text
  );

-- ---- update (ex.: sobrescrever / mover dentro do próprio tenant) -------------
-- USING controla quais objetos pode tocar; WITH CHECK impede mover para fora do
-- próprio prefixo. Ambos amarrados ao company_id.
create policy documents_objects_update on storage.objects
  for update to authenticated
  using (
    bucket_id = 'documents'
    and (storage.foldername(name))[1] = app.company_id()::text
  )
  with check (
    bucket_id = 'documents'
    and (storage.foldername(name))[1] = app.company_id()::text
  );

-- ---- delete -----------------------------------------------------------------
-- Documento é config (a tabela documents tem soft delete); apagar o binário do
-- próprio tenant pelo painel é permitido. Só dentro do próprio prefixo.
create policy documents_objects_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'documents'
    and (storage.foldername(name))[1] = app.company_id()::text
  );


-- =============================================================================
-- NOTA de consistência metadados ↔ binário.
-- =============================================================================
-- A tabela documents (02/03) e o Storage (aqui) são isolados pelo MESMO critério
-- (company_id), mas são objetos independentes — não há FK entre eles. O backend é
-- responsável por mantê-los em sincronia:
--   - ao subir: grava o binário em '<company_id>/...' E cria a linha em documents
--     com o mesmo storage_path;
--   - ao remover (soft delete): marca documents.deleted_at; a remoção física do
--     binário (se desejada) é decisão de rotina de limpeza no backend.
-- Como ambos exigem o mesmo company_id, um usuário de A jamais casa metadado de A
-- com binário de B nem vice-versa.
--
-- PENDÊNCIA pós-MVP — teste E2E do isolamento de Storage. O Supabase instala um
--   trigger (storage.protect_delete/insert) que PROÍBE INSERT/DELETE direto em
--   storage.objects via SQL, exigindo a Storage API. Logo, o isolamento por
--   prefixo destas policies NÃO é testável por SQL puro (o erro viria do trigger,
--   não da policy). O teste fiel — upload sob prefixo de outra empresa deve ser
--   negado — fica para um teste de integração via Storage API REST com JWT real,
--   quando o Spring estiver gerando signed URLs. Esse teste exercita o caminho
--   que produção usa. A validação possível por SQL é estática (pg_policies):
--   confirmar que as 4 policies existem e carregam a expressão de prefixo correta.
