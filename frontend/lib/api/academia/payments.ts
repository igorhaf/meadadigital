import { apiFetch } from '@/lib/api/client'
import type { Payment, PaymentSummary } from '@/profiles/academia/academia-types'

type PaymentsResponse = { items: Payment[]; summary: PaymentSummary }

export type RecordPaymentInput = {
  referenceMonth: string // "YYYY-MM-DD" (dia 01)
  amountCents: number
  method?: string | null
  notes?: string | null
}

export function listPayments(membershipId: string): Promise<PaymentsResponse> {
  return apiFetch<PaymentsResponse>(`/api/academia/memberships/${membershipId}/payments`)
}

export function recordPayment(membershipId: string, input: RecordPaymentInput): Promise<Payment> {
  return apiFetch<Payment>(`/api/academia/memberships/${membershipId}/payments`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function deletePayment(membershipId: string, paymentId: string): Promise<void> {
  return apiFetch<void>(`/api/academia/memberships/${membershipId}/payments/${paymentId}`, {
    method: 'DELETE',
  })
}
