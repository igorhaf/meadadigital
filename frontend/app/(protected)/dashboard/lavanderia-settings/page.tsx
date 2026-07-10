'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/lavanderia/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  deliveryFee: string
  minOrder: string
  turnaroundDefault: string
  expressEnabled: boolean
  expressSurchargePct: string
  expressTurnaroundDays: string
  collectReminderEnabled: boolean
  readyReminderEnabled: boolean
  readyReminderDays: string
  reactivationEnabled: boolean
  reactivationDays: string
  reactivationCouponCode: string
}

/**
 * Configurações do LavanderiaBot: taxa de entrega + valor mínimo do pedido (em R$) + prazo padrão
 * (turnaround default, em dias — sugestão quando um serviço não tem prazo específico).
 */
export default function LavanderiaSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['lavanderia-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    deliveryFee: String(d.deliveryFeeCents / 100),
    minOrder: String(d.minOrderCents / 100),
    turnaroundDefault: String(d.turnaroundDaysDefault),
    expressEnabled: d.expressEnabled ?? true,
    expressSurchargePct: String(d.expressSurchargePct ?? 50),
    expressTurnaroundDays: String(d.expressTurnaroundDays ?? 1),
    collectReminderEnabled: d.collectReminderEnabled ?? true,
    readyReminderEnabled: d.readyReminderEnabled ?? true,
    readyReminderDays: String(d.readyReminderDays ?? 2),
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
        turnaroundDaysDefault: Math.max(0, Math.round(Number(form.turnaroundDefault || 0))),
        expressEnabled: form.expressEnabled,
        expressSurchargePct: Math.min(
          300,
          Math.max(0, Math.round(Number(form.expressSurchargePct) || 50)),
        ),
        expressTurnaroundDays: Math.min(
          30,
          Math.max(0, Math.round(Number(form.expressTurnaroundDays) || 1)),
        ),
        collectReminderEnabled: form.collectReminderEnabled,
        readyReminderEnabled: form.readyReminderEnabled,
        readyReminderDays: Math.min(
          30,
          Math.max(1, Math.round(Number(form.readyReminderDays) || 2)),
        ),
        reactivationEnabled: form.reactivationEnabled,
        reactivationDays: Math.min(
          365,
          Math.max(7, Math.round(Number(form.reactivationDays) || 30)),
        ),
        reactivationCouponCode: form.reactivationCouponCode.trim() || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['lavanderia-config'] })
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
      <PageHeader
        title="Configurações"
        description="Taxa de entrega, valor mínimo e prazo padrão do pedido."
      />

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
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
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
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Prazo padrão (dias)
                  </label>
                  <input
                    type="number"
                    min="0"
                    step="1"
                    value={form.turnaroundDefault}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, turnaroundDefault: e.target.value })
                    }
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>
            </Section>

            <Section title="Serviço express">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.expressEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, expressEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Oferecer serviço EXPRESS
                    <span className="block text-xs text-muted-foreground">
                      Quando o cliente tem pressa, a IA oferece prazo curto com sobretaxa — o valor
                      vem daqui, nunca é inventado.
                    </span>
                  </span>
                </label>
                <div className="grid grid-cols-2 gap-4 sm:max-w-md">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Sobretaxa (% do subtotal)
                    </label>
                    <input
                      type="number"
                      min={0}
                      max={300}
                      value={form.expressSurchargePct}
                      onChange={(e) =>
                        setForm((f) => f && { ...f, expressSurchargePct: e.target.value })
                      }
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Prazo express (dias)
                    </label>
                    <input
                      type="number"
                      min={0}
                      max={30}
                      value={form.expressTurnaroundDays}
                      onChange={(e) =>
                        setForm((f) => f && { ...f, expressTurnaroundDays: e.target.value })
                      }
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                  </div>
                </div>
              </div>
            </Section>

            <Section title="Automações">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.collectReminderEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, collectReminderEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Lembrete de coleta na véspera
                    <span className="block text-xs text-muted-foreground">
                      &quot;Sua coleta é amanhã de manhã — alguém em casa?&quot; Corta coleta
                      furada.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.readyReminderEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, readyReminderEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Lembrete de peça pronta parada
                    <span className="block text-xs text-muted-foreground">
                      Pedido em &quot;pronto&quot; há dias sem sair pra entrega → cobra o cliente.
                    </span>
                  </span>
                </label>
                <div className="w-44">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Dias parado até o lembrete
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={30}
                    value={form.readyReminderDays}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, readyReminderDays: e.target.value })
                    }
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
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
                    Reativação de cliente inativo
                    <span className="block text-xs text-muted-foreground">
                      Convite gentil pra quem não pede há N dias (1 toque por ciclo). Desligado por
                      padrão — ligar pode disparar pra base toda de uma vez.
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
                      placeholder="VOLTA15"
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
