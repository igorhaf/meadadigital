'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/nutri/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  opensAt: string
  closesAt: string
  bufferMinutes: number
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
  reengagementEnabled: boolean
  reengagementDays: string
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações do consultório de nutrição (camada 8.0): horário de funcionamento + intervalo
 * entre consultas. Mudanças afetam consultas FUTURAS.
 */
export default function NutriSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['nutri-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    opensAt: hhmm(d.opensAt),
    closesAt: hhmm(d.closesAt),
    bufferMinutes: d.bufferMinutes,
    reminderEnabled: d.reminderEnabled ?? true,
    autoCompleteEnabled: d.autoCompleteEnabled ?? true,
    reengagementEnabled: d.reengagementEnabled ?? false,
    reengagementDays: String(d.reengagementDays ?? 30),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        ...form,
        reengagementDays: Math.max(7, Math.min(365, Number(form.reengagementDays) || 30)),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['nutri-config'] })
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
        title="Configurações do consultório"
        description="Horário de funcionamento e intervalo entre consultas."
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
                  Intervalo entre consultas (minutos)
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
              Mudanças afetam apenas consultas <strong>futuras</strong>.
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
                    Lembrete de consulta na véspera
                    <span className="block text-xs text-muted-foreground">
                      &quot;Sua consulta é amanhã às 14h — confirma?&quot; A resposta cai na
                      conversa com a IA (confirmar ou desmarcar).
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
                    Concluir automaticamente consultas confirmadas que já passaram
                    <span className="block text-xs text-muted-foreground">
                      Confirmada com horário no passado vira &quot;realizada&quot; (sem mensagem).
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.reengagementEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, reengagementEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Régua de retomada (paciente sem retorno)
                    <span className="block text-xs text-muted-foreground">
                      Um convite gentil quando a última consulta realizada passa da janela e não há
                      nada agendado. 1 toque por ciclo. Desligado por padrão — ligar pode disparar
                      pra base toda de uma vez.
                    </span>
                  </span>
                </label>
                <div className="w-44">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Dias sem consulta até o convite
                  </label>
                  <input
                    type="number"
                    min={7}
                    max={365}
                    value={form.reengagementDays}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, reengagementDays: e.target.value })
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
