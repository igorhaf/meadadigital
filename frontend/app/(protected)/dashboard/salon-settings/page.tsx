'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/salon/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  opensAt: string
  closesAt: string
  bufferMinutes: number
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações do salão (camada 7.5): horário de funcionamento + intervalo entre agendamentos.
 * Mudanças afetam agendamentos FUTUROS.
 */
export default function SalonSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['salon-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    opensAt: hhmm(d.opensAt),
    closesAt: hhmm(d.closesAt),
    bufferMinutes: d.bufferMinutes,
    reminderEnabled: d.reminderEnabled ?? true,
    autoCompleteEnabled: d.autoCompleteEnabled ?? false,
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig(form)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['salon-config'] })
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
        title="Configurações do salão"
        description="Horário de funcionamento e intervalo entre agendamentos."
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

            <Section title="Intervalo">
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Intervalo entre agendamentos (minutos)
                </label>
                <input
                  type="number"
                  min={0}
                  step={5}
                  value={form.bufferMinutes}
                  onChange={(e) =>
                    setForm((f) => f && { ...f, bufferMinutes: Number(e.target.value) })
                  }
                  className="w-full max-w-xs rounded-md border border-border bg-background px-3 py-2 text-sm"
                />
              </div>
            </Section>

            <p className="text-xs text-muted-foreground">
              Mudanças afetam apenas agendamentos <strong>futuros</strong>.
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
                    Lembrete de véspera pelo WhatsApp
                    <span className="block text-xs text-muted-foreground">
                      Mensagem fixa na véspera de cada horário agendado/confirmado (&quot;confirma?&quot;).
                      Remarcar reenvia; a resposta cai no fluxo normal da IA.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.autoCompleteEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, autoCompleteEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Concluir automaticamente horários confirmados que já passaram
                    <span className="block text-xs text-muted-foreground">
                      Confirmado com horário no passado vira &quot;realizado&quot; (sem mensagem). Faltas
                      continuam sendo marcadas manualmente.
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
