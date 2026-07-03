import { apiFetch } from '@/lib/api/client'
import type { WeddingPayment } from '@/profiles/casamento/casamento-types'

export type CreatePaymentInput = {
  kind?: 'sinal' | 'parcela'
  label?: string | null
  dueDate: string // yyyy-MM-dd
  amountCents: number
}

export function listPayments(proposalId: string): Promise<{ items: WeddingPayment[] }> {
  return apiFetch<{ items: WeddingPayment[] }>(`/api/casamento/proposals/${proposalId}/payments`)
}

export function createPayment(
  proposalId: string,
  input: CreatePaymentInput,
): Promise<WeddingPayment> {
  return apiFetch<WeddingPayment>(`/api/casamento/proposals/${proposalId}/payments`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function setPaymentPaid(
  proposalId: string,
  paymentId: string,
  paid: boolean,
): Promise<WeddingPayment> {
  return apiFetch<WeddingPayment>(
    `/api/casamento/proposals/${proposalId}/payments/${paymentId}/paid`,
    {
      method: 'PATCH',
      body: JSON.stringify({ paid }),
    },
  )
}

export function deletePayment(proposalId: string, paymentId: string): Promise<void> {
  return apiFetch<void>(`/api/casamento/proposals/${proposalId}/payments/${paymentId}`, {
    method: 'DELETE',
  })
}
