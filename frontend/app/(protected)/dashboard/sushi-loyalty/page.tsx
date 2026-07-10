'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getLoyalty, updateLoyalty } from '@/lib/api/sushi/loyalty'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  enabled: boolean
  thresholdOrders: string
  rewardKind: 'percent' | 'fixed'
  rewardValue: string // % ou R$
}

/**
 * Fidelidade do SushiBot. Form único: a cada N pedidos entregues, o próximo pedido do cliente
 * ganha o desconto configurado (percentual ou valor fixo).
 */
export default function SushiLoyaltyPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['sushi-loyalty'],
    queryFn: () => getLoyalty(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    enabled: d.enabled,
    thresholdOrders: String(d.thresholdOrders),
    rewardKind: d.rewardKind,
    rewardValue: d.rewardKind === 'percent' ? String(d.rewardValue) : String(d.rewardValue / 100),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      const rewardValue =
        form.rewardKind === 'percent'
          ? Math.round(Number(form.rewardValue || '0'))
          : Math.round(Number(form.rewardValue || '0') * 100)
      return updateLoyalty({
        enabled: form.enabled,
        thresholdOrders: Math.max(1, Math.round(Number(form.thresholdOrders || '1'))),
        rewardKind: form.rewardKind,
        rewardValue,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sushi-loyalty'] })
      setError(null)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'validation_error') {
        setError('Valores inválidos. Verifique os campos.')
      } else {
        setError('Erro ao salvar a fidelidade.')
      }
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Fidelidade"
        description="A cada N pedidos entregues, o próximo pedido do cliente ganha o desconto."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar a fidelidade.</p>
      ) : isPending || !form ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <Card>
          <form
            className="space-y-6"
            onSubmit={(e) => {
              e.preventDefault()
              saveMutation.mutate()
            }}
          >
            <Section title="Programa de fidelidade">
              <label className="flex items-center gap-2 text-sm text-muted-foreground">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(e) => setForm((f) => f && { ...f, enabled: e.target.checked })}
                />
                Ativar fidelidade
              </label>

              <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Pedidos para recompensa
                  </label>
                  <input
                    type="number"
                    min="1"
                    step="1"
                    value={form.thresholdOrders}
                    onChange={(e) => setForm((f) => f && { ...f, thresholdOrders: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Tipo de recompensa
                  </label>
                  <select
                    value={form.rewardKind}
                    onChange={(e) =>
                      setForm(
                        (f) => f && { ...f, rewardKind: e.target.value as 'percent' | 'fixed' },
                      )
                    }
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  >
                    <option value="percent">Percentual (%)</option>
                    <option value="fixed">Valor fixo (R$)</option>
                  </select>
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    {form.rewardKind === 'percent' ? 'Desconto (%)' : 'Desconto (R$)'}
                  </label>
                  <input
                    type="number"
                    min="0"
                    step={form.rewardKind === 'percent' ? '1' : '0.01'}
                    value={form.rewardValue}
                    onChange={(e) => setForm((f) => f && { ...f, rewardValue: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>

              <p className="mt-3 text-xs text-muted-foreground">
                Ex.: a cada {form.thresholdOrders || 'N'} pedidos entregues, o próximo ganha{' '}
                {form.rewardKind === 'percent'
                  ? `${form.rewardValue || '0'}% de desconto`
                  : `R$ ${form.rewardValue || '0'} de desconto`}
                .
              </p>
            </Section>

            {error && <p className="text-sm text-destructive">{error}</p>}
            {saved && <p className="text-sm text-emerald-600">Fidelidade salva.</p>}

            <div className="flex justify-end">
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? 'Salvando…' : 'Salvar'}
              </Button>
            </div>
          </form>
        </Card>
      )}
    </div>
  )
}
