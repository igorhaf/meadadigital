'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/concessionaria/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  businessName: string
  durationMinutes: number
  bufferMinutes: number
  opensAt: string
  closesAt: string
  notes: string
  followupEnabled: boolean
  followupDays: number
  testdriveReminderEnabled: boolean
  autoCompleteEnabled: boolean
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações da loja (camada 8.17): nome do negócio, duração do test-drive, intervalo e horário
 * de funcionamento. A duração é snapshotada em cada test-drive — mudanças afetam só os futuros.
 */
export default function ConcessionariaSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['concessionaria-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    businessName: d.businessName ?? '',
    durationMinutes: d.durationMinutes,
    bufferMinutes: d.bufferMinutes,
    opensAt: hhmm(d.opensAt),
    closesAt: hhmm(d.closesAt),
    notes: d.notes ?? '',
    followupEnabled: d.followupEnabled ?? true,
    followupDays: d.followupDays ?? 3,
    testdriveReminderEnabled: d.testdriveReminderEnabled ?? true,
    autoCompleteEnabled: d.autoCompleteEnabled ?? true,
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        businessName: form.businessName || null,
        durationMinutes: form.durationMinutes,
        bufferMinutes: form.bufferMinutes,
        opensAt: form.opensAt,
        closesAt: form.closesAt,
        notes: form.notes || null,
        followupEnabled: form.followupEnabled,
        followupDays: Math.max(1, Math.round(form.followupDays || 3)),
        testdriveReminderEnabled: form.testdriveReminderEnabled,
        autoCompleteEnabled: form.autoCompleteEnabled,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['concessionaria-config'] })
      setError(null); setSaved(true)
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
        title="Configurações da loja"
        description="Nome do negócio, duração do test-drive, intervalo e horário de funcionamento."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as configurações.</p>
      ) : isPending || !form ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <Card>
          <form className="space-y-6" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
            <Section title="Identificação">
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome do negócio</label>
                <input value={form.businessName}
                  onChange={(e) => setForm((f) => f && { ...f, businessName: e.target.value })}
                  placeholder="Ex.: Auto Center Modelo"
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
              </div>
            </Section>

            <Section title="Test-drive">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Duração do test-drive (minutos)
                  </label>
                  <input type="number" min={15} max={240} step={15} value={form.durationMinutes}
                    onChange={(e) => setForm((f) => f && { ...f, durationMinutes: Number(e.target.value) })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Intervalo entre test-drives (minutos)
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

            <Section title="Observações">
              <textarea value={form.notes}
                onChange={(e) => setForm((f) => f && { ...f, notes: e.target.value })}
                rows={3} placeholder="Notas internas da loja"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </Section>

            <p className="text-xs text-muted-foreground">
              Mudanças afetam apenas test-drives <strong>futuros</strong> — os já agendados mantêm a
              duração do momento em que foram criados.
            </p>

            <Section title="Automações (onda 1 do backlog)">
              <div className="space-y-3 text-sm">
                <label className="flex items-start gap-2">
                  <input type="checkbox" checked={form.testdriveReminderEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, testdriveReminderEnabled: e.target.checked })} />
                  <span>
                    Lembrar o cliente do <strong>test-drive</strong> nas 24h anteriores (confirma? SIM/CANCELAR)
                    <span className="block text-xs text-muted-foreground">A resposta confirma ou libera o horário automaticamente.</span>
                  </span>
                </label>
                <label className="flex items-start gap-2">
                  <input type="checkbox" checked={form.followupEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, followupEnabled: e.target.checked })} />
                  <span>
                    Follow-up automático de <strong>lead parado</strong> após
                    <input type="number" min="1" value={form.followupDays}
                      onChange={(e) => setForm((f) => f && { ...f, followupDays: Number(e.target.value) })}
                      className="mx-1 w-14 rounded-md border border-border bg-background px-1 py-0.5 text-sm" />
                    dia(s) sem movimento
                    <span className="block text-xs text-muted-foreground">Reengaja sem fechar preço — o vendedor assume a conversa.</span>
                  </span>
                </label>
                <label className="flex items-start gap-2">
                  <input type="checkbox" checked={form.autoCompleteEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, autoCompleteEnabled: e.target.checked })} />
                  <span>
                    Marcar test-drive <strong>confirmado</strong> como <strong>realizado</strong> após o horário
                    <span className="block text-xs text-muted-foreground">Automático e silencioso (2h de tolerância).</span>
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
