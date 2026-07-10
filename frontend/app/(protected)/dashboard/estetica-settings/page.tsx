'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/estetica/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  opensAt: string
  closesAt: string
  slotMinutes: string
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
  autoExpireEnabled: boolean
  packageValidityDays: string
  renewalEnabled: boolean
  renewalDays: string
  expiryWarningDays: string
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/** Configurações do EsteticaBot (camada 8.3): horário de funcionamento + granularidade de slot. */
export default function EsteticaSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['estetica-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    opensAt: hhmm(d.opensAt),
    closesAt: hhmm(d.closesAt),
    slotMinutes: String(d.slotMinutes),
    reminderEnabled: d.reminderEnabled ?? true,
    autoCompleteEnabled: d.autoCompleteEnabled ?? true,
    autoExpireEnabled: d.autoExpireEnabled ?? true,
    packageValidityDays: d.packageValidityDays == null ? '' : String(d.packageValidityDays),
    renewalEnabled: d.renewalEnabled ?? false,
    renewalDays: String(d.renewalDays ?? 30),
    expiryWarningDays: String(d.expiryWarningDays ?? 7),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        opensAt: form.opensAt,
        closesAt: form.closesAt,
        slotMinutes: Math.max(5, Math.round(Number(form.slotMinutes) || 30)),
        reminderEnabled: form.reminderEnabled,
        autoCompleteEnabled: form.autoCompleteEnabled,
        autoExpireEnabled: form.autoExpireEnabled,
        packageValidityDays: form.packageValidityDays.trim()
          ? Math.min(1095, Math.max(7, Math.round(Number(form.packageValidityDays) || 7)))
          : null,
        renewalEnabled: form.renewalEnabled,
        renewalDays: Math.min(365, Math.max(7, Math.round(Number(form.renewalDays) || 30))),
        expiryWarningDays: Math.min(
          60,
          Math.max(1, Math.round(Number(form.expiryWarningDays) || 7)),
        ),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['estetica-config'] })
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
        title="Configurações"
        description="Horário de funcionamento e granularidade de agendamento."
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
                    Slot (min)
                  </label>
                  <input
                    type="number"
                    min="5"
                    value={form.slotMinutes}
                    onChange={(e) => setForm((f) => f && { ...f, slotMinutes: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>
            </Section>

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
                    Lembrete de sessão na véspera (SIM/NÃO)
                    <span className="block text-xs text-muted-foreground">
                      SIM confirma; NÃO desmarca e devolve a sessão ao pacote automaticamente.
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
                    Concluir sessões confirmadas que já passaram
                    <span className="block text-xs text-muted-foreground">
                      Confirmada com horário no passado vira &quot;realizada&quot; (sem mensagem).
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.autoExpireEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, autoExpireEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Expirar pacote vencido automaticamente
                    <span className="block text-xs text-muted-foreground">
                      Pacote ativo com validade vencida vira &quot;expirado&quot; (sem mensagem).
                    </span>
                  </span>
                </label>
                <div className="w-64">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Validade do pacote na ativação (dias)
                  </label>
                  <input
                    type="number"
                    min={7}
                    max={1095}
                    value={form.packageValidityDays}
                    placeholder="vazio = sem validade automática"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, packageValidityDays: e.target.value })
                    }
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.renewalEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, renewalEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Régua de renovação de pacote
                    <span className="block text-xs text-muted-foreground">
                      Convite pra quem esgotou o pacote há N dias e aviso de pacote perto de vencer
                      (1 toque por pacote). Desligada por padrão — ligar pode disparar pra base toda
                      de uma vez.
                    </span>
                  </span>
                </label>
                <div className="grid grid-cols-2 gap-4 sm:max-w-md">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Dias após esgotar até o convite
                    </label>
                    <input
                      type="number"
                      min={7}
                      max={365}
                      value={form.renewalDays}
                      onChange={(e) => setForm((f) => f && { ...f, renewalDays: e.target.value })}
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Aviso de vencimento (dias antes)
                    </label>
                    <input
                      type="number"
                      min={1}
                      max={60}
                      value={form.expiryWarningDays}
                      onChange={(e) =>
                        setForm((f) => f && { ...f, expiryWarningDays: e.target.value })
                      }
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
