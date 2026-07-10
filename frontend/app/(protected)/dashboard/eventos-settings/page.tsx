'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/eventos/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  businessName: string
  notes: string
  autoCompleteEnabled: boolean
  postEventEnabled: boolean
  reviewLink: string
  followUpEnabled: boolean
  followUpDays: string
}

/**
 * Configurações do EventosBot (camada 8.2): nome do espaço/buffet + notas. SEM horário/slot — a
 * proposta é order-based, não agendada por horário.
 */
export default function EventosSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['eventos-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    businessName: d.businessName ?? '',
    notes: d.notes ?? '',
    autoCompleteEnabled: d.autoCompleteEnabled ?? true,
    postEventEnabled: d.postEventEnabled ?? true,
    reviewLink: d.reviewLink ?? '',
    followUpEnabled: d.followUpEnabled ?? true,
    followUpDays: String(d.followUpDays ?? 3),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        businessName: form.businessName || null,
        notes: form.notes || null,
        autoCompleteEnabled: form.autoCompleteEnabled,
        postEventEnabled: form.postEventEnabled,
        reviewLink: form.reviewLink.trim() || null,
        followUpEnabled: form.followUpEnabled,
        followUpDays: Math.min(60, Math.max(1, Math.round(Number(form.followUpDays) || 3))),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['eventos-config'] })
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
        description="Nome do espaço e observações gerais (informativo)."
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
            <Section title="Identidade do espaço">
              <div className="space-y-4">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Nome do espaço/buffet
                  </label>
                  <input
                    value={form.businessName}
                    onChange={(e) => setForm((f) => f && { ...f, businessName: e.target.value })}
                    placeholder="Espaço Jardim, Buffet Encanto…"
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

            <p className="text-xs text-muted-foreground">
              A casa de festas trabalha por <strong>proposta</strong> (orçamento + cronograma), não
              por agendamento de horário — por isso não há configuração de horário aqui.
            </p>

            <Section title="Automações">
              <div className="space-y-4">
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
                    Marcar como realizada após a data do evento
                    <span className="block text-xs text-muted-foreground">
                      Proposta fechada com data passada vira &quot;realizada&quot; sozinha — e
                      dispara o pós-venda.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.postEventEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, postEventEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Mensagem de pós-evento (agradecimento + avaliação + indicação)
                    <span className="block text-xs text-muted-foreground">
                      Sai quando o evento vira &quot;realizada&quot;.
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
                    checked={form.followUpEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, followUpEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Follow-up de orçamento parado
                    <span className="block text-xs text-muted-foreground">
                      Orçamento enviado sem resposta há N dias recebe um toque gentil (1 por
                      episódio).
                    </span>
                  </span>
                </label>
                <div className="w-44">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Dias parado até o follow-up
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={60}
                    value={form.followUpDays}
                    onChange={(e) => setForm((f) => f && { ...f, followUpDays: e.target.value })}
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
