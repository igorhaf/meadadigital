'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/viagens/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  businessName: string
  notes: string
  tripReminderEnabled: boolean
  quoteFollowupEnabled: boolean
  quoteFollowupDays: string
}

/**
 * Configurações do ViagensBot (camada 8.18): nome da agência + notas. SEM horário/slot — a proposta
 * é order-based, não agendada por horário.
 */
export default function ViagensSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['viagens-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    businessName: d.businessName ?? '',
    notes: d.notes ?? '',
    tripReminderEnabled: d.tripReminderEnabled ?? true,
    quoteFollowupEnabled: d.quoteFollowupEnabled ?? true,
    quoteFollowupDays: String(d.quoteFollowupDays ?? 2),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        businessName: form.businessName || null,
        notes: form.notes || null,
        tripReminderEnabled: form.tripReminderEnabled,
        quoteFollowupEnabled: form.quoteFollowupEnabled,
        quoteFollowupDays: Math.max(1, Math.min(30, Number(form.quoteFollowupDays) || 2)),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['viagens-config'] })
      setError(null)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    },
    onError: () => setError('Erro ao salvar as configurações.'),
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Configurações"
        description="Nome da agência e observações gerais (informativo)."
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
            <Section title="Identidade da agência">
              <div className="space-y-4">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Nome da agência
                  </label>
                  <input
                    value={form.businessName}
                    onChange={(e) => setForm((f) => f && { ...f, businessName: e.target.value })}
                    placeholder="Agência Horizonte, Viaje Bem…"
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Observações
                  </label>
                  <textarea
                    value={form.notes}
                    onChange={(e) => setForm((f) => f && { ...f, notes: e.target.value })}
                    rows={3}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>
            </Section>

            <Section title="Automações (lembretes e follow-up)">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.tripReminderEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, tripReminderEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Lembretes de viagem pelo WhatsApp (propostas <strong>fechadas</strong> com
                    datas)
                    <span className="block text-xs text-muted-foreground">
                      D-7 checklist de documentos/bagagem · dia do embarque &quot;boa viagem&quot; ·
                      2 dias após a volta pedido de avaliação/indicação. Mensagens fixas (não passam
                      pela IA), 1x por proposta/data — remarcar a viagem reenvia.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.quoteFollowupEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, quoteFollowupEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Follow-up automático de cotação sem resposta
                    <span className="block text-xs text-muted-foreground">
                      Cutuca com gentileza a proposta <strong>orçada</strong> parada, 1x por
                      orçamento (re-orçar rearma).
                    </span>
                  </span>
                </label>
                <div className="w-40">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Dias até o follow-up
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={30}
                    value={form.quoteFollowupDays}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, quoteFollowupDays: e.target.value })
                    }
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>
            </Section>

            <p className="text-xs text-muted-foreground">
              A agência trabalha por <strong>proposta</strong> (cotação + itinerário), não por
              agendamento de horário — por isso não há configuração de horário aqui.
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
