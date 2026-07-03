'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { Modal } from '@/components/ui/modal'
import {
  createAnnouncement,
  expireAnnouncement,
  listAnnouncements,
  updateAnnouncement,
  type Announcement,
  type AnnouncementSeverity,
} from '@/lib/api/admin/announcements'

const SEVERITY_LABEL: Record<AnnouncementSeverity, string> = {
  info: 'Info',
  warning: 'Atenção',
  critical: 'Crítico',
}

function SeverityBadge({ severity }: { severity: AnnouncementSeverity }) {
  const variant = severity === 'critical' ? 'danger' : severity === 'warning' ? 'warning' : 'info'
  return <Badge variant={variant}>{SEVERITY_LABEL[severity]}</Badge>
}

/** Um anúncio está ativo se não tem expiração ou ela é futura. */
function isActive(a: Announcement): boolean {
  return !a.expiresAt || new Date(a.expiresAt).getTime() > Date.now()
}

type FormState = {
  title: string
  body: string
  severity: AnnouncementSeverity
  expiresAt: string
  dismissable: boolean
}

const EMPTY_FORM: FormState = {
  title: '',
  body: '',
  severity: 'info',
  expiresAt: '',
  dismissable: true,
}

/**
 * Anúncios cross-tenant (camada 6.7, super-admin). Lista paginada, criação/edição via Modal,
 * e "expirar" (soft delete). Anúncios ativos viram banner no AppShell dos tenants.
 */
export default function AnnouncementsPage() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Announcement | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)

  const { data, isPending, isError } = useQuery({
    queryKey: ['admin-announcements', page],
    queryFn: () => listAnnouncements({ page, pageSize: 20 }),
    placeholderData: keepPreviousData,
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        title: form.title,
        body: form.body,
        severity: form.severity,
        expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : null,
        dismissable: form.dismissable,
      }
      if (editing) return updateAnnouncement(editing.id, payload)
      return createAnnouncement(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-announcements'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY_FORM)
    },
  })

  const expireMutation = useMutation({
    mutationFn: (id: string) => expireAnnouncement(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-announcements'] }),
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setModalOpen(true)
  }

  function openEdit(a: Announcement) {
    setEditing(a)
    setForm({
      title: a.title,
      body: a.body,
      severity: a.severity,
      expiresAt: a.expiresAt ? a.expiresAt.slice(0, 16) : '',
      dismissable: a.dismissable,
    })
    setModalOpen(true)
  }

  const columns: Column<Announcement>[] = [
    {
      key: 'title',
      header: 'Título',
      render: (a) => <span className="font-medium">{a.title}</span>,
    },
    {
      key: 'severity',
      header: 'Severidade',
      render: (a) => <SeverityBadge severity={a.severity} />,
    },
    {
      key: 'publishedAt',
      header: 'Publicado',
      render: (a) => new Date(a.publishedAt).toLocaleString('pt-BR'),
    },
    {
      key: 'expiresAt',
      header: 'Estado',
      render: (a) =>
        isActive(a) ? (
          <Badge variant="success">Ativo</Badge>
        ) : (
          <span className="text-xs text-muted-foreground">
            expirado {new Date(a.expiresAt as string).toLocaleDateString('pt-BR')}
          </span>
        ),
    },
    { key: 'dismissable', header: 'Dispensável', render: (a) => (a.dismissable ? 'Sim' : 'Não') },
    {
      key: 'actions',
      header: '',
      render: (a) => (
        <div className="flex justify-end gap-1">
          <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(a)}>
            Editar
          </Button>
          {isActive(a) && (
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={expireMutation.isPending}
              onClick={() => expireMutation.mutate(a.id)}
            >
              Expirar
            </Button>
          )}
        </div>
      ),
    },
  ]

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Anúncios"
        description="Avisos globais exibidos como banner para todos os tenants."
        actions={<Button onClick={openCreate}>Novo anúncio</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os anúncios.</p>
      ) : (
        <>
          <DataTable<Announcement>
            data={data?.items ?? []}
            columns={columns}
            loading={isPending}
            emptyMessage="Nenhum anúncio criado ainda."
          />
          {totalPages > 1 && (
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                Página {page + 1} de {totalPages} · {total} anúncio(s)
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
        </>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar anúncio' : 'Novo anúncio'}
        size="lg"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Título</label>
            <input
              value={form.title}
              onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              required
              maxLength={200}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Mensagem</label>
            <textarea
              value={form.body}
              onChange={(e) => setForm((f) => ({ ...f, body: e.target.value }))}
              required
              maxLength={5000}
              rows={4}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Severidade
              </label>
              <select
                value={form.severity}
                onChange={(e) =>
                  setForm((f) => ({ ...f, severity: e.target.value as AnnouncementSeverity }))
                }
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="info">Info</option>
                <option value="warning">Atenção</option>
                <option value="critical">Crítico</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Expira em (opcional)
              </label>
              <input
                type="datetime-local"
                value={form.expiresAt}
                onChange={(e) => setForm((f) => ({ ...f, expiresAt: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={form.dismissable}
              onChange={(e) => setForm((f) => ({ ...f, dismissable: e.target.checked }))}
            />
            Permitir que o tenant dispense (oculte) o aviso
          </label>
          {saveMutation.isError && (
            <p className="text-sm text-destructive">Erro ao salvar o anúncio.</p>
          )}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Salvando…' : editing ? 'Salvar' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
