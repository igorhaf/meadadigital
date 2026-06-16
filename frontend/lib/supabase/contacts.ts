import { createClient } from './client'

/**
 * Contato (cliente final) da empresa — shape do frontend, mapeado de public.contacts.
 * name é nullable (pode nunca ter vindo um pushName). blocked controla se a IA responde
 * (camada 5.11): contato bloqueado tem a inbound persistida mas não recebe resposta.
 */
export type Contact = {
  id: string
  phoneNumber: string
  name: string | null
  blocked: boolean
  createdAt: string
  // Canais do contato (#74 unificação multi-canal): jsonb agregando o identificador por canal
  // (ex.: { whatsapp: '+5511...', web: 'sess-abc' }). Opcional — só o detalhe (getContact) o
  // carrega; a lista e os mutators (rename/block) não, para não over-fetchar.
  channels?: Record<string, string> | null
}

/** Conversa resumida de um contato (para a lista no detalhe). */
export type ContactConversation = {
  id: string
  status: string
  handledBy: string
  lastMessageAt: string | null
  // Canal de origem da conversa (#74): 'whatsapp' (default) | 'web' | 'email'.
  channel: string
}

/**
 * Lista os contatos ATIVOS (não soft-deleted) da empresa do tenant, via SDK + RLS
 * (contacts_select: company_id = app.company_id()). Ordenado por created_at desc.
 */
export async function getMyContacts(): Promise<Contact[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('contacts')
    .select('id, phone_number, name, blocked, created_at')
    .is('deleted_at', null)
    .order('created_at', { ascending: false })

  if (error) {
    throw error
  }

  return (data ?? []).map((c) => ({
    id: c.id,
    phoneNumber: c.phone_number,
    name: c.name,
    blocked: c.blocked,
    createdAt: c.created_at,
    channels: null, // a lista não exibe canais — só o detalhe os carrega.
  }))
}

/** Um contato específico (detalhe). .single() — RLS garante 0 ou 1. */
export async function getContact(id: string): Promise<Contact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('contacts')
    .select('id, phone_number, name, blocked, created_at, channels')
    .eq('id', id)
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    phoneNumber: data.phone_number,
    name: data.name,
    blocked: data.blocked,
    createdAt: data.created_at,
    channels: (data.channels ?? null) as Record<string, string> | null,
  }
}

/** Conversas de um contato, mais recentes primeiro (lista no detalhe do contato). */
export async function getContactConversations(contactId: string): Promise<ContactConversation[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .select('id, status, handled_by, last_message_at, channel')
    .eq('contact_id', contactId)
    .order('last_message_at', { ascending: false, nullsFirst: false })

  if (error) {
    throw error
  }

  return (data ?? []).map((c) => ({
    id: c.id,
    status: c.status,
    handledBy: c.handled_by,
    lastMessageAt: c.last_message_at,
    channel: c.channel ?? 'whatsapp',
  }))
}

/**
 * Edita o nome de um contato (#40). UPDATE { name } via SDK + RLS (contacts_update).
 * Audita via trigger app.audit_trigger (fase-5.3 + trg_contacts_audit da 5.11).
 */
export async function updateContactName(id: string, name: string): Promise<Contact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('contacts')
    .update({ name })
    .eq('id', id)
    .select('id, phone_number, name, blocked, created_at')
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    phoneNumber: data.phone_number,
    name: data.name,
    blocked: data.blocked,
    createdAt: data.created_at,
  }
}

/**
 * Bloqueia/desbloqueia um contato (#41). UPDATE { blocked } via SDK + RLS. Quando
 * blocked=true, o WebhookService persiste a inbound mas NÃO dispara a IA — o contato
 * deixa de receber resposta automática. Audita via trigger.
 */
export async function setContactBlocked(id: string, blocked: boolean): Promise<Contact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('contacts')
    .update({ blocked })
    .eq('id', id)
    .select('id, phone_number, name, blocked, created_at')
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    phoneNumber: data.phone_number,
    name: data.name,
    blocked: data.blocked,
    createdAt: data.created_at,
  }
}
