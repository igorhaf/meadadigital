import { apiFetch } from '@/lib/api/client'
import type { Payment, PaymentSummary } from '@/profiles/cursos/cursos-types'

type PaymentsResponse = { items: Payment[]; summary: PaymentSummary }

export type RecordPaymentInput = {
  referenceMonth: string // "YYYY-MM-DD" (dia 01)
  amountCents: number
  method?: string | null
  notes?: string | null
}

export function listPayments(enrollmentId: string): Promise<PaymentsResponse> {
  return apiFetch<PaymentsResponse>(`/api/cursos/enrollments/${enrollmentId}/payments`)
}

export function recordPayment(enrollmentId: string, input: RecordPaymentInput): Promise<Payment> {
  return apiFetch<Payment>(`/api/cursos/enrollments/${enrollmentId}/payments`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function deletePayment(enrollmentId: string, paymentId: string): Promise<void> {
  return apiFetch<void>(`/api/cursos/enrollments/${enrollmentId}/payments/${paymentId}`, {
    method: 'DELETE',
  })
}
