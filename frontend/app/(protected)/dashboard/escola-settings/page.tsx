'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/escola/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = { businessName: string; opensAt: string; closesAt: string; notes: string }

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
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        businessName: form.businessName || null,
        opensAt: form.opensAt,
        closesAt: form.closesAt,
        notes: form.notes || null,
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
