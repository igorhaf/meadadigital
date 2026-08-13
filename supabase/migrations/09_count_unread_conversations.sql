-- =============================================================================
-- 09_count_unread_conversations.sql
-- Meada — Camada 5.10: RPC para badge de "não-lido" no menu.
--
-- Conta conversas do tenant logado, abertas, cuja ÚLTIMA mensagem (por created_at)
-- é INBOUND — "o contato falou por último e ninguém respondeu ainda". Sinal real de
-- "precisa de atenção", diferente de "atividade recente".
--
-- SEM filtro por handled_by (decisão Opção 1, camada 5.10): conta independente de quem
-- atende. Conversa que o HUMANO assumiu e o contato voltou a falar É o caso onde o
-- tenant mais precisa do aviso. Quando a IA atende, ela responde sozinha (OutboundService)
-- e o estado some naturalmente — então filtrar por 'ai' contaria só um estado transitório
-- e ignoraria o caso humano, que é o mais importante para o badge.
--
-- SCHEMA public (NÃO app): esta função é chamada via PostgREST (supabase.rpc do frontend),
-- e o PostgREST só expõe funções do schema 'public' por padrão. Difere de app.company_id()
-- (que fica em app porque é chamada SQL→SQL dentro de policies/funções, nunca via API).
--
-- SECURITY INVOKER (default): roda com a sessão do caller. A query usa app.company_id()
-- (SECURITY DEFINER, lê auth.uid()→users) para filtrar por tenant; o RLS de
-- conversations/messages se aplica naturalmente.
--
-- Retorna INT (count(*)::int). count cabe em int32 com folga.
-- =============================================================================

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
    and exists (
      select 1 from messages m
      where m.conversation_id = c.id
        and m.created_at = (
          select max(m2.created_at) from messages m2 where m2.conversation_id = c.id
        )
        and m.direction = 'inbound'
    );
$$;

-- Grants: tenant (authenticated) chama via PostgREST. service_role acessa por default.
grant execute on function public.count_unread_conversations() to authenticated;
