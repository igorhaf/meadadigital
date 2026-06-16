'use client'

import { useQuery } from '@tanstack/react-query'
import { ScrollText } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
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
  const router = useRouter()
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

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

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

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
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
