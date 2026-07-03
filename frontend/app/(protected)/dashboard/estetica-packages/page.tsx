'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { createPackage, listPackages, updatePackageStatus } from '@/lib/api/estetica/packages'
import { listProcedures } from '@/lib/api/estetica/procedures'
import {
  AESTHETIC_PACKAGE_STATUSES,
  ALLOWED_NEXT,
  statusLabel,
  type AestheticPackageStatusId,
} from '@/profiles/estetica/aesthetic-package-status'
import { formatDate, formatPrice, type AestheticPackage } from '@/profiles/estetica/estetica-types'

function StatusBadge({ status }: { status: AestheticPackageStatusId }) {
  const variant =
    status === 'ativo'
      ? 'success'
      : status === 'pendente'
        ? 'warning'
        : status === 'esgotado'
          ? 'info'
          : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type CreateForm = {
  customerName: string
  procedureId: string
  totalSessions: string
  notes: string
}
const EMPTY_CREATE: CreateForm = {
  customerName: '',
  procedureId: '',
  totalSessions: '10',
  notes: '',
}

/**
 * Pacotes do EsteticaBot (camada 8.3) — A TELA DA ESCAPADA. Lista por status com a barra de saldo
 * (sessões restantes / total), criação manual (total = sessões × preço do procedimento) e transições
 * de status (ativar = confirmar pagamento; cancelar/expirar).
 */
export default function EsteticaPackagesPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState<CreateForm>(EMPTY_CREATE)
  const [createError, setCreateError] = useState<string | null>(null)

  const [statusTarget, setStatusTarget] = useState<{
    id: string
    next: AestheticPackageStatusId
  } | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['estetica-packages', status, page],
    queryFn: () => listPackages({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })
  const procedures = useQuery({
    queryKey: ['estetica-procedures-all'],
    queryFn: () => listProcedures({ onlyActive: true }),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createPackage({
        customerName: createForm.customerName || null,
        procedureId: createForm.procedureId,
        totalSessions: Math.max(1, Math.round(Number(createForm.totalSessions) || 1)),
        notes: createForm.notes || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['estetica-packages'] })
      setCreateOpen(false)
      setCreateForm(EMPTY_CREATE)
      setCreateError(null)
    },
    onError: () => setCreateError('Erro ao criar o pacote.'),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, next }: { id: string; next: AestheticPackageStatusId }) =>
      updatePackageStatus(id, next),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['estetica-packages'] })
      setStatusTarget(null)
      setStatusError(null)
    },
    onError: (e) => {
      setStatusTarget(null)
      if (e instanceof ApiError && e.reason === 'invalid_status_transition')
        setStatusError('Transição de status inválida.')
      else setStatusError('Erro ao mudar o status.')
    },
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Pacotes"
        description="Pacotes de sessões: cada agendamento abate 1 do saldo. Ativar confirma o pagamento."
        actions={
          <Button
            onClick={() => {
              setCreateForm(EMPTY_CREATE)
              setCreateError(null)
              setCreateOpen(true)
            }}
          >
            Novo pacote
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
        {AESTHETIC_PACKAGE_STATUSES.map((s) => (
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
        <p className="text-sm text-destructive">Erro ao carregar os pacotes.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum pacote encontrado.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((pk: AestheticPackage) => {
            const next = ALLOWED_NEXT[pk.status]
            return (
              <div key={pk.id} className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{pk.customerName}</span>
                    <span className="text-xs text-muted-foreground">{pk.procedureName}</span>
                    <StatusBadge status={pk.status} />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {pk.sessionsRemaining} de {pk.totalSessions} sessões restantes ·{' '}
                    {formatPrice(pk.totalCents)} · {formatDate(pk.purchasedAt)}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {next.map((n) => (
                    <Button
                      key={n}
                      variant="outline"
                      className="h-7 px-2 text-xs"
                      onClick={() => setStatusTarget({ id: pk.id, next: n })}
                    >
                      {statusLabel(n)}
                    </Button>
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Página {page + 1} de {totalPages} · {total} pacotes
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
      {statusError && <p className="text-sm text-destructive">{statusError}</p>}

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Novo pacote" size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Cliente</label>
            <input
              value={createForm.customerName}
              onChange={(e) => setCreateForm((f) => ({ ...f, customerName: e.target.value }))}
              placeholder="Nome do cliente"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Procedimento
              </label>
              <select
                value={createForm.procedureId}
                onChange={(e) => setCreateForm((f) => ({ ...f, procedureId: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Selecione…</option>
                {(procedures.data?.items ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} · {formatPrice(p.unitPriceCents)}/sessão
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Nº de sessões
              </label>
              <input
                type="number"
                min="1"
                value={createForm.totalSessions}
                required
                onChange={(e) => setCreateForm((f) => ({ ...f, totalSessions: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações
            </label>
            <textarea
              value={createForm.notes}
              onChange={(e) => setCreateForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <p className="text-xs text-muted-foreground">
            O total é calculado automaticamente (sessões × preço do procedimento). O pacote nasce
            pendente — ative-o ao confirmar o pagamento.
          </p>
          {createError && <p className="text-sm text-destructive">{createError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Criando…' : 'Criar pacote'}
            </Button>
          </div>
        </form>
      </Modal>

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget.next) : ''}"?`}
        description="Ativar um pacote confirma o pagamento e libera o agendamento de sessões. O cliente é notificado na ativação (se houver vínculo com o WhatsApp)."
        confirmLabel="Mudar status"
        destructive={false}
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (statusTarget) statusMutation.mutate(statusTarget)
        }}
      />
    </div>
  )
}
