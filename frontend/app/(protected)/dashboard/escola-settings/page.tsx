'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/escola/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  businessName: string
  opensAt: string
  closesAt: string
  notes: string
  visitReminderEnabled: boolean
  visitAutoCompleteEnabled: boolean
  paymentReminderEnabled: boolean
  paymentDueDay: string
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações da escola (camada 8.19): nome do negócio, horário de funcionamento e observações.
 */
export default function EscolaSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['escola-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    businessName: d.businessName ?? '',
    opensAt: hhmm(d.opensAt),
    closesAt: hhmm(d.closesAt),
    notes: d.notes ?? '',
    visitReminderEnabled: d.visitReminderEnabled ?? true,
    visitAutoCompleteEnabled: d.visitAutoCompleteEnabled ?? true,
    paymentReminderEnabled: d.paymentReminderEnabled ?? false,
    paymentDueDay: String(d.paymentDueDay ?? 10),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        businessName: form.businessName || null,
        opensAt: form.opensAt,
        closesAt: form.closesAt,
        notes: form.notes || null,
        visitReminderEnabled: form.visitReminderEnabled,
        visitAutoCompleteEnabled: form.visitAutoCompleteEnabled,
        paymentReminderEnabled: form.paymentReminderEnabled,
        paymentDueDay: Math.min(28, Math.max(1, Math.round(Number(form.paymentDueDay) || 10))),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['escola-config'] })
      setError(null)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_hours') {
        setError('O horário de abertura precisa ser anterior ao de fechamento.')
      } else if (e instanceof ApiError && e.reason === 'invalid_time') {
        setError('Horário inválido.')
      } else {
        setError('Erro ao salvar as configurações.')
      }
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Configurações da escola"
        description="Nome do negócio, horário de funcionamento e observações."
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
            <Section title="Identificação">
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Nome do negócio
                </label>
                <input
                  value={form.businessName}
                  onChange={(e) => setForm((f) => f && { ...f, businessName: e.target.value })}
                  maxLength={200}
                  placeholder="Escola Pequenos Passos…"
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                />
              </div>
            </Section>

            <Section title="Horário de funcionamento">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
              </div>
            </Section>

            <Section title="Observações">
              <textarea
                value={form.notes}
                onChange={(e) => setForm((f) => f && { ...f, notes: e.target.value })}
                rows={3}
                placeholder="Informações gerais para a IA (sem dado sensível)…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </Section>

            <Section title="Automações">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.visitReminderEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, visitReminderEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Lembrete de visita (véspera e no dia)
                    <span className="block text-xs text-muted-foreground">
                      Visita é o topo do funil de matrícula — o lembrete corta o no-show.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.visitAutoCompleteEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, visitAutoCompleteEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Marcar visita passada como realizada
                    <span className="block text-xs text-muted-foreground">
                      Quem faltou, a secretaria marca cancelada.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.paymentReminderEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, paymentReminderEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Lembrete de mensalidade em aberto
                    <span className="block text-xs text-muted-foreground">
                      1 lembrete gentil por mês pra matrícula ativa sem pagamento registrado.
                      Desligado por padrão — ligar dispara pra base toda de uma vez.
                    </span>
                  </span>
                </label>
                <div className="w-44">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Dia de vencimento
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={28}
                    value={form.paymentDueDay}
                    onChange={(e) => setForm((f) => f && { ...f, paymentDueDay: e.target.value })}
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
