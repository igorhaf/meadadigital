'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { apiFetch } from '@/lib/api/client'

type TradeIn = {
  id: string
  customerName: string
  usedBrand: string
  usedModel: string
  usedYear: number | null
  usedKm: number | null
  usedCondition: string | null
  askingCents: number | null
  status: 'aberta' | 'avaliada' | 'aceita' | 'recusada'
  offerCents: number | null
  notes: string | null
  interestVehicle: string | null
}

const STATUS_LABEL: Record<string, string> = {
  aberta: 'Aberta',
  avaliada: 'Avaliada',
  aceita: 'Aceita',
  recusada: 'Recusada',
}

function brl(cents: number | null): string {
  return cents == null ? '—' : `R$ ${(cents / 100).toFixed(2).replace('.', ',')}`
}

/**
 * Trade-in (onda Concessionária 2, backlog #5): usados coletados pela IA na conversa. A avaliação
 * (proposta de abatimento) é HUMANA — a IA nunca precifica.
 */
export default function ConcessionariaTradeinPage() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState('aberta')
  const [offerDraft, setOfferDraft] = useState<Record<string, string>>({})

  const { data, isPending, isError } = useQuery({
    queryKey: ['concessionaria-tradein', statusFilter],
    queryFn: () =>
      apiFetch<{ items: TradeIn[] }>(
        `/api/concessionaria/tradein${statusFilter ? `?status=${statusFilter}` : ''}`,
      ),
  })

  const updateMutation = useMutation({
    mutationFn: (input: { id: string; status?: string; offerCents?: number | null }) =>
      apiFetch(`/api/concessionaria/tradein/${input.id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          status: input.status ?? null,
          offerCents: input.offerCents ?? null,
        }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['concessionaria-tradein'] }),
  })

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Trade-in (usados na troca)"
        description="Usados coletados pela IA na conversa. A avaliação é sua — a IA nunca promete valor."
      />

      <div className="flex gap-2">
        {['aberta', 'avaliada', 'aceita', 'recusada', ''].map((s) => (
          <Button
            key={s || 'todas'}
            variant={statusFilter === s ? 'default' : 'outline'}
            className="h-7 px-3 text-xs"
            onClick={() => setStatusFilter(s)}
          >
            {s ? STATUS_LABEL[s] : 'Todas'}
          </Button>
        ))}
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as propostas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma proposta nesse filtro.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((t) => (
            <div key={t.id} className="space-y-1 px-4 py-3">
              <div className="flex items-center gap-2">
                <span className="font-medium">
                  {t.usedBrand} {t.usedModel}
                  {t.usedYear ? ` ${t.usedYear}` : ''}
                </span>
                <Badge variant="muted">{STATUS_LABEL[t.status]}</Badge>
                {t.interestVehicle && <Badge variant="info">interesse: {t.interestVehicle}</Badge>}
              </div>
              <p className="text-xs text-muted-foreground">
                {t.customerName}
                {t.usedKm ? ` · ${t.usedKm.toLocaleString('pt-BR')} km` : ''}
                {t.usedCondition ? ` · ${t.usedCondition}` : ''} · pedido do cliente:{' '}
                {brl(t.askingCents)} · proposta da loja: {brl(t.offerCents)}
              </p>
              {(t.status === 'aberta' || t.status === 'avaliada') && (
                <div className="flex items-center gap-2 pt-1">
                  <input
                    type="number"
                    min={0}
                    step={100}
                    placeholder="Proposta (R$)"
                    value={offerDraft[t.id] ?? ''}
                    onChange={(e) => setOfferDraft((d) => ({ ...d, [t.id]: e.target.value }))}
                    className="w-36 rounded-md border border-border bg-background px-2 py-1 text-xs"
                  />
                  <Button
                    className="h-7 px-2 text-xs"
                    disabled={updateMutation.isPending || !offerDraft[t.id]}
                    onClick={() =>
                      updateMutation.mutate({
                        id: t.id,
                        status: 'avaliada',
                        offerCents: Math.round(Number(offerDraft[t.id]) * 100),
                      })
                    }
                  >
                    Avaliar
                  </Button>
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    disabled={updateMutation.isPending}
                    onClick={() => updateMutation.mutate({ id: t.id, status: 'aceita' })}
                  >
                    Aceita
                  </Button>
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    disabled={updateMutation.isPending}
                    onClick={() => updateMutation.mutate({ id: t.id, status: 'recusada' })}
                  >
                    Recusada
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
