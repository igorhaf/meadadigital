'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/pousada/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  checkInTime: string
  checkOutTime: string
  cancellationPolicy: string
  reminderEnabled: boolean
  autoTransitionEnabled: boolean
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações da pousada (camada 7.6): horário de check-in/check-out + política de cancelamento.
 * Mudanças afetam reservas FUTURAS.
 */
export default function PousadaSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['pousada-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    checkInTime: hhmm(d.checkInTime),
    checkOutTime: hhmm(d.checkOutTime),
    cancellationPolicy: d.cancellationPolicy ?? '',
    reminderEnabled: d.reminderEnabled ?? true,
    autoTransitionEnabled: d.autoTransitionEnabled ?? false,
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        checkInTime: form.checkInTime,
        checkOutTime: form.checkOutTime,
        cancellationPolicy: form.cancellationPolicy || null,
        reminderEnabled: form.reminderEnabled,
        autoTransitionEnabled: form.autoTransitionEnabled,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pousada-config'] })
      setError(null)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_time') {
        setError('Horário inválido.')
      } else {
        setError('Erro ao salvar as configurações.')
      }
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Configurações da pousada"
        description="Horário de check-in/check-out e política de cancelamento."
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
            <Section title="Horários">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Check-in a partir de
                  </label>
                  <input
                    type="time"
                    value={form.checkInTime}
                    onChange={(e) => setForm((f) => f && { ...f, checkInTime: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Check-out até
                  </label>
                  <input
                    type="time"
                    value={form.checkOutTime}
                    onChange={(e) => setForm((f) => f && { ...f, checkOutTime: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>
            </Section>

            <Section title="Política de cancelamento">
              <textarea
                value={form.cancellationPolicy}
                onChange={(e) => setForm((f) => f && { ...f, cancellationPolicy: e.target.value })}
                rows={4}
                placeholder="Texto livre — a IA repassa ao cliente. Opcional."
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </Section>

            <p className="text-xs text-muted-foreground">
              Mudanças afetam apenas reservas <strong>futuras</strong>.
            </p>

            <Section title="Automações">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.reminderEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, reminderEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Lembrete de check-in na véspera
                    <span className="block text-xs text-muted-foreground">
                      &quot;Sua estadia começa amanhã — confirma sua chegada?&quot; A resposta cai
                      na conversa com a IA (confirmar ou cancelar, liberando o quarto).
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.autoTransitionEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, autoTransitionEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Atualizar status vencidos automaticamente
                    <span className="block text-xs text-muted-foreground">
                      Confirmado com check-in vencido (1 dia de folga) vira no-show; hospedado com
                      check-out vencido vira check-out. Sem mensagens. Desligado por padrão.
                    </span>
                  </span>
                </label>
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
