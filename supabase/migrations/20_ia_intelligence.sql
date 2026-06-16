-- =============================================================================
-- 20_ia_intelligence.sql
-- Meada WhatsApp — Camada 5.18 (Fase B): IA mais inteligente.
--
-- Mesma técnica da 5.15 (#29): sub-objetos OPCIONAIS no responseSchema do Gemini, sem
-- function calling (incompatível com JSON-mode). O backend persiste cada sub-objeto na
-- coluna correspondente quando o modelo o preenche.
--
-- #51 cancellation_intent jsonb em conversations — cliente quer cancelar algo.
-- #52 complaint_intent jsonb em conversations — reclamação; o backend FORÇA handoff
--     (handled_by='human') quando detectada.
-- #53 extracted_data jsonb em conversations — dados estruturados que a IA coletou ao
--     longo da conversa (nome, email, cpf, endereço... livre).
-- #55 contact_memory jsonb em contacts — memória de longo prazo do contato (preferências,
--     fatos persistentes). O PromptBuilder injeta no contexto da IA; a IA pode atualizar
--     via sub-objeto memory_update na resposta.
-- #58 detected_tone text em contacts — tom detectado do contato (formal|informal|neutro|
--     irritado) na primeira interação; o PromptBuilder ajusta o system_prompt.
--
-- Sem CHECK no shape dos jsonb: o backend é o único escritor (valida no Java). detected_at
-- de cada intent é fato do servidor. Audit: triggers de conversations/contacts já cobrem.
-- =============================================================================

alter table public.conversations
  add column cancellation_intent jsonb,
  add column complaint_intent    jsonb,
  add column extracted_data      jsonb;

alter table public.contacts
  add column contact_memory jsonb,
  add column detected_tone  text check (detected_tone in
    ('formal', 'informal', 'neutro', 'irritado'));

-- Index parcial p/ o painel filtrar conversas com reclamação pendente.
create index idx_conversations_complaint on public.conversations (company_id)
  where complaint_intent is not null;
