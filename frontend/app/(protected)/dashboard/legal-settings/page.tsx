'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getConfig, updateConfig } from '@/lib/api/legal/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  reviewLink: string
  postClosureEnabled: boolean
  deadlineReminderEnabled: boolean
}

/**
 * Configurações do escritório (onda Legal 1): link de avaliação + toggles do pós-encerramento
 * (backlog #3) e do lembrete de prazos (backlog #1).
 */
export default function LegalSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['legal-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    reviewLink: d.reviewLink ?? '',
    postClosureEnabled: d.postClosureEnabled,
    deadlineReminderEnabled: d.deadlineReminderEnabled,
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        reviewLink: form.reviewLink.trim() || null,
        postClosureEnabled: form.postClosureEnabled,
        deadlineReminderEnabled: form.deadlineReminderEnabled,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['legal-config'] })
      setError(null)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    },
    onError: () => setError('Erro ao salvar as configurações.'),
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Configurações do escritório"
        description="Automações de relacionamento com o cliente."
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
            <Section title="Automações">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.deadlineReminderEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, deadlineReminderEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Lembrete de prazos e audiências ao cliente
                    <span className="block text-xs text-muted-foreground">
                      3 dias e 1 dia antes do compromisso, o cliente vinculado recebe um aviso com
                      data, hora e local — nunca conteúdo do processo.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.postClosureEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, postClosureEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Mensagem de pós-encerramento
                    <span className="block text-xs text-muted-foreground">
                      Ao encerrar um processo, o cliente recebe um agradecimento com convite de
                      avaliação e indicação.
                    </span>
                  </span>
                </label>
              </div>
            </Section>

            <Section title="Avaliação">
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Link de avaliação (Google, por exemplo)
                </label>
                <input
                  type="url"
                  value={form.reviewLink}
                  onChange={(e) => setForm((f) => f && { ...f, reviewLink: e.target.value })}
                  placeholder="https://g.page/r/…"
                  className="w-full max-w-lg rounded-md border border-border bg-background px-3 py-2 text-sm"
                />
                <p className="mt-1 text-xs text-muted-foreground">
                  Entra na mensagem de pós-encerramento. Vazio = a mensagem sai sem link.
                </p>
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
