/**
 * Status de um processo (camada 7.2) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/legal/LegalCaseStatus.java.
 *
 * O LegalCaseStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK constraint de legal_cases.status (migration 31) trava os mesmos ids no banco.
 * Transição é LIVRE (qualquer status → qualquer status). notificationText: null para 'ativo'.
 */
export const LEGAL_CASE_STATUSES = [
  {
    id: 'ativo',
    label: 'Ativo',
    notificationText: null,
  },
  {
    id: 'suspenso',
    label: 'Suspenso',
    notificationText:
      'Informação sobre seu processo: foi colocado em SUSPENSÃO. Em caso de dúvida, entre em contato com nosso escritório.',
  },
  {
    id: 'arquivado',
    label: 'Arquivado',
    notificationText:
      'Informação sobre seu processo: foi ARQUIVADO. Em caso de dúvida, entre em contato com nosso escritório.',
  },
  {
    id: 'encerrado',
    label: 'Encerrado',
    notificationText:
      'Informação sobre seu processo: foi ENCERRADO. Em caso de dúvida, entre em contato com nosso escritório.',
  },
] as const

export type LegalCaseStatus = (typeof LEGAL_CASE_STATUSES)[number]
export type LegalCaseStatusId = LegalCaseStatus['id']

/** Rótulo pt-BR de um status (fallback: o próprio id). */
export function statusLabel(id: string): string {
  return LEGAL_CASE_STATUSES.find((s) => s.id === id)?.label ?? id
}
