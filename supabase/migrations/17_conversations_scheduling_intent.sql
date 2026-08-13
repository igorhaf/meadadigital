-- =============================================================================
-- 17_conversations_scheduling_intent.sql
-- Meada — Camada 5.15 (#29): IA reconhece intent de agendamento.
--
-- Adiciona conversations.scheduling_intent jsonb (nullable, sem default):
--   - null  = nenhuma intenção de agendar detectada (estado normal da maioria).
--   - jsonb = a IA detectou intenção; o objeto carrega os detalhes extraídos pelo
--     modelo (+ detected_at, fato do servidor):
--       { detected_at, service_hint, when_hint, urgency: low|normal|high, raw_excerpt }
--
-- A IA NÃO interrompe o atendimento ao detectar — só MARCA a conversa (preenche esta
-- coluna). O reply normal segue (decisão de produto). O tenant vê o badge no painel e
-- decide quando intervir. "Marcar como tratado" no painel zera a coluna (UPDATE = null).
--
-- Index PARCIAL por company_id só nas conversas COM intent: a esmagadora maioria das
-- conversas tem scheduling_intent null; indexar só as detectadas mantém o índice
-- minúsculo e acelera o filtro do painel ("conversas com agendamento pendente").
--
-- SEM trigger novo: trg_conversations_audit (fase-5.3) já cobre UPDATE em conversations,
-- então marcar/limpar a intent já é auditado. Sem CHECK no shape do jsonb: o backend
-- (service_role) é o único escritor da intent (via OutboundService) e valida a estrutura
-- no Java; o painel só lê e zera.
-- =============================================================================

alter table public.conversations
  add column scheduling_intent jsonb;

create index idx_conversations_has_intent
  on public.conversations (company_id)
  where scheduling_intent is not null;
