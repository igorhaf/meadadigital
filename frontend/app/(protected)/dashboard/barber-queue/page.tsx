'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { listBarbers } from '@/lib/api/barbearia/barbers'
import { convertTicket, enqueue, listQueue, updateTicketStatus } from '@/lib/api/barbearia/queue'
import { listServices } from '@/lib/api/barbearia/services'
import { ApiError } from '@/lib/api/client'
import { statusLabel, type BarberQueueStatusId } from '@/profiles/barbearia/barber-queue-status'
import { formatTime, type QueueTicket } from '@/profiles/barbearia/barber-types'

/** Chave de agrupamento: "qualquer barbeiro" (geral) ou o barbeiro específico. */
const GERAL = '__geral__'

type Group = { key: string; label: string; tickets: QueueTicket[] }

/**
 * Agrupa os tickets ATIVOS por barbeiro (e fila geral), ordenando cada grupo: chamados primeiro,
 * depois aguardando por posição derivada (que o backend já calculou).
 */
function groupByBarber(items: QueueTicket[]): Group[] {
  const map = new Map<string, Group>()
  for (const t of items) {
    const key = t.barberId ?? GERAL
    const label = t.barberId ? (t.barberName ?? 'Barbeiro') : 'Fila geral (qualquer barbeiro)'
    if (!map.has(key)) map.set(key, { key, label, tickets: [] })
    map.get(key)!.tickets.push(t)
  }
  for (const g of map.values()) {
    g.tickets.sort((a, b) => {
      // chamado vem antes de aguardando; dentro de aguardando, pela posição derivada.
      if (a.status !== b.status) return a.status === 'chamado' ? -1 : 1
      return (a.position ?? 999) - (b.position ?? 999)
    })
  }
  // fila geral por último.
  return [...map.values()].sort((a, b) =>
    a.key === GERAL ? 1 : b.key === GERAL ? -1 : a.label.localeCompare(b.label),
  )
}

function fmtEta(min: number | null): string {
  if (min == null || min <= 0) return 'agora'
  return `~${min} min`
}

type FormState = {
  barberId: string
  serviceId: string
  guestName: string
  guestPhone: string
  notes: string
}
const EMPTY: FormState = { barberId: '', serviceId: '', guestName: '', guestPhone: '', notes: '' }

/**
 * FILA DE WALK-IN do BarbeariaBot (camada 8.1) — A TELA DA ESCAPADA. A posição de cada ticket é
 * DERIVADA pelo backend (sem coluna persistida): atender/desistir de quem está à frente recomputa
 * tudo. "Chamar" faz aguardando→chamado (notifica "chegou sua vez"); a IA NUNCA chama — só o painel.
 */
export default function BarberQueuePage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['barber-queue'],
    queryFn: () => listQueue(),
    refetchInterval: 10000, // a fila muda rápido — atualiza a cada 10s.
  })

  const barbers = useQuery({
    queryKey: ['barber-barbers-all'],
    queryFn: () => listBarbers({ onlyActive: true }),
  })
  const services = useQuery({
    queryKey: ['barber-services-all'],
    queryFn: () => listServices({ onlyActive: true }),
  })

  const enqueueMutation = useMutation({
    mutationFn: () =>
      enqueue({
        barberId: form.barberId || null,
        serviceId: form.serviceId,
        guestName: form.guestName,
        guestPhone: form.guestPhone || null,
        notes: form.notes || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['barber-queue'] })
      setModalOpen(false)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'queue_disabled') {
        setFormError('A fila de walk-in está desligada. Ligue-a nas Configurações.')
      } else if (e instanceof ApiError && e.reason === 'inactive_service') {
        setFormError('Esse serviço está inativo.')
      } else if (e instanceof ApiError && e.reason === 'inactive_barber') {
        setFormError('Esse barbeiro está inativo.')
      } else {
        setFormError('Erro ao colocar na fila.')
      }
    },
  })

  const convertMutation = useMutation({
    mutationFn: (t: { id: string; barberId: string | null }) => convertTicket(t.id, t.barberId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['barber-queue'] })
      qc.invalidateQueries({ queryKey: ['barber-appointments'] })
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, newStatus }: { id: string; newStatus: BarberQueueStatusId }) =>
      updateTicketStatus(id, newStatus),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['barber-queue'] }),
  })

  const items = data?.items ?? []
  const waiting = data?.waiting ?? 0
  const groups = groupByBarber(items)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Fila"
        description="Walk-in por ordem de chegada. A posição é calculada na hora — atender ou liberar alguém reordena tudo automaticamente."
        actions={
          <Button
            onClick={() => {
              setForm(EMPTY)
              setFormError(null)
              setModalOpen(true)
            }}
          >
            Colocar na fila
          </Button>
        }
      />

      <p className="text-sm text-muted-foreground">
        <strong>{waiting}</strong> aguardando no total.
      </p>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar a fila.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">A fila está vazia.</p>
      ) : (
        <div className="space-y-6">
          {groups.map((g) => (
            <section key={g.key} className="space-y-2">
              <h2 className="text-sm font-semibold text-muted-foreground">{g.label}</h2>
              <div className="divide-y divide-border rounded-lg border border-border">
                {g.tickets.map((t) => (
                  <div key={t.id} className="flex items-center justify-between gap-3 px-4 py-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        {t.status === 'aguardando' && t.position != null && (
                          <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary">
                            {t.position}
                          </span>
                        )}
                        <span className="font-medium">{t.guestName}</span>
                        {t.status === 'chamado' && (
                          <Badge variant="success">{statusLabel(t.status)}</Badge>
                        )}
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {t.serviceName}
                        {t.status === 'aguardando' && (
                          <> · espera estimada {fmtEta(t.etaMinutes)}</>
                        )}
                        {t.status === 'chamado' && t.calledAt && (
                          <> · chamado às {formatTime(t.calledAt)}</>
                        )}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      {t.status === 'aguardando' && (
                        <Button
                          className="h-7 px-3 text-xs"
                          disabled={statusMutation.isPending}
                          onClick={() => statusMutation.mutate({ id: t.id, newStatus: 'chamado' })}
                        >
                          Chamar
                        </Button>
                      )}
                      {t.status === 'chamado' && t.barberId && (
                        <Button
                          className="h-7 px-3 text-xs"
                          disabled={statusMutation.isPending || convertMutation.isPending}
                          onClick={() => convertMutation.mutate({ id: t.id, barberId: t.barberId })}
                        >
                          Iniciar atendimento
                        </Button>
                      )}
                      {t.status === 'chamado' && (
                        <Button
                          variant="outline"
                          className="h-7 px-3 text-xs"
                          disabled={statusMutation.isPending}
                          onClick={() => statusMutation.mutate({ id: t.id, newStatus: 'atendido' })}
                        >
                          Atendido
                        </Button>
                      )}
                      <Button
                        variant="outline"
                        className="h-7 px-2 text-xs"
                        disabled={statusMutation.isPending}
                        onClick={() => statusMutation.mutate({ id: t.id, newStatus: 'desistiu' })}
                      >
                        Desistiu
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Colocar na fila" size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            enqueueMutation.mutate()
          }}
        >
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Barbeiro
              </label>
              <select
                value={form.barberId}
                onChange={(e) => setForm((f) => ({ ...f, barberId: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Qualquer barbeiro</option>
                {(barbers.data?.items ?? []).map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Serviço
              </label>
              <select
                value={form.serviceId}
                onChange={(e) => setForm((f) => ({ ...f, serviceId: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Selecione…</option>
                {(services.data?.items ?? []).map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.name} ({o.durationMinutes}min)
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Cliente
              </label>
              <input
                value={form.guestName}
                onChange={(e) => setForm((f) => ({ ...f, guestName: e.target.value }))}
                required
                maxLength={200}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Telefone (opcional)
              </label>
              <input
                value={form.guestPhone}
                onChange={(e) => setForm((f) => ({ ...f, guestPhone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações
            </label>
            <textarea
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={enqueueMutation.isPending}>
              {enqueueMutation.isPending ? 'Adicionando…' : 'Adicionar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
