import { apiFetch } from './client'

/**
 * Export LGPD de um contato (camada 5.24 #90) — shape do /admin/contacts/{id}/export. Objeto
 * estruturado com o contato e todos os dados dele (conversas, mensagens, agendamentos, tags).
 * Os sub-objetos são livres (a UI só baixa como arquivo), por isso unknown[].
 */
export type ContactExport = {
  contact: Record<string, unknown>
  conversations: unknown[]
  messages: unknown[]
  appointments: unknown[]
  tags: unknown[]
}

/**
 * Exporta todos os dados do contato (GET /admin/contacts/{id}/export). Tenant only — apiFetch
 * injeta o Bearer. Erros (404 contact_not_found) propagam como ApiError com .reason.
 */
export async function exportContactData(contactId: string): Promise<ContactExport> {
  return apiFetch<ContactExport>(`/admin/contacts/${contactId}/export`)
}

/**
 * Apaga DEFINITIVAMENTE os dados do contato (DELETE /admin/contacts/{id}/erase). 204 No
 * Content. Hard delete irreversível: o caller só chama após confirmação explícita do usuário.
 */
export async function eraseContact(contactId: string): Promise<void> {
  return apiFetch<void>(`/admin/contacts/${contactId}/erase`, { method: 'DELETE' })
}
