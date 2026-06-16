import { apiFetch } from './client'

/** Contato no resultado da busca global (#84). */
export type SearchContact = {
  id: string
  name: string | null
  phoneNumber: string
}

/** Conversa no resultado da busca global (#84). */
export type SearchConversation = {
  id: string
  contactName: string | null
  phoneNumber: string
}

/** Mensagem no resultado da busca global (#84). */
export type SearchMessage = {
  id: string
  conversationId: string
  content: string
}

/**
 * Resultado da busca global (GET /admin/search). Cada grupo traz no máximo 10 itens,
 * ordenados por similaridade (pg_trgm) no backend. q com menos de 2 chars → listas vazias.
 */
export type SearchResults = {
  contacts: SearchContact[]
  conversations: SearchConversation[]
  messages: SearchMessage[]
}

/** Busca global por {q} (contatos/conversas/mensagens da própria empresa). */
export async function globalSearch(q: string): Promise<SearchResults> {
  return apiFetch<SearchResults>('/admin/search?q=' + encodeURIComponent(q))
}
