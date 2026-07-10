import { apiFetch } from '@/lib/api/client'
import type { EscolaPayment, EscolaPaymentSummary } from '@/profiles/escola/escola-types'

type PaymentsResponse = { items: EscolaPayment[]; summary: EscolaPaymentSummary }

export type RegisterPaymentInput = {
  referenceMonth: string // "YYYY-MM-DD" (dia 01)
  amountCents: number
  method?: string | null
  notes?: string | null
}

export function listPayments(enrollmentId: string): Promise<PaymentsResponse> {
  return apiFetch<PaymentsResponse>(`/api/escola/enrollments/${enrollmentId}/payments`)
}

export function registerPayment(
  enrollmentId: string,
  input: RegisterPaymentInput,
): Promise<EscolaPayment> {
  return apiFetch<EscolaPayment>(`/api/escola/enrollments/${enrollmentId}/payments`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function deletePayment(enrollmentId: string, paymentId: string): Promise<void> {
  return apiFetch<void>(`/api/escola/enrollments/${enrollmentId}/payments/${paymentId}`, {
    method: 'DELETE',
  })
}
