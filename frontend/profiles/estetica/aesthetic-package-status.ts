/**
 * Status de um pacote multi-sessão de estética (camada 8.3) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/estetica/AestheticPackageStatus.java.
 *
 * O AestheticPackageStatusParityTest (backend) garante a paridade. Transições MANUAIS (PATCH do
 * tenant): pendente→ativo/cancelado; ativo→expirado/cancelado. 'esgotado' NÃO é destino manual (é
 * materializado pelo backend ao consumir a última sessão); a reabertura esgotado→ativo (ao cancelar
 * agendamento que consumiu) também é do backend.
 */
export const AESTHETIC_PACKAGE_STATUSES = [
  { id: 'pendente', label: 'Pendente' },
  { id: 'ativo', label: 'Ativo' },
  { id: 'esgotado', label: 'Esgotado' },
  { id: 'expirado', label: 'Expirado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type AestheticPackageStatus = (typeof AESTHETIC_PACKAGE_STATUSES)[number]
export type AestheticPackageStatusId = AestheticPackageStatus['id']

/** Transições MANUAIS permitidas pelo painel (espelha AestheticPackageStatus.allowedNext). */
export const ALLOWED_NEXT: Record<AestheticPackageStatusId, AestheticPackageStatusId[]> = {
  pendente: ['ativo', 'cancelado'],
  ativo: ['expirado', 'cancelado'],
  esgotado: [],
  expirado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return AESTHETIC_PACKAGE_STATUSES.find((s) => s.id === id)?.label ?? id
}
