'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/comida/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  deliveryFee: string // reais
  minOrder: string // reais
  opensAt: string
  closesAt: string
  autoDeliverHours: string
  reactivationEnabled: boolean
  reactivationDays: string
  reactivationCouponCode: string
}

/** Configurações do ComidaBot (delivery): taxa de entrega + valor mínimo do pedido (em R$). */
export default function ComidaSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['comida-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    deliveryFee: String(d.deliveryFeeCents / 100),
    minOrder: String(d.minOrderCents / 100),
    opensAt: d.opensAt?.slice(0, 5) ?? '',
    closesAt: d.closesAt?.slice(0, 5) ?? '',
    autoDeliverHours: d.autoDeliverHours == null ? '' : String(d.autoDeliverHours),
    reactivationEnabled: d.reactivationEnabled ?? false,
    reactivationDays: String(d.reactivationDays ?? 30),
    reactivationCouponCode: d.reactivationCouponCode ?? '',
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        deliveryFeeCents: Math.max(0, Math.round(Number(form.deliveryFee || 0) * 100)),
        minOrderCents: Math.max(0, Math.round(Number(form.minOrder || 0) * 100)),
        opensAt: form.opensAt || null,
        closesAt: form.closesAt || null,
        autoDeliverHours: form.autoDeliverHours.trim()
          ? Math.min(24, Math.max(1, Math.round(Number(form.autoDeliverHours) || 1)))
          : null,
        reactivationEnabled: form.reactivationEnabled,
        reactivationDays: Math.min(
          365,
          Math.max(7, Math.round(Number(form.reactivationDays) || 30)),
        ),
        reactivationCouponCode: form.reactivationCouponCode.trim() || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['comida-config'] })
      setError(null)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
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
          <form
            className="space-y-6"
            onSubmit={(e) => {
              e.preventDefault()
              saveMutation.mutate()
            }}
          >
            <Section title="Delivery">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Taxa de entrega (R$)
                  </label>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.deliveryFee}
                    onChange={(e) => setForm((f) => f && { ...f, deliveryFee: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Pedido mínimo (R$)
                  </label>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.minOrder}
                    onChange={(e) => setForm((f) => f && { ...f, minOrder: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>
            </Section>

            <Section title="Horário e automações">
              <div className="space-y-4">
                <div className="grid grid-cols-3 gap-4 sm:max-w-lg">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Delivery abre às
                    </label>
                    <input
                      type="time"
                      value={form.opensAt}
                      onChange={(e) => setForm((f) => f && { ...f, opensAt: e.target.value })}
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Fecha às
                    </label>
                    <input
                      type="time"
                      value={form.closesAt}
                      onChange={(e) => setForm((f) => f && { ...f, closesAt: e.target.value })}
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Auto-entrega (horas)
                    </label>
                    <input
                      type="number"
                      min={1}
                      max={24}
                      value={form.autoDeliverHours}
                      placeholder="vazio = off"
                      onChange={(e) =>
                        setForm((f) => f && { ...f, autoDeliverHours: e.target.value })
                      }
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                  </div>
                </div>
                <p className="text-xs text-muted-foreground">
                  Horário vazio = sempre aberto. Fora do horário a IA avisa e não fecha pedido.
                  Auto-entrega: pedido em &quot;saiu pra entrega&quot; há mais de N horas vira
                  &quot;entregue&quot; sozinho.
                </p>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.reactivationEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, reactivationEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Reativação de cliente sumido
                    <span className="block text-xs text-muted-foreground">
                      1 toque por ciclo pra quem não pede há N dias. Desligado por padrão — ligar
                      pode disparar pra base toda.
                    </span>
                  </span>
                </label>
                <div className="grid grid-cols-2 gap-4 sm:max-w-md">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Dias sem pedido até o convite
                    </label>
                    <input
                      type="number"
                      min={7}
                      max={365}
                      value={form.reactivationDays}
                      onChange={(e) =>
                        setForm((f) => f && { ...f, reactivationDays: e.target.value })
                      }
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Cupom de retorno (opcional)
                    </label>
                    <input
                      value={form.reactivationCouponCode}
                      onChange={(e) =>
                        setForm((f) => f && { ...f, reactivationCouponCode: e.target.value })
                      }
                      placeholder="SAUDADE20"
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                  </div>
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
