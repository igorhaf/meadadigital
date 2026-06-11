-- ============================================================================
-- seed-dev-alpha.sql — dado de DESENVOLVIMENTO da Empresa Alpha.
--
-- Popula a Alpha com conversas/mensagens sintéticas para smoke VISUAL e para
-- desenvolver telas que dependem de conversas (camada 4.6+). Diferente do seed de
-- smoke (descartável dentro de um curl): este FICA no banco e desbloqueia testes
-- visuais permanentes.
--
-- ⚠ DEV-ONLY. NUNCA rodar em produção. Conteúdo é fictício; números E.164 são da
--   faixa de teste (9999000x); nada de dado real.
--
-- IDEMPOTENTE: faz DELETE dos marcadores de dev antes do INSERT — pode ser rodado
-- N vezes sem duplicar. Marcadores: instance_name='alpha-dev-seed',
-- phones +5511999990001/02.
--
-- Uso:
--   PGPASSWORD=... psql "host=...pooler.supabase.com port=5432 dbname=postgres \
--     user=postgres.<ref> sslmode=require" -f scripts/seed-dev-alpha.sql
-- ============================================================================

DO $$
DECLARE
  v_alpha   uuid := '52e88a0b-8fa1-4774-b205-11349720c9b1';  -- Empresa Alpha
  v_inst    uuid := gen_random_uuid();
  v_c1      uuid := gen_random_uuid();
  v_c2      uuid := gen_random_uuid();
  v_conv1   uuid := gen_random_uuid();  -- handled_by = ai
  v_conv2   uuid := gen_random_uuid();  -- handled_by = human
  v_now     timestamptz := now();
BEGIN
  -- Limpeza idempotente dos marcadores de dev (ordem das FKs: messages →
  -- conversations → contacts → instance).
  delete from messages
    where company_id = v_alpha
      and conversation_id in (
        select id from conversations where whatsapp_instance_id in (
          select id from whatsapp_instances where instance_name = 'alpha-dev-seed'));
  delete from conversations
    where company_id = v_alpha
      and whatsapp_instance_id in (
        select id from whatsapp_instances where instance_name = 'alpha-dev-seed');
  delete from contacts
    where company_id = v_alpha
      and phone_number in ('+5511999990001', '+5511999990002');
  delete from whatsapp_instances where instance_name = 'alpha-dev-seed';

  -- Instância fictícia (FK composta exige id+company_id; instance_name é UNIQUE global).
  insert into whatsapp_instances (id, company_id, instance_name, evolution_token, status)
  values (v_inst, v_alpha, 'alpha-dev-seed', 'dev-fake-token', 'connected');

  -- Contatos.
  insert into contacts (id, company_id, phone_number, name) values
    (v_c1, v_alpha, '+5511999990001', 'Cliente Dev 1'),
    (v_c2, v_alpha, '+5511999990002', 'Cliente Dev 2');

  -- Conversas (last_message_at = timestamp da última msg de cada uma).
  insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by, last_message_at) values
    (v_conv1, v_alpha, v_c1, v_inst, 'open', 'ai',    v_now - interval '10 minutes'),
    (v_conv2, v_alpha, v_c2, v_inst, 'open', 'human', v_now - interval '2 minutes');

  -- Mensagens conv1 (ai) — 3, escalonadas.
  insert into messages (company_id, conversation_id, direction, sender, content, created_at) values
    (v_alpha, v_conv1, 'inbound',  'contact', 'Olá, queria saber os horários de atendimento.', v_now - interval '15 minutes'),
    (v_alpha, v_conv1, 'outbound', 'ai',      'Olá! Atendemos de segunda a sexta, das 9h às 18h.', v_now - interval '14 minutes'),
    (v_alpha, v_conv1, 'inbound',  'contact', 'Perfeito, obrigado!', v_now - interval '10 minutes');

  -- Mensagens conv2 (human) — 3, escalonadas.
  insert into messages (company_id, conversation_id, direction, sender, content, created_at) values
    (v_alpha, v_conv2, 'inbound',  'contact', 'Preciso de ajuda com um problema complexo.', v_now - interval '8 minutes'),
    (v_alpha, v_conv2, 'outbound', 'human',   'Claro, um atendente vai te ajudar agora.', v_now - interval '5 minutes'),
    (v_alpha, v_conv2, 'inbound',  'contact', 'Muito obrigado pela atenção.', v_now - interval '2 minutes');

  -- ---- business_hours (4.8): 7 linhas fixas da Alpha ----------------------
  -- DELETE por company_id (não por marcador): a Alpha é EMPRESA DE TESTE e seu
  -- único business_hours vem deste seed. ⚠ Este padrão (limpar por company_id)
  -- NÃO serve para empresas reais — só porque a Alpha é descartável de dev.
  delete from business_hours where company_id = v_alpha;
  insert into business_hours (company_id, weekday, opens_at, closes_at, closed) values
    (v_alpha, 0, null,    null,    true),   -- Domingo: fechado
    (v_alpha, 1, '09:00', '18:00', false),  -- Segunda
    (v_alpha, 2, '09:00', '18:00', false),  -- Terça
    (v_alpha, 3, '09:00', '18:00', false),  -- Quarta
    (v_alpha, 4, '09:00', '18:00', false),  -- Quinta
    (v_alpha, 5, '09:00', '18:00', false),  -- Sexta
    (v_alpha, 6, '09:00', '13:00', false);  -- Sábado

  -- ---- ai_settings (4.9): 1 linha 1:1 da Alpha ---------------------------
  -- DELETE por company_id (Alpha = empresa de teste; ver justificativa acima).
  -- model_provider NÃO especificado: usa o default 'gemini' do banco.
  delete from ai_settings where company_id = v_alpha;
  insert into ai_settings (company_id, tone, system_rules, restrictions, handoff_triggers) values
    (v_alpha,
     'Cordial e profissional. Trate o cliente por você.',
     'Sempre confirmar dados de agendamento. Nunca prometer prazo sem checar a agenda.',
     'Não enviar áudio. Não dar descontos sem confirmação humana.',
     'Cliente pede pessoa humana. Cliente expressa irritação. Reclamação formal ou jurídico.');
END $$;
