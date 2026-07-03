'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/barbearia/config'

type FormState = {
  opensAt: string; closesAt: string; slotMinutes: number; queueEnabled: boolean
  reminderEnabled: boolean; autoCompleteEnabled: boolean; upsellEnabled: boolean
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações da barbearia (camada 8.1): horário de funcionamento, granularidade dos slots e
 * se a FILA de walk-in está ligada. Mudanças afetam agendamentos FUTUROS.
 */
export default function BarberSettingsPage() {
  const qc = useQueryClient()
  const [form, setForm] = useState<FormState | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['barber-config'],
    queryFn: () => getConfig(),
  })

  useEffect(() => {
    if (data) {
      setForm({
        opensAt: hhmm(data.opensAt), closesAt: hhmm(data.closesAt),
        slotMinutes: data.slotMinutes, queueEnabled: data.queueEnabled,
        reminderEnabled: data.reminderEnabled ?? true,
        autoCompleteEnabled: data.autoCompleteEnabled ?? true,
        upsellEnabled: data.upsellEnabled ?? false,
      })
    }
  }, [data])

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig(form)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['barber-config'] })
      setError(null); setSaved(true); setTimeout(() => setSaved(false), 2500)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_hours') {
        setError('O horário de abertura precisa ser anterior ao de fechamento.')
      } else if (e instanceof ApiError && e.reason === 'invalid_slot') {
        setError('A granularidade do slot precisa ser maior que zero.')
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
        title="Configurações da barbearia"
        description="Horário de funcionamento, granularidade dos slots e a fila de walk-in."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as configurações.</p>
      ) : isPending || !form ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <Card>
          <form className="space-y-6" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
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

            <Section title="Slots">
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Granularidade do slot (minutos)
                </label>
                <input type="number" min={1} step={5} value={form.slotMinutes}
                  onChange={(e) => setForm((f) => f && { ...f, slotMinutes: Number(e.target.value) })}
                  className="w-full max-w-xs rounded-md border border-border bg-background px-3 py-2 text-sm" />
                <p className="mt-1 text-xs text-muted-foreground">
                  Define os horários livres que a IA enxerga (ex.: 15 = de 15 em 15 minutos).
                </p>
              </div>
            </Section>

            <Section title="Fila de walk-in">
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={form.queueEnabled}
                  onChange={(e) => setForm((f) => f && { ...f, queueEnabled: e.target.checked })} />
                Permitir entrar na fila sem hora marcada (walk-in)
              </label>
              <p className="mt-1 text-xs text-muted-foreground">
                Desligado: a IA só oferece marcar horário; quem manda mensagem não entra na fila.
              </p>
            </Section>

            <Section title="Automação (onda 1 do backlog)">
              <div className="space-y-3">
                <label className="flex items-start gap-2 text-sm">
                  <input type="checkbox" checked={form.reminderEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, reminderEnabled: e.target.checked })} />
                  <span>
                    Lembrete de confirmação nas 24h antes do horário
                    <span className="block text-xs text-muted-foreground">
                      "Confirma seu corte amanhã 15h? Responda SIM ou CANCELAR" — a resposta do cliente
                      confirma ou libera o horário automaticamente.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input type="checkbox" checked={form.autoCompleteEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, autoCompleteEnabled: e.target.checked })} />
                  <span>
                    Auto-transição de status
                    <span className="block text-xs text-muted-foreground">
                      Confirmado com horário passado vira "realizado" (alimenta fidelidade e relatório);
                      fila de dias anteriores expira sozinha.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input type="checkbox" checked={form.upsellEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, upsellEnabled: e.target.checked })} />
                  <span>
                    Upsell da IA (uma sugestão por conversa)
                    <span className="block text-xs text-muted-foreground">
                      No fechamento do agendamento, a IA pode sugerir UMA vez um serviço complementar do
                      catálogo (barba, sobrancelha…), sem insistir. Desligado = sem sugestão.
                    </span>
                  </span>
                </label>
              </div>
            </Section>

            <p className="text-xs text-muted-foreground">
              Mudanças afetam apenas agendamentos <strong>futuros</strong>.
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
