-- =============================================================================
-- 15_conversations_marked_unread.sql
-- Meada — Camada 5.14 (#20): marcar conversa como não-lida manualmente.
--
-- Adiciona conversations.marked_unread: o tenant marca uma conversa como "preciso
-- voltar aqui" mesmo quando ele já foi o último a responder (a heurística automática
-- da 5.10 — última msg inbound — não cobriria esse caso). É um override manual,
-- ortogonal ao status open/closed e ao handled_by.
--
-- NOT NULL DEFAULT false: toda conversa existente nasce "lida" (graceful).
--
-- A coluna entra no badge do menu (count_unread_conversations, RPC da 5.10): a contagem
-- passa a ser conversas open com (última msg inbound) OU (marked_unread = true). Por isso
-- esta migration DROP+CREATE a função — ela vira WHOLE_FILE_SCRIPTS no teste de
-- integração (função com $$ + grant subsequente).
--
-- Sem trigger de audit novo: trg_conversations_audit (fase-5.3) já cobre UPDATE em
-- conversations, então marcar/desmarcar não-lida já é auditado automaticamente.
-- =============================================================================

alter table public.conversations
  add column marked_unread boolean not null default false;


-- -----------------------------------------------------------------------------
-- RPC count_unread_conversations — agora inclui OR marked_unread
--   Mantém SEM filtro por handled_by e SECURITY INVOKER (decisões da 5.10).
--   A condição passa a ser: open AND (última msg inbound OR marcada manualmente).
-- -----------------------------------------------------------------------------
create or replace function public.count_unread_conversations() returns int
language sql
stable
security invoker
set search_path = public, app
as $$
  select count(*)::int
  from conversations c
  where c.company_id = app.company_id()
    and c.status = 'open'
    and (
      c.marked_unread = true
      or exists (
        select 1 from messages m
        where m.conversation_id = c.id
          and m.created_at = (
            select max(m2.created_at) from messages m2 where m2.conversation_id = c.id
          )
          and m.direction = 'inbound'
      )
    );
$$;

grant execute on function public.count_unread_conversations() to authenticated;
