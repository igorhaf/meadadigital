'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/casamento/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  businessName: string
  notes: string
  checklistReminderEnabled: boolean
  paymentReminderEnabled: boolean
  autoCompleteEnabled: boolean
  anniversaryEnabled: boolean
}

/**
 * Configurações do CasamentoBot (camada 8.7): nome da assessoria + notas. SEM horário/slot — a
 * proposta é order-based, não agendada por horário.
 */
export default function CasamentoSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['casamento-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    businessName: d.businessName ?? '',
    notes: d.notes ?? '',
    checklistReminderEnabled: d.checklistReminderEnabled ?? true,
    paymentReminderEnabled: d.paymentReminderEnabled ?? true,
    autoCompleteEnabled: d.autoCompleteEnabled ?? true,
    anniversaryEnabled: d.anniversaryEnabled ?? true,
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        businessName: form.businessName || null,
        notes: form.notes || null,
        checklistReminderEnabled: form.checklistReminderEnabled,
        paymentReminderEnabled: form.paymentReminderEnabled,
        autoCompleteEnabled: form.autoCompleteEnabled,
        anniversaryEnabled: form.anniversaryEnabled,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-config'] })
      setError(null); setSaved(true); setTimeout(() => setSaved(false), 2500)
    },
    onError: () => setError('Erro ao salvar as configurações.'),
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Configurações"
        description="Nome da assessoria e observações gerais (informativo)."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as configurações.</p>
      ) : isPending || !form ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <Card>
          <form className="space-y-6" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
            <Section title="Identidade da assessoria">
              <div className="space-y-4">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome da assessoria</label>
                  <input value={form.businessName}
                    onChange={(e) => setForm((f) => f && { ...f, businessName: e.target.value })}
                    placeholder="Assessoria Encanto, Cerimonial Aurora…"
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Observações</label>
                  <textarea value={form.notes}
                    onChange={(e) => setForm((f) => f && { ...f, notes: e.target.value })}
                    rows={3} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
              </div>
            </Section>

            <Section title="Automações (onda 1 do backlog)">
              <div className="space-y-3 text-sm">
                <label className="flex items-start gap-2">
                  <input type="checkbox" checked={form.checklistReminderEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, checklistReminderEnabled: e.target.checked })} />
                  <span>
                    Lembrar o casal <strong>3 dias antes</strong> do prazo de cada tarefa do checklist
                    <span className="block text-xs text-muted-foreground">Mensagem fixa (não passa pela IA), 1x por tarefa/prazo.</span>
                  </span>
                </label>
                <label className="flex items-start gap-2">
                  <input type="checkbox" checked={form.paymentReminderEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, paymentReminderEnabled: e.target.checked })} />
                  <span>
                    Lembrar o casal <strong>3 dias antes</strong> do vencimento de cada parcela/sinal
                    <span className="block text-xs text-muted-foreground">Só parcelas em aberto do plano de pagamento.</span>
                  </span>
                </label>
                <label className="flex items-start gap-2">
                  <input type="checkbox" checked={form.autoCompleteEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, autoCompleteEnabled: e.target.checked })} />
                  <span>
                    Marcar proposta <strong>fechada</strong> como <strong>realizada</strong> após a data do casamento
                    <span className="block text-xs text-muted-foreground">Automático e silencioso (ninguém é notificado).</span>
                  </span>
                </label>
                <label className="flex items-start gap-2">
                  <input type="checkbox" checked={form.anniversaryEnabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, anniversaryEnabled: e.target.checked })} />
                  <span>
                    Parabenizar o casal no <strong>aniversário de casamento</strong> (1x por ano)
                    <span className="block text-xs text-muted-foreground">Pós-venda de longo prazo — mensagem calorosa, sem oferta agressiva.</span>
                  </span>
                </label>
              </div>
            </Section>

            <p className="text-xs text-muted-foreground">
              A assessoria trabalha por <strong>proposta</strong> (orçamento + cronograma + checklist),
              não por agendamento de horário — por isso não há configuração de horário aqui.
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
