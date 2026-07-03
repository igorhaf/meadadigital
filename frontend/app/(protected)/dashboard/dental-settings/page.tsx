'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/dental/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  durationMinutes: number
  bufferMinutes: number
  opensAt: string
  closesAt: string
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações do consultório (camada 7.4): duração da consulta, buffer e horário de
 * funcionamento. Mudanças afetam consultas FUTURAS (a duração é snapshotada em cada consulta).
 */
export default function DentalSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['dental-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    durationMinutes: d.durationMinutes,
    bufferMinutes: d.bufferMinutes,
    opensAt: hhmm(d.opensAt),
    closesAt: hhmm(d.closesAt),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig(form)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['dental-config'] })
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
        description="Duração da consulta, intervalo entre consultas e horário de funcionamento."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as configurações.</p>
      ) : isPending || !form ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <Card>
          <form className="space-y-6" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
            <Section title="Duração">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Duração da consulta (minutos)
                  </label>
                  <input type="number" min={15} max={240} step={15} value={form.durationMinutes}
                    onChange={(e) => setForm((f) => f && { ...f, durationMinutes: Number(e.target.value) })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Intervalo entre consultas (minutos)
                  </label>
                  <input type="number" min={0} step={5} value={form.bufferMinutes}
                    onChange={(e) => setForm((f) => f && { ...f, bufferMinutes: Number(e.target.value) })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
              </div>
            </Section>

            <Section title="Horário de funcionamento">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Abre às</label>
                  <input type="time" value={form.opensAt}
                    onChange={(e) => setForm((f) => f && { ...f, opensAt: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Fecha às</label>
                  <input type="time" value={form.closesAt}
                    onChange={(e) => setForm((f) => f && { ...f, closesAt: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
              </div>
            </Section>

            <p className="text-xs text-muted-foreground">
              Mudanças afetam apenas consultas <strong>futuras</strong> — consultas já confirmadas
              mantêm a duração do momento em que foram criadas.
            </p>

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
