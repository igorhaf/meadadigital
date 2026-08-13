import { apiFetch } from './client'

/**
 * Time/departamento da empresa do tenant (camada 5.20 #76) — shape do /admin/teams.
 * Uma conversa pode ser atribuída a um time (ver setConversationTeam). createdAt ISO string.
 */
export type Team = {
  id: string
  name: string
  createdAt: string
}

/** Lista os times da empresa do admin (mais recentes primeiro). */
export async function getMyTeams(): Promise<Team[]> {
  return apiFetch<Team[]>('/admin/teams')
}

/** Cria um time com {name}. Retorna o time criado (201). */
export async function createTeam(name: string): Promise<Team> {
  return apiFetch<Team>('/admin/teams', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

/** Renomeia um time. Retorna o time atualizado. */
export async function updateTeam(id: string, name: string): Promise<Team> {
  return apiFetch<Team>(`/admin/teams/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

/** Remove um time. 204 No Content. As conversas atribuídas ficam sem time (FK SET NULL). */
export async function deleteTeam(id: string): Promise<void> {
  return apiFetch<void>(`/admin/teams/${id}`, { method: 'DELETE' })
}
