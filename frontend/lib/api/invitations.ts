import { apiFetch } from './client'

/**
 * Convite de admin extra (camada 5.16 #6) — shape do /admin/invitations. token e inviteUrl
 * vêm do backend; o admin copia inviteUrl pra enviar. usedAt/usedBy null = ainda ativo.
 */
export type Invitation = {
  id: string
  email: string
  token: string
  inviteUrl: string
  createdAt: string
  expiresAt: string
  usedAt: string | null
  usedBy: string | null
}

/** Lista os convites da empresa do admin (inclui usados/expirados — histórico). */
export async function getMyInvitations(): Promise<Invitation[]> {
  return apiFetch<Invitation[]>('/admin/invitations')
}

/** Cria um convite para {email}. Retorna o convite com token + inviteUrl. */
export async function createInvitation(email: string): Promise<Invitation> {
  return apiFetch<Invitation>('/admin/invitations', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

/** Cancela (expira) um convite. 204 No Content. */
export async function cancelInvitation(id: string): Promise<void> {
  return apiFetch<void>(`/admin/invitations/${id}`, { method: 'DELETE' })
}

/**
 * Visão pública do convite (camada 5.16 #6) — o que a página /invite/{token} mostra antes
 * do login. Sem dados sensíveis (sem token, sem invited_by).
 */
export type PublicInvitation = {
  email: string
  companyName: string
  expiresAt: string
}

/**
 * Lookup público do convite (GET /api/invitations/{token}) — SEM auth. fetch direto (não
 * apiFetch, que injetaria Bearer e trataria 401 com signOut). Retorna null se inválido/
 * expirado (404), pra a página mostrar "convite não encontrado".
 */
export async function getInvitationPublic(token: string): Promise<PublicInvitation | null> {
  const base = process.env.NEXT_PUBLIC_API_URL
  if (!base) {
    throw new Error('NEXT_PUBLIC_API_URL não configurada.')
  }
  const res = await fetch(`${base}/api/invitations/${token}`)
  if (res.status === 404) {
    return null
  }
  if (!res.ok) {
    throw new Error(`lookup invitation failed: ${res.status}`)
  }
  return res.json() as Promise<PublicInvitation>
}

/** Resposta do accept: companyId vinculado + para onde redirecionar. */
export type AcceptInvitationResult = {
  companyId: string
  redirectTo: string
}

/**
 * Aceita o convite (POST /api/invitations/{token}/accept). Usa apiFetch porque o convidado
 * acabou de logar e tem JWT — o backend autentica como INVITEE (sem exigir linha em users).
 * Erros (404/409/410/403) propagam como ApiError com .reason — o caller (login) trata.
 */
export async function acceptInvitation(token: string): Promise<AcceptInvitationResult> {
  return apiFetch<AcceptInvitationResult>(`/api/invitations/${token}/accept`, {
    method: 'POST',
  })
}
