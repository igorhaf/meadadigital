'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { getLoyalty, updateLoyalty } from '@/lib/api/barbearia/loyalty'

type FormState = { enabled: boolean; thresholdCuts: string }

/**
 * Fidelidade do BarbeariaBot (onda 1, backlog #3): "a cada N cortes realizados, o próximo é GRÁTIS".
 * O desconto é aplicado AUTOMATICAMENTE pelo backend na criação do agendamento; a IA só informa o
 * saldo na conversa. O cartão-fidelidade de cada cliente é derivado do histórico de realizados.
 */
export default function BarberLoyaltyPage() {
  const qc = useQueryClient()
  const [form, setForm] = useState<FormState | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data, isPending, isError } = useQuery({
    queryKey: ['barber-loyalty'],
    queryFn: () => getLoyalty(),
  })

  useEffect(() => {
    if (data) {
      setForm({ enabled: data.enabled, thresholdCuts: String(data.thresholdCuts) })
    }
  }, [data])

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateLoyalty({
        enabled: form.enabled,
        thresholdCuts: Math.max(1, Math.round(Number(form.thresholdCuts) || 1)),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['barber-loyalty'] })
      setError(null); setSaved(true); setTimeout(() => setSaved(false), 2500)
    },
    onError: () => setError('Erro ao salvar a fidelidade.'),
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Fidelidade"
        description="A cada N cortes realizados, o próximo sai grátis — aplicado automaticamente no agendamento."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar a fidelidade.</p>
      ) : isPending || !form ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <Card>
          <form className="space-y-6" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
            <Section title="Cartão-fidelidade digital">
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input type="checkbox" checked={form.enabled} className="mt-0.5"
                    onChange={(e) => setForm((f) => f && { ...f, enabled: e.target.checked })} />
                  <span>
                    Ativar a fidelidade por contagem de cortes
                    <span className="block text-xs text-muted-foreground">
                      O backend conta os agendamentos <strong>realizados</strong> de cada cliente; ao
                      completar o ciclo, o próximo agendamento sai com 100% de desconto
                      (marcado como "grátis · fidelidade" na agenda). A IA informa o saldo na conversa.
                    </span>
                  </span>
                </label>
                <div className="w-40">
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Cortes por ciclo (N)</label>
                  <input type="number" min="1" step="1" value={form.thresholdCuts}
                    onChange={(e) => setForm((f) => f && { ...f, thresholdCuts: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
              </div>
            </Section>

            {error && <p className="text-sm text-destructive">{error}</p>}
            {saved && <p className="text-sm text-emerald-600">Fidelidade salva.</p>}

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
