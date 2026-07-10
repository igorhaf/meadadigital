'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { Modal } from '@/components/ui/modal'
import {
  createAvailabilitySlot,
  deleteAvailabilitySlot,
  getMyAvailabilitySlots,
  type AvailabilitySlot,
} from '@/lib/api/availability'
import { getMe } from '@/lib/api/me'
import { useResetWhen } from '@/lib/use-synced-form'

const WEEKDAYS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb']

const columns: Column<AvailabilitySlot>[] = [
  {
    key: 'weekday',
    header: 'Dia da semana',
    render: (s) => WEEKDAYS[s.weekday] ?? '—',
  },
  { key: 'startsAt', header: 'Início' },
  { key: 'endsAt', header: 'Fim' },
  {
    key: 'slotMinutes',
    header: 'Duração',
    render: (s) => `${s.slotMinutes} min`,
  },
  {
    key: 'active',
    header: 'Ativo',
    render: (s) => (s.active ? 'Sim' : 'Não'),
  },
]

/**
 * Janelas de disponibilidade do tenant (camada 5.17 #61) — CRUD via backend (/admin/
 * availability-slots, apiFetch + JWT/RLS). Lista numa DataTable; cria via Modal ("Nova
 * janela"); remove via confirm() por linha. Super-admin não usa: redireciona para
 * /dashboard (guard copiado de tags/page.tsx).
 */
export default function AvailabilityPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['my-availability-slots'],
    queryFn: getMyAvailabilitySlots,
    enabled: isTenant,
  })

  const removeSlot = useMutation({
    mutationFn: (id: string) => deleteAvailabilitySlot(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-availability-slots'] }),
    onError: (err) => console.error('deleteAvailabilitySlot failed:', err),
  })

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
  }

  if (isError) {
    console.error('failed to load availability slots:', error)
    return (
      <div className="space-y-4">
        <PageHeader title="Disponibilidade" />
        <p className="text-sm text-destructive">Erro ao carregar janelas.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Disponibilidade"
        description="Defina janelas de atendimento por dia da semana para gerar os horários agendáveis."
        actions={
          <Button onClick={() => setDialogOpen(true)} disabled={!me?.companyId}>
            Nova janela
          </Button>
        }
      />
      {isEmpty ? (
        <EmptyState
          icon={<CalendarClock />}
          title="Sem janelas ainda"
          description="Defina janelas de atendimento por dia da semana (ex.: Segunda 09:00–12:00 em slots de 30 min) para gerar os horários agendáveis."
          action={
            me?.companyId ? (
              <Button onClick={() => setDialogOpen(true)}>Criar primeira janela</Button>
            ) : undefined
          }
        />
      ) : (
        <DataTable<AvailabilitySlot>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhuma janela cadastrada."
          actions={(s) => (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={removeSlot.isPending && removeSlot.variables === s.id}
                onClick={() => {
                  if (
                    confirm(`Remover a janela de ${WEEKDAYS[s.weekday]} ${s.startsAt}–${s.endsAt}?`)
                  ) {
                    removeSlot.mutate(s.id)
                  }
                }}
              >
                Remover
              </Button>
            </div>
          )}
        />
      )}
      <NewSlotDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        disabled={!me?.companyId}
      />
    </div>
  )
}

/**
 * Modal de criação de janela. Campos: weekday (select 0-6), startsAt/endsAt (input time),
 * slotMinutes (number). active não aparece (toda janela nasce ativa); a edição de active é
 * via o PUT do backend, fora do escopo desta tela. Invalida ['my-availability-slots'] no
 * sucesso.
 */
function NewSlotDialog({
  open,
  onClose,
  disabled,
}: {
  open: boolean
  onClose: () => void
  disabled: boolean
}) {
  const queryClient = useQueryClient()
  const [weekday, setWeekday] = useState(1)
  const [startsAt, setStartsAt] = useState('09:00')
  const [endsAt, setEndsAt] = useState('12:00')
  const [slotMinutes, setSlotMinutes] = useState(30)
  const [serverError, setServerError] = useState<string | null>(null)

  useResetWhen(open, () => {
    setWeekday(1)
    setStartsAt('09:00')
    setEndsAt('12:00')
    setSlotMinutes(30)
    setServerError(null)
  })

  const mutation = useMutation({
    mutationFn: () =>
      createAvailabilitySlot({ weekday, startsAt, endsAt, slotMinutes, active: true }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-availability-slots'] })
      onClose()
    },
    onError: (err) => {
      console.error('createAvailabilitySlot failed:', err)
      setServerError('Erro ao criar janela. Tente novamente.')
    },
  })

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setServerError(null)
    if (startsAt >= endsAt) {
      setServerError('O início deve ser anterior ao fim.')
      return
    }
    mutation.mutate()
  }

  return (
    <Modal open={open} onClose={onClose} title="Nova janela">
      <form onSubmit={onSubmit} className="space-y-4">
        <div>
          <label htmlFor="weekday" className="mb-1 block text-sm font-medium">
            Dia da semana
          </label>
          <select
            id="weekday"
            value={weekday}
            onChange={(e) => setWeekday(Number(e.target.value))}
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
          >
            {WEEKDAYS.map((label, i) => (
              <option key={i} value={i}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex gap-3">
          <div className="flex-1">
            <label htmlFor="startsAt" className="mb-1 block text-sm font-medium">
              Início
            </label>
            <input
              id="startsAt"
              type="time"
              value={startsAt}
              onChange={(e) => setStartsAt(e.target.value)}
              className="w-full rounded-md border border-border px-3 py-2 text-sm"
            />
          </div>
          <div className="flex-1">
            <label htmlFor="endsAt" className="mb-1 block text-sm font-medium">
              Fim
            </label>
            <input
              id="endsAt"
              type="time"
              value={endsAt}
              onChange={(e) => setEndsAt(e.target.value)}
              className="w-full rounded-md border border-border px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div>
          <label htmlFor="slotMinutes" className="mb-1 block text-sm font-medium">
            Duração do slot (min)
          </label>
          <input
            id="slotMinutes"
            type="number"
            min={5}
            step={5}
            value={slotMinutes}
            onChange={(e) => setSlotMinutes(Number(e.target.value))}
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
          />
        </div>

        {serverError && <p className="text-sm text-destructive">{serverError}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={disabled || mutation.isPending}>
            {mutation.isPending ? 'Criando…' : 'Criar'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
