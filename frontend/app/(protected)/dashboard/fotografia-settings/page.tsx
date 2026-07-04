'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { getConfig, updateConfig } from '@/lib/api/fotografia/config'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  opensAt: string
  closesAt: string
  slotMinutes: number
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
  autoDeliverEnabled: boolean
  postDeliveryUpsellEnabled: boolean
  cancellationPolicyHours: string
}

function hhmm(t: string): string {
  return t?.slice(0, 5) ?? ''
}

/**
 * Configurações do estúdio de fotografia (camada 8.16): horário de funcionamento + granularidade do
 * slot de agenda. A duração de cada sessão vem do PACOTE (não daqui). Mudanças afetam sessões
 * FUTURAS.
 */
export default function FotografiaSettingsPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['fotografia-config'],
    queryFn: () => getConfig(),
  })

  const [form, setForm] = useSyncedForm(data, (d): FormState => ({
    opensAt: hhmm(d.opensAt),
    closesAt: hhmm(d.closesAt),
    slotMinutes: d.slotMinutes,
    reminderEnabled: d.reminderEnabled ?? true,
    autoCompleteEnabled: d.autoCompleteEnabled ?? true,
    autoDeliverEnabled: d.autoDeliverEnabled ?? true,
    postDeliveryUpsellEnabled: d.postDeliveryUpsellEnabled ?? true,
    cancellationPolicyHours:
      d.cancellationPolicyHours == null ? '' : String(d.cancellationPolicyHours),
  }))

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateConfig({
        ...form,
        cancellationPolicyHours: form.cancellationPolicyHours.trim()
          ? Math.min(720, Math.max(1, Math.round(Number(form.cancellationPolicyHours) || 1)))
          : null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fotografia-config'] })
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
        title="Configurações do estúdio"
        description="Horário de funcionamento e granularidade do slot da agenda. A duração de cada sessão vem do pacote."
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

            <Section title="Slot da agenda">
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Granularidade do slot (minutos)
                </label>
                <input
                  type="number"
                  min={5}
                  step={5}
                  value={form.slotMinutes}
                  onChange={(e) =>
                    setForm((f) => f && { ...f, slotMinutes: Number(e.target.value) })
                  }
                  className="w-full max-w-xs rounded-md border border-border bg-background px-3 py-2 text-sm"
                />
              </div>
            </Section>

            <p className="text-xs text-muted-foreground">
              Mudanças afetam apenas sessões <strong>futuras</strong>.
            </p>

            <Section title="Automações">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.reminderEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, reminderEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Lembrete de sessão 2 dias e 1 dia antes
                    <span className="block text-xs text-muted-foreground">
                      Pede confirmação — a resposta cai na conversa e a IA confirma ou cancela.
                    </span>
                  </span>
                </label>
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
                    Concluir automaticamente sessões confirmadas que já passaram
                    <span className="block text-xs text-muted-foreground">
                      Confirmada com horário no passado vira &quot;realizada&quot; (sem mensagem).
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.autoDeliverEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, autoDeliverEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Entregar o material automaticamente no prazo
                    <span className="block text-xs text-muted-foreground">
                      No dia prometido, sessão realizada com link gravado é entregue sozinha (o link
                      sai exatamente como você gravou). Sem link, o card fica destacado.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.postDeliveryUpsellEnabled}
                    className="mt-0.5"
                    onChange={(e) =>
                      setForm((f) => f && { ...f, postDeliveryUpsellEnabled: e.target.checked })
                    }
                  />
                  <span>
                    Convite pós-entrega (fotos extras, álbum)
                    <span className="block text-xs text-muted-foreground">
                      Logo após a entrega — o momento mais quente de compra. Sem valores na
                      mensagem.
                    </span>
                  </span>
                </label>
              </div>
            </Section>

            <Section title="Política de cancelamento">
              <div className="w-64">
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Cancelamento grátis até (horas antes)
                </label>
                <input
                  type="number"
                  min={1}
                  max={720}
                  value={form.cancellationPolicyHours}
                  placeholder="ex.: 72 — vazio = sem política"
                  onChange={(e) =>
                    setForm((f) => f && { ...f, cancellationPolicyHours: e.target.value })
                  }
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                />
                <p className="mt-1 text-xs text-muted-foreground">
                  A IA comunica a política ao agendar. Retenção de sinal fica pra quando houver
                  pagamento online.
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
