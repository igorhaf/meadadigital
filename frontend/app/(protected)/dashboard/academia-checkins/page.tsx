'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { listClasses } from '@/lib/api/academia/classes'
import { createCheckin, listCheckins } from '@/lib/api/academia/checkins'
import { listMemberships } from '@/lib/api/academia/memberships'
import { dayOfWeekLabel, formatDate, formatTime } from '@/profiles/academia/academia-types'

/** Date local → "YYYY-MM-DD". */
function toDateStr(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function daysAgo(n: number): string {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return toDateStr(d)
}

/**
 * Check-ins do AcademiaBot (camada 7.7): a recepção registra a presença de hoje por aula
 * (matrículas ativas, botão "Presente") e consulta o histórico de frequência por período.
 */
export default function AcademiaCheckinsPage() {
  const qc = useQueryClient()
  const today = toDateStr(new Date())
  const [classId, setClassId] = useState('')
  const [histFrom, setHistFrom] = useState(daysAgo(6))
  const [histTo, setHistTo] = useState(today)
  const [registerMsg, setRegisterMsg] = useState<string | null>(null)

  const classes = useQuery({ queryKey: ['academia-classes-all'], queryFn: () => listClasses({ onlyActive: true }) })

  // Matrículas ativas (da aula selecionada, ou todas p/ resolver nomes no histórico).
  const memberships = useQuery({
    queryKey: ['academia-memberships', 'checkins', classId],
    queryFn: () => listMemberships({ status: 'ativa', classId: classId || undefined, pageSize: 100 }),
  })

  // Check-ins de HOJE da aula selecionada — marca quem já tem presença.
  const todayCheckins = useQuery({
    queryKey: ['academia-checkins', 'today', classId, today],
    queryFn: () => listCheckins({ classId, from: today, to: today }),
    enabled: classId !== '',
  })

  // Histórico por período (respeita a aula selecionada, se houver).
  const history = useQuery({
    queryKey: ['academia-checkins', 'history', classId, histFrom, histTo],
    queryFn: () => listCheckins({ classId: classId || undefined, from: histFrom || undefined, to: histTo || undefined }),
  })

  const checkinMutation = useMutation({
    mutationFn: (membershipId: string) =>
      createCheckin({ membershipId, classId, source: 'painel' }),
    onSuccess: () => {
      setRegisterMsg(null)
      qc.invalidateQueries({ queryKey: ['academia-checkins'] })
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'duplicate_checkin') {
        // não é erro grave: a presença de hoje já existia — só sincroniza a lista.
        setRegisterMsg('Presença já registrada hoje para esse aluno.')
        qc.invalidateQueries({ queryKey: ['academia-checkins'] })
      } else if (e instanceof ApiError && (e.reason === 'membership_not_found' || e.reason === 'class_not_found')) {
        setRegisterMsg('Matrícula ou aula não encontrada. Recarregue a página.')
      } else {
        setRegisterMsg('Erro ao registrar a presença.')
      }
    },
  })

  const classItems = classes.data?.items ?? []
  const membershipItems = memberships.data?.items ?? []
  const presentToday = new Set((todayCheckins.data?.items ?? []).map((c) => c.membershipId))
  const historyItems = history.data?.items ?? []

  const selectedClass = classItems.find((c) => c.id === classId) ?? null

  function studentNameOf(membershipId: string): string {
    const m = membershipItems.find((x) => x.id === membershipId)
    return m ? m.studentName : membershipId.slice(0, 8)
  }

  function classNameOf(cid: string): string {
    const c = classItems.find((x) => x.id === cid)
    return c ? c.name : cid.slice(0, 8)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Check-ins"
        description="Frequência dos alunos. Registre a presença de hoje pela recepção e acompanhe o histórico por período."
      />

      <div className="max-w-md">
        <label className="mb-1 block text-xs font-medium text-muted-foreground">Aula</label>
        <select value={classId} onChange={(e) => { setClassId(e.target.value); setRegisterMsg(null) }}
          className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
          <option value="">Todas as aulas</option>
          {classItems.map((c) => (
            <option key={c.id} value={c.id}>
              {dayOfWeekLabel(c.dayOfWeek)} {formatTime(c.startTime)} · {c.modality} "{c.name}"
            </option>
          ))}
        </select>
      </div>

      {/* Registro de presença de hoje (só com aula selecionada) */}
      {classId !== '' && (
        <section className="space-y-2 rounded-lg border border-border p-4">
          <h2 className="text-sm font-semibold">
            Registrar presença de hoje{selectedClass ? ` — ${selectedClass.name}` : ''} ({formatDate(today)})
          </h2>
          {memberships.isError ? (
            <p className="text-sm text-destructive">Erro ao carregar as matrículas da aula.</p>
          ) : memberships.isPending ? (
            <p className="text-sm text-muted-foreground">Carregando…</p>
          ) : membershipItems.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhuma matrícula ativa nesta aula.</p>
          ) : (
            <div className="divide-y divide-border rounded-md border border-border">
              {membershipItems.map((m) => {
                const present = presentToday.has(m.id)
                return (
                  <div key={m.id} className="flex items-center justify-between gap-3 px-3 py-2">
                    <div className="min-w-0">
                      <span className="text-sm font-medium">{m.studentName}</span>
                      {m.studentPhone && <span className="ml-2 text-xs text-muted-foreground">{m.studentPhone}</span>}
                    </div>
                    {present ? (
                      <Badge variant="success">Presente ✓</Badge>
                    ) : (
                      <Button className="h-7 px-3 text-xs" disabled={checkinMutation.isPending}
                        onClick={() => checkinMutation.mutate(m.id)}>
                        Presente
                      </Button>
                    )}
                  </div>
                )
              })}
            </div>
          )}
          {registerMsg && <p className="text-xs text-muted-foreground">{registerMsg}</p>}
        </section>
      )}

      {/* Histórico */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold">Histórico</h2>
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">De</label>
            <input type="date" value={histFrom} onChange={(e) => setHistFrom(e.target.value)}
              className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Até</label>
            <input type="date" value={histTo} onChange={(e) => setHistTo(e.target.value)}
              className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
        </div>

        {history.isError ? (
          <p className="text-sm text-destructive">Erro ao carregar o histórico.</p>
        ) : history.isPending ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : historyItems.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nenhum check-in no período.</p>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs text-muted-foreground">
                  <th className="px-3 py-2 font-medium">Data</th>
                  {classId === '' && <th className="px-3 py-2 font-medium">Aula</th>}
                  <th className="px-3 py-2 font-medium">Aluno</th>
                  <th className="px-3 py-2 font-medium">Origem</th>
                  <th className="px-3 py-2 font-medium">Notas</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {historyItems.map((c) => (
                  <tr key={c.id}>
                    <td className="px-3 py-2 tabular-nums">{formatDate(c.checkinDate)}</td>
                    {classId === '' && <td className="px-3 py-2">{classNameOf(c.classId)}</td>}
                    <td className="px-3 py-2">{studentNameOf(c.membershipId)}</td>
                    <td className="px-3 py-2">
                      <Badge variant={c.source === 'ia' ? 'info' : 'muted'}>{c.source === 'ia' ? 'IA' : 'Painel'}</Badge>
                    </td>
                    <td className="px-3 py-2 text-xs text-muted-foreground">{c.notes ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
