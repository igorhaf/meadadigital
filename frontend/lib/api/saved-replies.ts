import { apiFetch } from './client'

/**
 * Resposta pronta da empresa (camada 5.22 #88) — shape do /admin/saved-replies. Texto
 * reutilizável que o atendente copia/insere numa conversa. Sem variáveis dinâmicas nesta fase.
 */
export type SavedReply = {
  id: string
  title: string
  body: string
  createdAt: string
}

/** Lista as respostas prontas da empresa do admin. */
export async function getMySavedReplies(): Promise<SavedReply[]> {
  return apiFetch<SavedReply[]>('/admin/saved-replies')
}

/** Cria uma resposta pronta {title, body}. Retorna a linha criada (201). */
export async function createSavedReply(title: string, body: string): Promise<SavedReply> {
  return apiFetch<SavedReply>('/admin/saved-replies', {
    method: 'POST',
    body: JSON.stringify({ title, body }),
  })
}

/** Atualiza uma resposta pronta {title, body}. 204 No Content. */
export async function updateSavedReply(id: string, title: string, body: string): Promise<void> {
  return apiFetch<void>(`/admin/saved-replies/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ title, body }),
  })
}

/** Remove uma resposta pronta. 204 No Content. */
export async function deleteSavedReply(id: string): Promise<void> {
  return apiFetch<void>(`/admin/saved-replies/${id}`, { method: 'DELETE' })
}
