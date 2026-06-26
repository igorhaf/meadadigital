'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/las/config'

type FormState = { deliveryFee: string; minOrder: string } // reais

/** Configurações do LasBot (varejo): taxa de entrega + valor mínimo do pedido (em R$). */
export default function LasSettingsPage() {
  const qc = useQueryClient()
  const [form, setForm] = useState<FormState | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['las-config'],
    queryFn: () => getConfig(),
  })

  useEffect(() => {
    if (data) {
      setForm({
        deliveryFee: String(data.deliveryFeeCents / 100),
        minOrder: String(data.minOrderCents / 100),
      })
    }
  }, [data])

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        deliveryFeeCents: Math.max(0, Math.round(Number(form.deliveryFee || 0) * 100)),
        minOrderCents: Math.max(0, Math.round(Number(form.minOrder || 0) * 100)),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['las-config'] })
      setError(null); setSaved(true); setTimeout(() => setSaved(false), 2500)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'validation_error') {
        setError('Valores inválidos. Use números maiores ou iguais a zero.')
      } else {
        setError('Erro ao salvar as configurações.')
      }
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader title="Configurações" description="Taxa de entrega e valor mínimo do pedido." />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as configurações.</p>
      ) : isPending || !form ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <Card>
          <form className="space-y-6" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
            <Section title="Delivery">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Taxa de entrega (R$)</label>
                  <input type="number" min="0" step="0.01" value={form.deliveryFee}
                    onChange={(e) => setForm((f) => f && { ...f, deliveryFee: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Pedido mínimo (R$)</label>
                  <input type="number" min="0" step="0.01" value={form.minOrder}
                    onChange={(e) => setForm((f) => f && { ...f, minOrder: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
              </div>
            </Section>

            {error && <p className="text-sm text-destructive">{error}</p>}
            {saved && <p className="text-sm text-emerald-600">Configurações salvas.</p>}

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
