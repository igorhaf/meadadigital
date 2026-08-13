'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { PageSkeleton } from '@/components/ui/skeleton'
import { getMe } from '@/lib/api/me'
import {
  getMyBusinessHours,
  saveMyBusinessHours,
  type BusinessHour,
} from '@/lib/supabase/business_hours'
import { useOnSync } from '@/lib/use-synced-form'

const WEEKDAYS = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado']

/** Constrói as 7 linhas fixas a partir do que veio do banco (linhas faltantes = fechado). */
function buildSevenRows(existing: BusinessHour[]): BusinessHour[] {
  return WEEKDAYS.map((_, weekday) => {
    const found = existing.find((r) => r.weekday === weekday)
    return found ?? { weekday, opensAt: null, closesAt: null, closed: true }
  })
}

/**
 * Horários de funcionamento do tenant (SDK + RLS). 7 linhas fixas (Dom→Sáb); cada uma:
 * checkbox "Fechado" + abre/fecha. Salva o conjunto inteiro (delete-then-insert). A
 * coerência (closed ⇒ sem horas; aberto ⇒ horas presentes e distintas) é validada antes
 * de salvar, espelhando o CHECK do banco.
 */
export default function BusinessHoursPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [rows, setRows] = useState<BusinessHour[]>([])
  const [validationError, setValidationError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError } = useQuery({
    queryKey: ['my-business-hours'],
    queryFn: getMyBusinessHours,
    enabled: isTenant,
  })

  // Sincroniza o estado editável local quando os dados chegam.
  useOnSync(data, (d) => setRows(buildSevenRows(d)))

  const mutation = useMutation({
    mutationFn: (toSave: BusinessHour[]) => saveMyBusinessHours(me!.companyId!, toSave),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-business-hours'] })
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    },
    onError: (err) => console.error('saveMyBusinessHours failed:', err),
  })

  function updateRow(weekday: number, patch: Partial<BusinessHour>) {
    setRows((prev) => prev.map((r) => (r.weekday === weekday ? { ...r, ...patch } : r)))
    setSaved(false)
  }

  function onSave() {
    setValidationError(null)
    // Valida coerência (espelha chk_business_hours_shape) antes de chamar o banco.
    for (const r of rows) {
      if (!r.closed) {
        if (!r.opensAt || !r.closesAt) {
          setValidationError(
            `${WEEKDAYS[r.weekday]}: preencha abertura e fechamento (ou marque como fechado).`,
          )
          return
        }
        if (r.opensAt === r.closesAt) {
          setValidationError(`${WEEKDAYS[r.weekday]}: abertura e fechamento não podem ser iguais.`)
          return
        }
      }
    }
    mutation.mutate(rows)
  }

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
  }

  if (isError) {
    return (
      <div className="space-y-4">
        <PageHeader title="Horários" />
        <p className="text-sm text-destructive">Erro ao carregar horários.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  if (isPending) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Horários"
        description="Defina o horário de atendimento de cada dia da semana."
      />

      <Card className="space-y-2 p-4">
        {rows.map((r) => (
          <div
            key={r.weekday}
            className="flex flex-wrap items-center gap-3 border-b border-border pb-2 last:border-0"
          >
            <span className="w-24 text-sm font-medium">{WEEKDAYS[r.weekday]}</span>
            <label className="flex items-center gap-1 text-sm text-muted-foreground">
              <input
                type="checkbox"
                checked={r.closed}
                onChange={(e) => updateRow(r.weekday, { closed: e.target.checked })}
              />
              Fechado
            </label>
            <input
              type="time"
              value={r.opensAt ?? ''}
              disabled={r.closed}
              onChange={(e) => updateRow(r.weekday, { opensAt: e.target.value || null })}
              className="rounded-md border border-border px-2 py-1 text-sm disabled:opacity-40"
            />
            <span className="text-sm text-muted-foreground">até</span>
            <input
              type="time"
              value={r.closesAt ?? ''}
              disabled={r.closed}
              onChange={(e) => updateRow(r.weekday, { closesAt: e.target.value || null })}
              className="rounded-md border border-border px-2 py-1 text-sm disabled:opacity-40"
            />
          </div>
        ))}
      </Card>

      {validationError && <p className="text-sm text-destructive">{validationError}</p>}
      {mutation.isError && (
        <p className="text-sm text-destructive">Erro ao salvar. Recarregue e tente de novo.</p>
      )}
      {saved && <p className="text-sm text-green-600">Salvo!</p>}

      <div>
        <Button onClick={onSave} disabled={mutation.isPending || !me?.companyId}>
          {mutation.isPending ? 'Salvando…' : 'Salvar'}
        </Button>
      </div>
    </div>
  )
}
