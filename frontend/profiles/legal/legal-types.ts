import type { LegalCaseStatusId } from './legal-case-status'

/** Cliente do escritório (espelha LegalClient). */
export type LegalClient = {
  id: string
  name: string
  email: string | null
  phone: string | null
  document: string | null
  contactId: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Andamento de um processo (espelha LegalCaseUpdate). */
export type LegalCaseUpdate = {
  id: string
  title: string
  body: string | null
  occurredAt: string
  createdAt: string
}

/** Processo (espelha LegalCase). cnjNumber = storage; cnjNumberFormatted = máscara. */
export type LegalCase = {
  id: string
  legalClientId: string
  legalClientName: string
  cnjNumber: string
  cnjNumberFormatted: string
  title: string
  description: string | null
  court: string | null
  forum: string | null
  subject: string | null
  status: LegalCaseStatusId
  createdAt: string
  updatedAt: string
  statusUpdatedAt: string
  updatesCount: number
  updates: LegalCaseUpdate[]
}

/**
 * Formata o CNJ (20 dígitos) como NNNNNNN-DD.AAAA.J.TR.OOOO. Aceita entrada parcial
 * (aplica a máscara progressivamente, para o input controlado).
 */
export function formatCnj(raw: string): string {
  const d = raw.replace(/\D/g, '').slice(0, 20)
  let out = d.slice(0, 7)
  if (d.length > 7) out += '-' + d.slice(7, 9)
  if (d.length > 9) out += '.' + d.slice(9, 13)
  if (d.length > 13) out += '.' + d.slice(13, 14)
  if (d.length > 14) out += '.' + d.slice(14, 16)
  if (d.length > 16) out += '.' + d.slice(16, 20)
  return out
}

/** Só os dígitos do CNJ (para enviar ao backend). */
export function cnjDigits(raw: string): string {
  return raw.replace(/\D/g, '')
}
