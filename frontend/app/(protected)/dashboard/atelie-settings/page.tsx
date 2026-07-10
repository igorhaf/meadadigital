'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/atelie/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  businessName: string
  notes: string
  fittingReminderEnabled: boolean
  postDeliveryEnabled: boolean
  reviewLink: string
  reactivationEnabled: boolean
  reactivationDays: string
}

/**
 * Configurações do AtelieBot (camada 8.14): nome do ateliê + notas. SEM horário/slot — a proposta é
 * order-based (orçamento + provas/ajustes), não agendada por horário.
 */
export default function AtelieSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['atelie-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    businessName: d.businessName ?? '',
    notes: d.notes ?? '',
    fittingReminderEnabled: d.fittingReminderEnabled ?? true,
    postDeliveryEnabled: d.postDeliveryEnabled ?? true,
    reviewLink: d.reviewLink ?? '',
    reactivationEnabled: d.reactivationEnabled ?? false,
    reactivationDays: String(d.reactivationDays ?? 90),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        businessName: form.businessName || null,
        notes: form.notes || null,
        fittingReminderEnabled: form.fittingReminderEnabled,
        postDeliveryEnabled: form.postDeliveryEnabled,
        reviewLink: form.reviewLink.trim() || null,
        reactivationEnabled: form.reactivationEnabled,
        reactivationDays: Math.min(
          730,
          Math.max(7, Math.round(Number(form.reactivationDays) || 90)),
        ),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['atelie-config'] })
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
        description="Nome do ateliê e observações gerais (informativo)."
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
            <Section title="Identidade do ateliê">
              <div className="space-y-4">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Nome do ateliê
                  </label>
                  <input
                    value={form.businessName}
                    onChange={(e) => setForm((f) => f && { ...f, businessName: e.target.value })}
                    placeholder="Ateliê Agulha de Ouro, Studio Linha…"
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

            <Section title="Lembrete de prova/ajuste">
              <label className="flex items-start gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.fittingReminderEnabled}
                  className="mt-0.5"
                  onChange={(e) =>
                    setForm((f) => f && { ...f, fittingReminderEnabled: e.target.checked })
                  }
                />
                <span>
                  Lembrar o cliente pelo WhatsApp na <strong>véspera</strong> de cada prova/ajuste
                  com prazo
                  <span className="block text-xs text-muted-foreground">
                    Mensagem automática fixa (não passa pela IA), enviada 1x por prova/data.
                    Remarcar a prova para outra data reenvia o lembrete.
                  </span>
                </span>
              </label>
            </Section>

            <p className="text-xs text-muted-foreground">
              O ateliê trabalha por <strong>proposta</strong> (orçamento + provas/ajustes), não por
              agendamento de horário — por isso não há configuração de horário aqui.
            </p>

            <Section title="Pós-entrega e reativação">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.postDeliveryEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, postDeliveryEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Mensagem de pós-entrega (agradecimento + avaliação + indicação)
                    <span className="block text-xs text-muted-foreground">
                      Sai quando a proposta vira &quot;realizada&quot; (peça entregue).
                    </span>
                  </span>
                </label>
                <div className="max-w-lg">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Link de avaliação (Google, por exemplo)
                  </label>
                  <input
                    type="url"
                    value={form.reviewLink}
                    onChange={(e) => setForm((f) => f && { ...f, reviewLink: e.target.value })}
                    placeholder="https://g.page/r/…"
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.reactivationEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, reactivationEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Reativação de cliente inativo
                    <span className="block text-xs text-muted-foreground">
                      Convite gentil pra quem não encomenda há N dias (1 toque por ciclo). Desligado
                      por padrão — ligar pode disparar pra base toda de uma vez.
                    </span>
                  </span>
                </label>
                <div className="w-44">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Dias sem encomenda até o convite
                  </label>
                  <input
                    type="number"
                    min={7}
                    max={730}
                    value={form.reactivationDays}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, reactivationDays: e.target.value })
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
