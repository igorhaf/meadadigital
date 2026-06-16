'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { PageSkeleton } from '@/components/ui/skeleton'
import {
  getAppointments,
  updateAppointmentStatus,
  type Appointment,
} from '@/lib/api/appointments'
import { getMe } from '@/lib/api/me'

const WEEKDAY_LABELS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb']
const STATUS_LABELS: Record<Appointment['status'], string> = {
  scheduled: 'Agendado',
  completed: 'Concluído',
  cancelled: 'Cancelado',
  no_show: 'Não compareceu',
}

/** Chave de dia local "YYYY-MM-DD" de um ISO (usa o fuso do navegador, suficiente p/ o MVP). */
function dayKey(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** Hora local "HH:MM" de um ISO. */
function timeLabel(iso: string): string {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

/**
 * Calendário de agendamentos do tenant (camada 5.19 #59). Vista MÊS: grade de dias com a
 * contagem de agendamentos por dia; clicar num dia lista os agendamentos daquele dia (contato +
 * hora + status) com um dropdown para mudar o status (concluído/cancelado/não compareceu).
 * Super-admin não usa: redireciona para /dashboard (guard copiado de availability/page.tsx).
 */
export default function CalendarPage() {
  const router = useRouter()
  const queryClient = useQueryClient()

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  // Mês exibido (1º dia do mês). Navegável por ‹ ›.
  const [monthStart, setMonthStart] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })
  const [selectedDay, setSelectedDay] = useState<string | null>(null)

  // Range do mês em ISO (início do mês → início do mês seguinte).
  const fromIso = monthStart.toISOString()
  const toIso = new Date(monthStart.getFullYear(), monthStart.getMonth() + 1, 1).toISOString()

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['appointments', fromIso, toIso],
    queryFn: () => getAppointments(fromIso, toIso),
    enabled: isTenant,
  })

  const updateStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: Appointment['status'] }) =>
      updateAppointmentStatus(id, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
    onError: (err) => console.error('updateAppointmentStatus failed:', err),
  })

  // Agrupa por dia local.
  const byDay = useMemo(() => {
    const map = new Map<string, Appointment[]>()
    for (const a of data ?? []) {
      const key = dayKey(new Date(a.scheduledAt))
      const list = map.get(key) ?? []
      list.push(a)
      map.set(key, list)
    }
    return map
  }, [data])

  // Células da grade: padding antes do dia 1 (alinha ao weekday) + os dias do mês.
  const cells = useMemo(() => {
    const year = monthStart.getFullYear()
    const month = monthStart.getMonth()
    const firstWeekday = new Date(year, month, 1).getDay() // 0=Dom
    const daysInMonth = new Date(year, month + 1, 0).getDate()
    const result: (Date | null)[] = []
    for (let i = 0; i < firstWeekday; i++) result.push(null)
    for (let d = 1; d <= daysInMonth; d++) result.push(new Date(year, month, d))
    return result
  }, [monthStart])

  const monthLabel = monthStart.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
  const selectedAppointments = selectedDay ? (byDay.get(selectedDay) ?? []) : []

  if (isError) {
    console.error('failed to load appointments:', error)
  }

  if (me && !isTenant) {
    return <p className="text-sm text-muted-foreground">Redirecionando…</p>
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Calendário" description="Agendamentos da sua empresa, por mês." />

      <div className="flex items-center justify-between">
        <Button
          variant="outline"
          className="h-8 px-3"
          onClick={() => {
            setSelectedDay(null)
            setMonthStart((m) => new Date(m.getFullYear(), m.getMonth() - 1, 1))
          }}
        >
          ‹
        </Button>
        <span className="text-sm font-medium capitalize">{monthLabel}</span>
        <Button
          variant="outline"
          className="h-8 px-3"
          onClick={() => {
            setSelectedDay(null)
            setMonthStart((m) => new Date(m.getFullYear(), m.getMonth() + 1, 1))
          }}
        >
          ›
        </Button>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar agendamentos.</p>
      ) : (
        <Card className="grid grid-cols-7 gap-1 text-sm">
          {WEEKDAY_LABELS.map((w) => (
            <div key={w} className="py-1 text-center text-xs font-medium text-muted-foreground">
              {w}
            </div>
          ))}
          {cells.map((date, i) => {
            if (!date) return <div key={`pad-${i}`} />
            const key = dayKey(date)
            const count = byDay.get(key)?.length ?? 0
            const isSelected = selectedDay === key
            return (
              <button
                key={key}
                type="button"
                onClick={() => setSelectedDay(isSelected ? null : key)}
                className={`flex aspect-square flex-col items-center justify-start rounded-md border p-1 transition-colors ${
                  isSelected
                    ? 'border-primary bg-primary/10'
                    : 'border-border hover:bg-muted'
                }`}
              >
                <span className="text-xs">{date.getDate()}</span>
                {count > 0 && (
                  <span className="mt-1 rounded-full bg-primary px-1.5 text-[10px] font-medium text-primary-foreground">
                    {count}
                  </span>
                )}
              </button>
            )
          })}
        </Card>
      )}

      {isPending && isTenant && <PageSkeleton />}

      {selectedDay && (
        <div className="mt-6">
          <h2 className="mb-3 text-sm font-semibold">
            Agendamentos de{' '}
            {new Date(`${selectedDay}T00:00:00`).toLocaleDateString('pt-BR', {
              day: '2-digit',
              month: 'long',
            })}
          </h2>
          {selectedAppointments.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhum agendamento neste dia.</p>
          ) : (
            <ul className="space-y-2">
              {selectedAppointments
                .slice()
                .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt))
                .map((a) => (
                  <li
                    key={a.id}
                    className="flex items-center justify-between gap-3 rounded-md border border-border p-3"
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-medium">{timeLabel(a.scheduledAt)}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        Contato {a.contactId.slice(0, 8)} · {STATUS_LABELS[a.status]}
                      </p>
                    </div>
                    <select
                      value={a.status}
                      disabled={updateStatus.isPending}
                      onChange={(e) =>
                        updateStatus.mutate({
                          id: a.id,
                          status: e.target.value as Appointment['status'],
                        })
                      }
                      className="rounded-md border border-border px-2 py-1 text-xs"
                    >
                      {(['scheduled', 'completed', 'cancelled', 'no_show'] as const).map((s) => (
                        <option key={s} value={s}>
                          {STATUS_LABELS[s]}
                        </option>
                      ))}
                    </select>
                  </li>
                ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
