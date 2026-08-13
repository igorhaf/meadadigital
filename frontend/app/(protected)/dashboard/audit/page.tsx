'use client'

import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ScrollText } from 'lucide-react'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getGlobalAuditLogs, type GlobalAuditLog } from '@/lib/api/admin/audit'
import { getAuditLogs, type AuditLogEntry } from '@/lib/api/audit'
import { getMe } from '@/lib/api/me'

/** Resumo curto do metadata (objeto livre) para a coluna — chave:valor truncado. */
function metadataPreview(metadata: Record<string, unknown> | null): string {
  if (!metadata || Object.keys(metadata).length === 0) {
    return '—'
  }
  const preview = Object.entries(metadata)
    .slice(0, 3)
    .map(([k, v]) => `${k}: ${String(v)}`)
    .join(', ')
  return preview.length > 80 ? `${preview.slice(0, 80)}…` : preview
}

const columns: Column<AuditLogEntry>[] = [
  {
    key: 'createdAt',
    header: 'Quando',
    render: (e) => new Date(e.createdAt).toLocaleString('pt-BR'),
  },
  {
    key: 'action',
    header: 'Ação',
    render: (e) => <Badge variant="default">{e.action}</Badge>,
  },
  { key: 'entity', header: 'Entidade' },
  {
    key: 'userId',
    header: 'Usuário',
    render: (e) => (
      <span className="font-mono text-xs text-muted-foreground">
        {e.userId ? e.userId.slice(0, 8) : '—'}
      </span>
    ),
  },
  {
    key: 'metadata',
    header: 'Detalhes',
    render: (e) => (
      <span className="text-xs text-muted-foreground">{metadataPreview(e.metadata)}</span>
    ),
  },
]

/**
 * Visualizador de audit log da empresa do tenant (camada 5.20 #78), via backend REST
 * (/admin/audit-logs). Filtros simples (entidade, ação) que refazem a query. Super-admin não
 * usa: redireciona para /dashboard.
 */
export default function AuditPage() {
  const [entity, setEntity] = useState('')
  const [action, setAction] = useState('')
  // Filtros aplicados (só mudam ao submeter) — separados dos inputs para não refazer a query
  // a cada tecla.
  const [applied, setApplied] = useState<{ entity: string; action: string }>({
    entity: '',
    action: '',
  })

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'
  const isSuperAdmin = me?.role === 'super_admin'

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['audit-logs', applied.entity, applied.action],
    queryFn: () => getAuditLogs({ entity: applied.entity, action: applied.action }),
    enabled: isTenant,
  })

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (isError) {
    console.error('failed to load audit logs:', error)
  }

  function applyFilters(e: React.FormEvent) {
    e.preventDefault()
    setApplied({ entity, action })
  }

  // Super-admin: auditoria GLOBAL da plataforma (camada 6.5). Tenant segue com a tela atual.
  if (isSuperAdmin) {
    return <GlobalAudit />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Auditoria"
        description="Histórico de ações sensíveis (criar/editar/remover dados) da sua empresa."
      />

      <Card>
        <form onSubmit={applyFilters} className="flex flex-wrap items-end gap-2">
          <div>
            <label htmlFor="filter-entity" className="mb-1 block text-xs font-medium">
              Entidade
            </label>
            <input
              id="filter-entity"
              type="text"
              placeholder="ex.: service"
              value={entity}
              onChange={(e) => setEntity(e.target.value)}
              className="rounded-md border border-border px-3 py-1.5 text-sm"
            />
          </div>
          <div>
            <label htmlFor="filter-action" className="mb-1 block text-xs font-medium">
              Ação
            </label>
            <input
              id="filter-action"
              type="text"
              placeholder="ex.: created"
              value={action}
              onChange={(e) => setAction(e.target.value)}
              className="rounded-md border border-border px-3 py-1.5 text-sm"
            />
          </div>
          <Button type="submit" variant="outline">
            Filtrar
          </Button>
          {(applied.entity || applied.action) && (
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setEntity('')
                setAction('')
                setApplied({ entity: '', action: '' })
              }}
            >
              Limpar
            </Button>
          )}
        </form>
      </Card>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o histórico.</p>
      ) : isEmpty ? (
        <EmptyState
          icon={<ScrollText />}
          title="Nenhum registro"
          description="As ações sensíveis da sua empresa (criar/editar/remover dados) aparecem aqui conforme acontecem."
        />
      ) : (
        <DataTable<AuditLogEntry>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum registro encontrado."
        />
      )}
    </div>
  )
}

/** Auditoria GLOBAL da plataforma (camada 6.5, super-admin). Filtros action/entity. */
function GlobalAudit() {
  const [action, setAction] = useState('')
  const [entity, setEntity] = useState('')
  const [applied, setApplied] = useState<{ action: string; entity: string }>({
    action: '',
    entity: '',
  })
  const [page, setPage] = useState(0)

  const { data, isPending, isError } = useQuery({
    queryKey: ['admin-audit-all', applied, page],
    queryFn: () =>
      getGlobalAuditLogs({
        action: applied.action || undefined,
        entity: applied.entity || undefined,
        page,
        pageSize: 20,
      }),
    placeholderData: keepPreviousData,
  })

  const columns: Column<GlobalAuditLog>[] = [
    {
      key: 'createdAt',
      header: 'Quando',
      render: (r) => new Date(r.createdAt).toLocaleString('pt-BR'),
    },
    { key: 'companyName', header: 'Empresa', render: (r) => r.companyName ?? '—' },
    { key: 'action', header: 'Ação' },
    { key: 'entity', header: 'Entidade' },
    {
      key: 'metadata',
      header: 'Detalhes',
      render: (r) => (
        <span className="line-clamp-1 text-xs text-muted-foreground">
          {r.metadata ? JSON.stringify(r.metadata) : '—'}
        </span>
      ),
    },
  ]

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div className="space-y-6">
      <PageHeader title="Auditoria" description="Auditoria global de todas as empresas." />
      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault()
          setApplied({ action, entity })
          setPage(0)
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Ação</label>
          <input
            value={action}
            onChange={(e) => setAction(e.target.value)}
            placeholder="insert, update…"
            className="w-48 rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Entidade</label>
          <input
            value={entity}
            onChange={(e) => setEntity(e.target.value)}
            placeholder="services, faqs…"
            className="w-48 rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          />
        </div>
        <Button type="submit" variant="outline">
          Filtrar
        </Button>
      </form>
      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar auditoria.</p>
      ) : (
        <>
          <DataTable<GlobalAuditLog>
            data={data?.items ?? []}
            columns={columns}
            loading={isPending}
            emptyMessage="Nenhum registro de auditoria."
          />
          {totalPages > 1 && (
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                Página {page + 1} de {totalPages} · {total} registro(s)
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
    </div>
  )
}
