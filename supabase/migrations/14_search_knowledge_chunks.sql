-- =============================================================================
-- 14_search_knowledge_chunks.sql
-- Meada — Camada 5.13.b: RPC de retrieval (busca semântica de chunks).
--
-- Dado o embedding da consulta do usuário, retorna os top-N chunks mais similares do
-- tenant (cosine), acima de um threshold. Usado pelo backend no retrieval (5.13.d) —
-- mas o backend pode chamá-la via JdbcTemplate (service_role) OU um tenant via PostgREST;
-- por isso fica em public + SECURITY INVOKER (app.company_id() filtra o tenant).
--
-- Distância <=> é cosine distance (0 = idêntico, 2 = oposto). similarity = 1 - distância.
-- Só chunks de documentos active e não-deletados entram (a IA não deve usar documento
-- desligado/removido). Embeddings normalizados → cosine bem-comportado.
-- =============================================================================

create or replace function public.search_knowledge_chunks(
  query_embedding vector(384),
  match_threshold float default 0.65,
  match_count     int   default 5
) returns table (
  chunk_id       uuid,
  document_id    uuid,
  document_title text,
  chunk_index    int,
  content        text,
  similarity     float
)
language sql
stable
security invoker
set search_path = public, app
as $$
  select
    c.id,
    c.document_id,
    d.title,
    c.chunk_index,
    c.content,
    (1 - (c.embedding <=> query_embedding))::float as similarity
  from knowledge_chunks c
  join knowledge_documents d on d.id = c.document_id
  where c.company_id = app.company_id()
    and d.active and d.deleted_at is null
    and (1 - (c.embedding <=> query_embedding)) >= match_threshold
  order by c.embedding <=> query_embedding
  limit match_count;
$$;

grant execute on function public.search_knowledge_chunks(vector, float, int) to authenticated;
