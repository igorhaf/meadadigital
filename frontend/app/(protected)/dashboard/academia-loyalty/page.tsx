'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import {
  addLoyaltyPoints,
  getLoyaltyBalance,
  getLoyaltyConfig,
  updateLoyaltyConfig,
} from '@/lib/api/academia/loyalty'
import { listMemberships } from '@/lib/api/academia/memberships'
import { ApiError } from '@/lib/api/client'
import { useSyncedForm } from '@/lib/use-synced-form'

type FormState = {
  enabled: boolean
  pointsPerCheckin: string
  rewardThreshold: string // vazio = sem limiar
  rewardText: string
}

/**
 * Fidelidade por assiduidade do AcademiaBot: cada check-in credita pontos; ao atingir o limiar o
 * aluno ganha a recompensa configurada. Inclui consulta de saldo por aluno e crédito manual de pontos.
 */
export default function AcademiaLoyaltyPage() {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [contactId, setContactId] = useState('')
  const [creditPoints, setCreditPoints] = useState('')
  const [creditError, setCreditError] = useState<string | null>(null)

  const {
    data: config,
    isPending,
    isError,
  } = useQuery({
    queryKey: ['academia-loyalty-config'],
    queryFn: () => getLoyaltyConfig(),
  })

  const [form, setForm] = useSyncedForm(config, (d): FormState => ({
    enabled: d.enabled,
    pointsPerCheckin: String(d.pointsPerCheckin),
    rewardThreshold: d.rewardThreshold !== null ? String(d.rewardThreshold) : '',
    rewardText: d.rewardText ?? '',
  }))

  const { data: membershipsData } = useQuery({
    queryKey: ['academia-memberships', 'loyalty-select'],
    queryFn: () => listMemberships({ status: 'ativa', pageSize: 100 }),
  })

  const {
    data: balance,
    isPending: balancePending,
    isError: balanceError,
  } = useQuery({
    queryKey: ['academia-loyalty-balance', contactId],
    queryFn: () => getLoyaltyBalance(contactId),
    enabled: contactId !== '',
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!form) throw new Error('form não carregado')
      return updateLoyaltyConfig({
        enabled: form.enabled,
        pointsPerCheckin: Math.max(1, Math.round(Number(form.pointsPerCheckin || '1'))),
        rewardThreshold: form.rewardThreshold.trim()
          ? Math.max(1, Math.round(Number(form.rewardThreshold)))
          : null,
        rewardText: form.rewardText.trim() ? form.rewardText.trim() : null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['academia-loyalty-config'] })
      qc.invalidateQueries({ queryKey: ['academia-loyalty-balance'] })
      setError(null)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_config') {
        setError('Valores inválidos. Verifique os campos.')
      } else {
        setError('Erro ao salvar a fidelidade.')
      }
    },
  })

  const creditMutation = useMutation({
    mutationFn: () => addLoyaltyPoints(contactId, Math.round(Number(creditPoints || '0'))),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['academia-loyalty-balance', contactId] })
      setCreditPoints('')
      setCreditError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_points') {
        setCreditError('Quantidade de pontos inválida.')
      } else if (e instanceof ApiError && e.reason === 'contact_not_found') {
        setCreditError('Contato não encontrado.')
      } else {
        setCreditError('Erro ao creditar os pontos.')
      }
    },
  })

  // Só matrículas ativas COM contato vinculado (o saldo é por contactId).
  const students = (membershipsData?.items ?? []).filter((m) => m.contactId != null)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Fidelidade"
        description="Pontos por check-in: quem frequenta acumula pontos e ganha a recompensa ao atingir o limiar."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar a fidelidade.</p>
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
            <Section title="Programa de fidelidade">
              <label className="flex items-center gap-2 text-sm text-muted-foreground">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(e) => setForm((f) => f && { ...f, enabled: e.target.checked })}
                />
                Ativar fidelidade
              </label>

              <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Pontos por check-in
                  </label>
                  <input
                    type="number"
                    min="1"
                    step="1"
                    value={form.pointsPerCheckin}
                    onChange={(e) =>
                      setForm((f) => f && { ...f, pointsPerCheckin: e.target.value })
                    }
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Limiar da recompensa (opcional)
                  </label>
                  <input
                    type="number"
                    min="1"
                    step="1"
                    value={form.rewardThreshold}
                    placeholder="ex.: 20"
                    onChange={(e) => setForm((f) => f && { ...f, rewardThreshold: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">
                    Recompensa
                  </label>
                  <input
                    value={form.rewardText}
                    maxLength={200}
                    placeholder="Uma aula grátis"
                    onChange={(e) => setForm((f) => f && { ...f, rewardText: e.target.value })}
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>

              <p className="mt-3 text-xs text-muted-foreground">
                Ex.: cada check-in vale {form.pointsPerCheckin || 'N'} ponto(s); ao juntar{' '}
                {form.rewardThreshold || 'o limiar de'} pontos, o aluno ganha{' '}
                {form.rewardText.trim() ? `"${form.rewardText.trim()}"` : 'a recompensa'}.
              </p>
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

      <Card>
        <Section
          title="Saldo do aluno"
          description="Consulte os pontos de um aluno com matrícula ativa e credite pontos manualmente."
        >
          <div className="max-w-sm">
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Aluno</label>
            <select
              value={contactId}
              onChange={(e) => {
                setContactId(e.target.value)
                setCreditError(null)
              }}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Selecione um aluno…</option>
              {students.map((m) => (
                <option key={m.id} value={m.contactId as string}>
                  {m.studentName}
                </option>
              ))}
            </select>
            {students.length === 0 && (
              <p className="mt-2 text-xs text-muted-foreground">
                Nenhuma matrícula ativa com contato vinculado.
              </p>
            )}
          </div>

          {contactId &&
            (balanceError ? (
              <p className="text-sm text-destructive">Erro ao carregar o saldo.</p>
            ) : balancePending || !balance ? (
              <p className="text-sm text-muted-foreground">Carregando saldo…</p>
            ) : (
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <span className="text-2xl font-semibold tabular-nums">{balance.points}</span>
                  <span className="text-sm text-muted-foreground">
                    ponto(s)
                    {balance.rewardThreshold !== null
                      ? ` — limiar: ${balance.rewardThreshold}`
                      : ' — sem limiar configurado'}
                  </span>
                  {balance.rewardReached && <Badge variant="success">Recompensa atingida</Badge>}
                </div>

                <form
                  className="flex items-end gap-2"
                  onSubmit={(e) => {
                    e.preventDefault()
                    creditMutation.mutate()
                  }}
                >
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Creditar pontos
                    </label>
                    <input
                      type="number"
                      min="1"
                      step="1"
                      value={creditPoints}
                      required
                      placeholder="ex.: 5"
                      onChange={(e) => setCreditPoints(e.target.value)}
                      className="w-32 rounded-md border border-border bg-background px-3 py-2 text-sm"
                    />
                  </div>
                  <Button type="submit" variant="outline" disabled={creditMutation.isPending}>
                    {creditMutation.isPending ? 'Creditando…' : 'Creditar'}
                  </Button>
                </form>
                {creditError && <p className="text-sm text-destructive">{creditError}</p>}
              </div>
            ))}
        </Section>
      </Card>
    </div>
  )
}
