import { createClient } from './client'

/**
 * Conversa do tenant com o contato embutido (join PostgREST). O nome/telefone do contato
 * vivem em contacts; trazemos via select aninhado. RLS de conversations e contacts ambos
 * filtram por company_id = app.company_id() — o tenant só vê suas conversas/contatos.
 */
/**
 * Intenção de agendamento detectada pela IA (camada 5.15 #29) — shape do jsonb
 * conversations.scheduling_intent. null quando não detectado. serviceHint/whenHint são
 * livres (nullable); urgency é enum; rawExcerpt é o trecho que disparou a detecção;
 * detectedAt é ISO string (fato do servidor).
 */
export type SchedulingIntent = {
  detectedAt: string
  serviceHint: string | null
  whenHint: string | null
  urgency: 'low' | 'normal' | 'high'
  rawExcerpt: string
}

/**
 * Intent genérico detectado pela IA (camada 5.18) — shape dos jsonb
 * conversations.cancellation_intent (#51) e conversations.complaint_intent (#52). null
 * quando não detectado. detectedAt é ISO string (fato do servidor); summary é o resumo
 * curto; rawExcerpt é o trecho que disparou a detecção.
 */
export type DetectedIntent = {
  detectedAt: string
  summary: string
  rawExcerpt: string
}

export type ConversationWithContact = {
  id: string
  status: string
  handledBy: string
  markedUnread: boolean
  schedulingIntent: SchedulingIntent | null
  cancellationIntent: DetectedIntent | null
  complaintIntent: DetectedIntent | null
  extractedData: Record<string, unknown> | null
  lastMessageAt: string | null
  contactName: string | null
  contactPhone: string
}

const SELECT_WITH_CONTACT =
  'id, status, handled_by, marked_unread, scheduling_intent, cancellation_intent, '
  + 'complaint_intent, extracted_data, last_message_at, '
  + 'contact:contacts(name, phone_number)'

/** jsonb cru do scheduling_intent (snake_case, como o PostgREST devolve). */
type SchedulingIntentRow = {
  detected_at: string
  service_hint: string | null
  when_hint: string | null
  urgency: 'low' | 'normal' | 'high'
  raw_excerpt: string
}

/** Mapeia o jsonb cru (snake_case) para SchedulingIntent (camelCase). null → null. */
function toSchedulingIntent(raw: SchedulingIntentRow | null): SchedulingIntent | null {
  if (!raw) {
    return null
  }
  return {
    detectedAt: raw.detected_at,
    serviceHint: raw.service_hint ?? null,
    whenHint: raw.when_hint ?? null,
    urgency: raw.urgency,
    rawExcerpt: raw.raw_excerpt,
  }
}

/** jsonb cru do cancellation_intent / complaint_intent (snake_case, como o PostgREST devolve). */
type DetectedIntentRow = {
  detected_at: string
  summary: string
  raw_excerpt: string
}

/** Mapeia o jsonb cru (snake_case) para DetectedIntent (camelCase). null → null. */
function toDetectedIntent(raw: DetectedIntentRow | null): DetectedIntent | null {
  if (!raw) {
    return null
  }
  return {
    detectedAt: raw.detected_at,
    summary: raw.summary,
    rawExcerpt: raw.raw_excerpt,
  }
}

/** Linha crua de conversations vinda do PostgREST (snake_case + join possivelmente array). */
type ConversationRow = {
  id: string
  status: string
  handled_by: string
  marked_unread: boolean
  scheduling_intent: SchedulingIntentRow | null
  cancellation_intent: DetectedIntentRow | null
  complaint_intent: DetectedIntentRow | null
  extracted_data: Record<string, unknown> | null
  last_message_at: string | null
  contact:
    | { name: string | null; phone_number: string }
    | { name: string | null; phone_number: string }[]
    | null
}

/**
 * Normaliza a linha crua (snake_case + join possivelmente array) para
 * ConversationWithContact. Aceita unknown e casta internamente: o supabase-js não infere
 * o shape do select quando há jsonb (scheduling_intent) — o data tipa como
 * GenericStringError. O cast único aqui evita repeti-lo em cada call-site.
 */
function toConversation(raw: unknown): ConversationWithContact {
  const row = raw as ConversationRow
  const contact = Array.isArray(row.contact) ? row.contact[0] : row.contact
  return {
    id: row.id,
    status: row.status,
    handledBy: row.handled_by,
    markedUnread: row.marked_unread,
    schedulingIntent: toSchedulingIntent(row.scheduling_intent),
    cancellationIntent: toDetectedIntent(row.cancellation_intent),
    complaintIntent: toDetectedIntent(row.complaint_intent),
    extractedData: row.extracted_data ?? null,
    lastMessageAt: row.last_message_at,
    contactName: contact?.name ?? null,
    contactPhone: contact?.phone_number ?? '',
  }
}

/**
 * Lista as conversas da empresa do tenant (SDK + RLS), com o contato via join, ordenadas
 * por last_message_at desc (mais recente primeiro; nulls por último). Polling na tela.
 *
 * <p>Join PostgREST: contact:contacts(name, phone_number) — alias "contact" para o
 * objeto aninhado. O RLS de contacts também aplica (defesa em profundidade).
 */
export async function getMyConversations(): Promise<ConversationWithContact[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .select(SELECT_WITH_CONTACT)
    .order('last_message_at', { ascending: false, nullsFirst: false })

  if (error) {
    throw error
  }

  return (data ?? []).map(toConversation)
}

/**
 * Cabeçalho de uma conversa específica (com contato), via SDK + RLS. Usado na tela de
 * detalhe. .single(): RLS garante 0 ou 1 (id é PK; se de outro tenant, RLS → 0 → erro).
 */
export async function getConversation(id: string): Promise<ConversationWithContact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .select(SELECT_WITH_CONTACT)
    .eq('id', id)
    .single()

  if (error) {
    throw error
  }

  return toConversation(data)
}

/**
 * Troca quem atende a conversa (ai ↔ human) via SDK + RLS. A policy conversations_update
 * tem USING e WITH CHECK = company_id = app.company_id(): o tenant só atualiza conversa
 * da própria empresa, e o resultado precisa continuar dela. company_id NÃO é tocado aqui
 * (só handled_by) — o WITH CHECK passa naturalmente. Retorna a conversa atualizada.
 */
export async function updateConversationHandledBy(
  id: string,
  handledBy: 'ai' | 'human',
): Promise<ConversationWithContact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .update({ handled_by: handledBy })
    .eq('id', id)
    .select(SELECT_WITH_CONTACT)
    .single()

  if (error) {
    throw error
  }

  return toConversation(data)
}

/**
 * Abre/fecha a conversa (open ↔ closed) via SDK + RLS. Mesmo contrato de RLS do
 * updateConversationHandledBy. Retorna a conversa atualizada.
 */
export async function updateConversationStatus(
  id: string,
  status: 'open' | 'closed',
): Promise<ConversationWithContact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .update({ status })
    .eq('id', id)
    .select(SELECT_WITH_CONTACT)
    .single()

  if (error) {
    throw error
  }

  return toConversation(data)
}

/**
 * Marca/desmarca a conversa como não-lida manualmente (camada 5.14 #20). UPDATE
 * { marked_unread } via SDK + RLS (conversations_update: USING + WITH CHECK = company_id).
 * Override manual ortogonal a status/handled_by: o tenant sinaliza "preciso voltar aqui"
 * mesmo tendo sido o último a responder. Entra no badge do menu via a RPC
 * count_unread_conversations (ramo OR marked_unread, atualizada na migration 15).
 *
 * <p>Audita via trigger trg_conversations_audit (fase-5.3). Retorna a conversa atualizada.
 */
export async function setConversationMarkedUnread(
  id: string,
  markedUnread: boolean,
): Promise<ConversationWithContact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .update({ marked_unread: markedUnread })
    .eq('id', id)
    .select(SELECT_WITH_CONTACT)
    .single()

  if (error) {
    throw error
  }

  return toConversation(data)
}

/**
 * Marca a intenção de agendamento como tratada (camada 5.15 #29): UPDATE
 * { scheduling_intent: null } via SDK + RLS (conversations_update). O tenant clica
 * "Marcar como tratado" no detalhe quando já lidou com o pedido — a seção e o badge
 * somem. Idempotente (zerar uma coluna já null é no-op). Retorna a conversa atualizada.
 */
export async function clearSchedulingIntent(
  id: string,
): Promise<ConversationWithContact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .update({ scheduling_intent: null })
    .eq('id', id)
    .select(SELECT_WITH_CONTACT)
    .single()

  if (error) {
    throw error
  }

  return toConversation(data)
}

/**
 * Conta conversas com mensagem pendente de resposta (camada 5.10): conversas open cuja
 * ÚLTIMA mensagem é inbound (o contato falou por último). Via RPC, porque correlação de
 * "última msg por created_at" não é expressível em uma única .select() do PostgREST.
 *
 * RPC SECURITY INVOKER respeita o RLS do tenant — só conta as próprias conversas.
 * Consumida pelo badge do menu (ConversationsNavLink) com polling.
 */
export async function countUnreadConversations(): Promise<number> {
  const supabase = createClient()
  const { data, error } = await supabase.rpc('count_unread_conversations')
  if (error) {
    throw error
  }
  return typeof data === 'number' ? data : 0
}
