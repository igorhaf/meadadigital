'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  assignSalesperson,
  createLead,
  listLeads,
  updateLeadStatus,
} from '@/lib/api/concessionaria/leads'
import { listSalespeople } from '@/lib/api/concessionaria/salespeople'
import { listVehicles } from '@/lib/api/concessionaria/vehicles'
import {
  ALLOWED_NEXT,
  LEAD_STATUSES,
  statusLabel,
  type LeadStatusId,
} from '@/profiles/concessionaria/concessionaria-lead-status'
import {
  formatBrl,
  formatDateTime,
  PAYMENT_CONDITIONS,
  paymentConditionLabel,
  type Lead,
  type PaymentCondition,
} from '@/profiles/concessionaria/concessionaria-types'

function StatusBadge({ status }: { status: LeadStatusId }) {
  const variant =
    status === 'fechado'
      ? 'success'
      : status === 'em_negociacao'
        ? 'warning'
        : status === 'novo'
          ? 'info'
          : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  vehicleId: string
  paymentCondition: PaymentCondition
  customerName: string
  customerPhone: string
  salespersonId: string
  notes: string
}
const EMPTY: FormState = {
  vehicleId: '',
  paymentCondition: 'avista',
  customerName: '',
  customerPhone: '',
  salespersonId: '',
  notes: '',
}

/**
 * Funil de leads do ConcessionariaBot (camada 8.17). Lista por status, criação manual via Modal,
 * detalhe com snapshot do veículo + condição de pagamento, atribuição de vendedor e mudança de
 * status (ALLOWED_NEXT). Ao mover para 'perdido', exige o motivo (lostReason).
 */
export default function ConcessionariaLeadsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const [detail, setDetail] = useState<Lead | null>(null)
  const [lostReason, setLostReason] = useState('')
  const [statusError, setStatusError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['concessionaria-leads', status, page],
    queryFn: () => listLeads({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const vehicles = useQuery({
    queryKey: ['concessionaria-vehicles-available'],
    queryFn: () => listVehicles({ available: true }),
  })
  const salespeople = useQuery({
    queryKey: ['concessionaria-salespeople-active'],
    queryFn: () => listSalespeople({ onlyActive: true }),
  })
  const salespersonLabel = useMemo(() => {
    const m = new Map<string, string>()
    for (const s of salespeople.data?.items ?? []) m.set(s.id, s.name)
    return m
  }, [salespeople.data])

  const createMutation = useMutation({
    mutationFn: () =>
      createLead({
        vehicleId: form.vehicleId,
        paymentCondition: form.paymentCondition,
        customerName: form.customerName || null,
        customerPhone: form.customerPhone || null,
        salespersonId: form.salespersonId || null,
        notes: form.notes || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['concessionaria-leads'] })
      setModalOpen(false)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao criar o lead.'),
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: LeadStatusId) => {
      if (!detail) throw new Error('sem lead')
      const reason = newStatus === 'perdido' ? lostReason || null : null
      return updateLeadStatus(detail.id, newStatus, reason)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['concessionaria-leads'] })
      setDetail(updated)
      setLostReason('')
      setStatusError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_status_transition') {
        setStatusError('Essa mudança de status não é permitida.')
      } else {
        setStatusError('Erro ao mudar o status.')
      }
    },
  })

  const assignMutation = useMutation({
    mutationFn: (salespersonId: string) => {
      if (!detail) throw new Error('sem lead')
      return assignSalesperson(detail.id, salespersonId)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['concessionaria-leads'] })
      setDetail(updated)
    },
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Leads"
        description="Funil de interesse. A IA capta leads pelo WhatsApp; conduza a negociação aqui."
        actions={
          <Button
            onClick={() => {
              setForm(EMPTY)
              setFormError(null)
              setModalOpen(true)
            }}
          >
            Novo lead
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <button
          onClick={() => {
            setStatus('')
            setPage(0)
          }}
          className={`rounded-full border px-3 py-1 text-xs ${status === '' ? 'border-primary bg-primary/10' : 'border-border'}`}
        >
          Todos
        </button>
        {LEAD_STATUSES.map((s) => (
          <button
            key={s.id}
            onClick={() => {
              setStatus(s.id)
              setPage(0)
            }}
            className={`rounded-full border px-3 py-1 text-xs ${status === s.id ? 'border-primary bg-primary/10' : 'border-border'}`}
          >
            {s.label}
          </button>
        ))}
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os leads.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum lead encontrado.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((l) => (
            <button
              key={l.id}
              onClick={() => {
                setDetail(l)
                setLostReason('')
                setStatusError(null)
              }}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">
                    {l.vehicleBrand} {l.vehicleModel}
                    {l.vehicleYear ? ` ${l.vehicleYear}` : ''}
                  </span>
                  <StatusBadge status={l.status} />
                </div>
                <p className="text-xs text-muted-foreground">
                  {l.customerName ?? 'Cliente'} · {formatBrl(l.vehiclePriceCents)} ·{' '}
                  {paymentConditionLabel(l.paymentCondition)}
                  {l.salespersonId
                    ? ` · ${salespersonLabel.get(l.salespersonId) ?? 'Vendedor'}`
                    : ''}
                </p>
              </div>
              <span className="shrink-0 text-xs text-muted-foreground">
                {formatDateTime(l.createdAt)}
              </span>
            </button>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Página {page + 1} de {totalPages} · {total} lead(s)
          </span>
          <div className="flex gap-1">
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ←
            </Button>
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              →
            </Button>
          </div>
        </div>
      )}

      {/* Modal: novo lead */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Novo lead" size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Veículo de interesse
            </label>
            <select
              value={form.vehicleId}
              onChange={(e) => setForm((f) => ({ ...f, vehicleId: e.target.value }))}
              required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Selecione…</option>
              {(vehicles.data?.items ?? []).map((v) => (
                <option key={v.id} value={v.id}>
                  {v.brand} {v.model}
                  {v.modelYear ? ` ${v.modelYear}` : ''} — {formatBrl(v.priceCents)}
                </option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Cliente
              </label>
              <input
                value={form.customerName}
                onChange={(e) => setForm((f) => ({ ...f, customerName: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Telefone
              </label>
              <input
                value={form.customerPhone}
                onChange={(e) => setForm((f) => ({ ...f, customerPhone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Pagamento
              </label>
              <select
                value={form.paymentCondition}
                onChange={(e) =>
                  setForm((f) => ({ ...f, paymentCondition: e.target.value as PaymentCondition }))
                }
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                {PAYMENT_CONDITIONS.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Vendedor
              </label>
              <select
                value={form.salespersonId}
                onChange={(e) => setForm((f) => ({ ...f, salespersonId: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">(sem atribuir)</option>
                {(salespeople.data?.items ?? []).map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
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
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Criando…' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: detalhe do lead */}
      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Lead" size="md">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">
                {detail.vehicleBrand} {detail.vehicleModel}
                {detail.vehicleYear ? ` ${detail.vehicleYear}` : ''}
              </span>
              <StatusBadge status={detail.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Cliente</dt>
                  <dd>{detail.customerName ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Telefone</dt>
                  <dd>{detail.customerPhone ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Veículo (valor)</dt>
                  <dd>{formatBrl(detail.vehiclePriceCents)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Pagamento</dt>
                  <dd>{paymentConditionLabel(detail.paymentCondition)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Origem</dt>
                  <dd>{detail.conversationId ? 'WhatsApp' : 'Manual'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Criado em</dt>
                  <dd>{formatDateTime(detail.createdAt)}</dd>
                </div>
                {detail.lostReason && (
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">Motivo da perda</dt>
                    <dd>{detail.lostReason}</dd>
                  </div>
                )}
                {detail.notes && (
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">Observações</dt>
                    <dd>{detail.notes}</dd>
                  </div>
                )}
              </dl>
            </Card>

            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Vendedor responsável
              </label>
              <select
                value={detail.salespersonId ?? ''}
                disabled={assignMutation.isPending}
                onChange={(e) => {
                  if (e.target.value) assignMutation.mutate(e.target.value)
                }}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">(sem atribuir)</option>
                {(salespeople.data?.items ?? []).map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>

            {ALLOWED_NEXT[detail.status].length > 0 ? (
              <div className="space-y-2">
                <label className="block text-xs font-medium text-muted-foreground">
                  Mover no funil para…
                </label>
                {ALLOWED_NEXT[detail.status].includes('perdido') && (
                  <input
                    value={lostReason}
                    onChange={(e) => setLostReason(e.target.value)}
                    placeholder="Motivo da perda (ao marcar como Perdido)"
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                  />
                )}
                <div className="flex flex-wrap gap-2">
                  {ALLOWED_NEXT[detail.status].map((next) => (
                    <Button
                      key={next}
                      variant="outline"
                      className="h-8 px-3 text-xs"
                      disabled={statusMutation.isPending}
                      onClick={() => statusMutation.mutate(next)}
                    >
                      {statusLabel(next)}
                    </Button>
                  ))}
                </div>
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">Este lead está num status final.</p>
            )}
            {statusError && <p className="text-sm text-destructive">{statusError}</p>}
          </div>
        )}
      </Modal>
    </div>
  )
}
