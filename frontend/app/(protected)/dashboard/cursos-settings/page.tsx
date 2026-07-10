'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/cursos/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  opensAt: string
  closesAt: string
  notes: string
  nudgeEnabled: boolean
  nudgeDays: string
  certificateBaseUrl: string
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações do CursosBot (camada 8.20): horário de atendimento + observações.
 */
export default function CursosSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['cursos-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    opensAt: hhmm(d.opensAt),
    closesAt: hhmm(d.closesAt),
    notes: d.notes ?? '',
    nudgeEnabled: d.nudgeEnabled ?? true,
    nudgeDays: String(d.nudgeDays ?? 7),
    certificateBaseUrl: d.certificateBaseUrl ?? '',
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        opensAt: form.opensAt,
        closesAt: form.closesAt,
        notes: form.notes || null,
        nudgeEnabled: form.nudgeEnabled,
        nudgeDays: Math.min(90, Math.max(1, Math.round(Number(form.nudgeDays) || 7))),
        certificateBaseUrl: form.certificateBaseUrl.trim() || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cursos-config'] })
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
        title="Configurações dos cursos"
        description="Horário de atendimento e observações."
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
            <Section title="Horário de atendimento">
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
                placeholder="Informações gerais sobre os cursos…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </Section>

            <Section title="Automações e certificado">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.nudgeEnabled}
                    className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, nudgeEnabled: e.target.checked })}
                  />
                  <span>
                    Lembrete de próximo módulo (anti-abandono)
                    <span className="block text-xs text-muted-foreground">
                      Aluno parado há N dias no mesmo módulo recebe um toque motivador (1 por
                      episódio).
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
                    max={90}
                    value={form.nudgeDays}
                    onChange={(e) => setForm((f) => f && { ...f, nudgeDays: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <div className="max-w-lg">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    URL pública do certificado (base)
                  </label>
                  <input
                    type="url"
                    value={form.certificateBaseUrl}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, certificateBaseUrl: e.target.value })
                    }
                    placeholder="https://escola.meadadigital.com"
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                  <p className="mt-1 text-xs text-muted-foreground">
                    Vira o link do certificado enviado ao aluno na conclusão. Vazio = só o código.
                  </p>
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
