import { createClient } from './client'

/**
 * Mensagem de uma conversa — shape do frontend. direction: inbound|outbound;
 * sender: contact|ai|human (CHECK do banco). content é imutável (messages é append-only).
 */
export type Message = {
  id: string
  direction: string
  sender: string
  content: string
  createdAt: string
}

/** Resultado paginado: as mensagens (até `limit`, mais recentes) + o total real. */
export type MessagesPage = {
  messages: Message[]
  total: number
}

/**
 * Últimas `limit` mensagens de uma conversa, em ordem CRONOLÓGICA (asc) para render de
 * chat. Estratégia: busca as N mais recentes (created_at desc + limit) e inverte para asc
 * no cliente — assim pegamos a CAUDA da conversa, não o começo. `total` vem de um count
 * exato separado (head:true) para o indicador "mostrando últimas N de M".
 *
 * <p>RLS de messages: só SELECT (company_id = app.company_id()); o tenant lê, não escreve
 * (Spring é o escritor único via service_role). Isolamento garantido pelo banco.
 */
export async function getConversationMessages(
  conversationId: string,
  limit = 50,
): Promise<MessagesPage> {
  const supabase = createClient()

  const { data, error } = await supabase
    .from('messages')
    .select('id, direction, sender, content, created_at')
    .eq('conversation_id', conversationId)
    .order('created_at', { ascending: false })
    .limit(limit)

  if (error) {
    throw error
  }

  const { count, error: countError } = await supabase
    .from('messages')
    .select('id', { count: 'exact', head: true })
    .eq('conversation_id', conversationId)

  if (countError) {
    throw countError
  }

  const messages = (data ?? [])
    .map((m) => ({
      id: m.id,
      direction: m.direction,
      sender: m.sender,
      content: m.content,
      createdAt: m.created_at,
    }))
    .reverse() // desc (cauda) → asc (cronológico) para o chat

  return { messages, total: count ?? messages.length }
}
