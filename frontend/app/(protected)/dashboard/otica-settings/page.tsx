'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/otica/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  opensAt: string
  closesAt: string
  examDurationMinutes: number
  minOrder: string // reais
  leadTimeDaysDefault: number
  examReminderEnabled: boolean
  pickupFollowupEnabled: boolean
  pickupFollowupDays: string
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações da ótica (camada 8.12): janela de funcionamento + duração do exame (FLUXO A) e
 * pedido mínimo + prazo de produção padrão dos óculos sob encomenda (FLUXO B). Mudanças afetam
 * exames FUTUROS e novos pedidos.
 */
export default function OticaSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['otica-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    opensAt: hhmm(d.opensAt),
    closesAt: hhmm(d.closesAt),
    examDurationMinutes: d.examDurationMinutes,
    minOrder: String(d.minOrderCents / 100),
    leadTimeDaysDefault: d.leadTimeDaysDefault,
    examReminderEnabled: d.examReminderEnabled ?? true,
    pickupFollowupEnabled: d.pickupFollowupEnabled ?? true,
    pickupFollowupDays: String(d.pickupFollowupDays ?? 3),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        opensAt: form.opensAt,
        closesAt: form.closesAt,
        examDurationMinutes: form.examDurationMinutes,
        minOrderCents: Math.max(0, Math.round(Number(form.minOrder || 0) * 100)),
        leadTimeDaysDefault: form.leadTimeDaysDefault,
        examReminderEnabled: form.examReminderEnabled,
        pickupFollowupEnabled: form.pickupFollowupEnabled,
        pickupFollowupDays: Math.max(1, Math.min(30, Number(form.pickupFollowupDays) || 3)),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['otica-config'] })
      setError(null)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_hours') {
        setError('O horário de abertura precisa ser anterior ao de fechamento.')
      } else if (e instanceof ApiError && e.reason === 'invalid_time') {
        setError('Horário inválido.')
      } else if (e instanceof ApiError && e.reason === 'validation_error') {
        setError('Valores inválidos. Use números maiores ou iguais a zero.')
      } else {
        setError('Erro ao salvar as configurações.')
      }
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Configurações da ótica"
        description="Janela de funcionamento e duração do exame; pedido mínimo e prazo padrão de produção dos óculos."
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
            <Section title="Exames (horário de funcionamento)">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Abre às
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
                    Duração do exame (min)
                  </label>
                  <input
                    type="number"
                    min={5}
                    step={5}
                    value={form.examDurationMinutes}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, examDurationMinutes: Number(e.target.value) })
                    }
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>
            </Section>

            <Section title="Óculos (pedidos sob receita)">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
                    Prazo de produção padrão (dias)
                  </label>
                  <input
                    type="number"
                    min={0}
                    step={1}
                    value={form.leadTimeDaysDefault}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, leadTimeDaysDefault: Number(e.target.value) })
                    }
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>
            </Section>

            <p className="text-xs text-muted-foreground">
              Mudanças afetam apenas exames <strong>futuros</strong> e novos pedidos.
            </p>

            <Section title="Automações">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.examReminderEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, examReminderEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Lembrete de exame na véspera
                    <span className="block text-xs text-muted-foreground">
                      &quot;Seu exame é amanhã às 14h — confirma?&quot; A resposta cai na conversa
                      com a IA (confirmar ou desmarcar, liberando o horário).
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.pickupFollowupEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, pickupFollowupEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Aviso de óculos pronto parado
                    <span className="block text-xs text-muted-foreground">
                      Pedido em &quot;pronto&quot; sem retirada recebe um lembrete gentil após a
                      janela abaixo (1x por vez).
                    </span>
                  </span>
                </label>
                <div className="w-44">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Dias em pronto até o aviso
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={30}
                    value={form.pickupFollowupDays}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, pickupFollowupDays: e.target.value })
                    }
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
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
