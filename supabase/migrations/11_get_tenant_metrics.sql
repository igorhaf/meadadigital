-- =============================================================================
-- 11_get_tenant_metrics.sql
-- Meada — Camada 5.12: RPC do dashboard de métricas do tenant.
--
-- Uma única função retorna TODO o payload do dashboard em jsonb (1 round-trip):
--   - messagesInbound30d / messagesOutbound30d — contagens dos últimos 30 dias
--   - conversationsStarted30d / contactsNew30d  — criados nos últimos 30 dias
--   - messagesByDay  — série diária (30 dias, dias sem msg = zeros) p/ o gráfico
--   - topFaqs        — FAQs ativas em ordem cronológica (ranking por uso é fase futura)
--   - avgResponseSeconds — tempo médio da IA: delta inbound → próxima outbound da IA
--
-- SCHEMA public (não app): chamada via PostgREST (supabase.rpc); o PostgREST só expõe
-- 'public'. SECURITY INVOKER: usa app.company_id() para filtrar por tenant, RLS aplica.
-- =============================================================================

create or replace function public.get_tenant_metrics() returns jsonb
language sql
stable
security invoker
set search_path = public, app
as $$
  select jsonb_build_object(
    'messagesInbound30d', (
      select count(*)::int from messages
      where company_id = app.company_id() and direction = 'inbound'
        and created_at >= now() - interval '30 days'),
    'messagesOutbound30d', (
      select count(*)::int from messages
      where company_id = app.company_id() and direction = 'outbound'
        and created_at >= now() - interval '30 days'),
    'conversationsStarted30d', (
      select count(*)::int from conversations
      where company_id = app.company_id()
        and created_at >= now() - interval '30 days'),
    'contactsNew30d', (
      select count(*)::int from contacts
      where company_id = app.company_id() and deleted_at is null
        and created_at >= now() - interval '30 days'),
    'messagesByDay', (
      -- 30 dias fixos (gen_series), LEFT JOIN com as contagens diárias do tenant.
      -- Dias sem mensagem aparecem com inbound=0/outbound=0 (coalesce).
      select coalesce(jsonb_agg(jsonb_build_object(
        'day', to_char(d.day, 'YYYY-MM-DD'),
        'inbound', coalesce(m.inbound, 0),
        'outbound', coalesce(m.outbound, 0)
      ) order by d.day), '[]'::jsonb)
      from generate_series(
        date_trunc('day', now() - interval '29 days'),
        date_trunc('day', now()),
        interval '1 day') as d(day)
      left join (
        select date_trunc('day', created_at) as day,
               count(*) filter (where direction = 'inbound')::int  as inbound,
               count(*) filter (where direction = 'outbound')::int as outbound
        from messages
        where company_id = app.company_id()
          and created_at >= now() - interval '30 days'
        group by 1
      ) m on m.day = d.day),
    'topFaqs', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'id', f.id, 'question', f.question,
        'createdAt', to_char(f.created_at, 'YYYY-MM-DD"T"HH24:MI:SSOF')
      ) order by f.created_at desc), '[]'::jsonb)
      from (
        select id, question, created_at from faqs
        where company_id = app.company_id() and active and deleted_at is null
        order by created_at desc limit 10
      ) f),
    'avgResponseSeconds', (
      -- Para cada inbound (últimos 30d), a PRIMEIRA outbound da IA depois dela na
      -- mesma conversa (LATERAL). Média dos deltas em segundos. NULL se não há pares.
      select round(avg(extract(epoch from (o.created_at - i.created_at))))::int
      from messages i
      cross join lateral (
        select created_at from messages o
        where o.conversation_id = i.conversation_id
          and o.direction = 'outbound' and o.sender = 'ai'
          and o.created_at > i.created_at
        order by o.created_at asc limit 1
      ) o
      where i.company_id = app.company_id() and i.direction = 'inbound'
        and i.created_at >= now() - interval '30 days')
  );
$$;

grant execute on function public.get_tenant_metrics() to authenticated;
