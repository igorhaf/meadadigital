'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/sushi/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  deliveryFee: string // reais
  minOrder: string // reais
  schedulingEnabled: boolean
  upsellEnabled: boolean
  reactivationEnabled: boolean
  reactivationDays: string
  reactivationCouponCode: string
}

/**
 * Configurações do SushiBot: taxa de entrega + valor mínimo do pedido (em R$) e se aceita pedidos
 * agendados (data + período).
 */
export default function SushiSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['sushi-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    deliveryFee: String(d.deliveryFeeCents / 100),
    minOrder: String(d.minOrderCents / 100),
    schedulingEnabled: d.schedulingEnabled,
    upsellEnabled: d.upsellEnabled ?? true,
    reactivationEnabled: d.reactivationEnabled ?? false,
    reactivationDays: String(d.reactivationDays ?? 21),
    reactivationCouponCode: d.reactivationCouponCode ?? '',
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        deliveryFeeCents: Math.max(0, Math.round(Number(form.deliveryFee || 0) * 100)),
        minOrderCents: Math.max(0, Math.round(Number(form.minOrder || 0) * 100)),
        schedulingEnabled: form.schedulingEnabled,
        upsellEnabled: form.upsellEnabled,
        reactivationEnabled: form.reactivationEnabled,
        reactivationDays: Math.max(7, Math.min(180, Number(form.reactivationDays) || 21)),
        reactivationCouponCode: form.reactivationCouponCode || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sushi-config'] })
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
        description="Taxa de entrega, valor mínimo e pedidos agendados."
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

            <Section title="Agendamento">
              <label className="flex items-center gap-2 text-sm text-muted-foreground">
                <input
                  type="checkbox"
                  checked={form.schedulingEnabled}
                  onChange={(e) =>
                    setForm((f) => f && { ...f, schedulingEnabled: e.target.checked })
                  }
                />
                Aceitar pedidos agendados
              </label>
              <p className="mt-2 text-xs text-muted-foreground">
                Quando ativo, a IA pode agendar pedidos para uma data e período (manhã/tarde/noite).
              </p>
            </Section>

            <Section title="Sugestão complementar (upsell)">
              <label className="flex items-center gap-2 text-sm text-muted-foreground">
                <input
                  type="checkbox"
                  checked={form.upsellEnabled}
                  onChange={(e) => setForm((f) => f && { ...f, upsellEnabled: e.target.checked })}
                />
                A IA oferece 1 item complementar antes de fechar o pedido
              </label>
              <p className="mt-2 text-xs text-muted-foreground">
                Bebida/sobremesa/combo maior — sempre do próprio cardápio disponível, uma oferta só,
                sem insistir.
              </p>
            </Section>

            <Section title="Reativação de cliente inativo">
              <div className="space-y-4">
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
                    Chamar de volta pelo WhatsApp quem não pede há muito tempo
                    <span className="block text-xs text-muted-foreground">
                      Mensagem automática fixa, 1x por cliente por janela (o mesmo cliente não é
                      reabordado dentro do período). Desligado por padrão — ligar é decisão sua.
                    </span>
                  </span>
                </label>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Dias sem pedido até considerar inativo
                    </label>
                    <input
                      type="number"
                      min={7}
                      max={180}
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
                      placeholder="Código de um cupom da tela Cupons"
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                    <p className="mt-1 text-xs text-muted-foreground">
                      Só entra na mensagem se o cupom existir, estar ativo e dentro da validade.
                    </p>
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
