'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { DataTable, type Column } from '@/components/ui/data-table'
import { getErrors, type ErrorEntry } from '@/lib/api/admin/health'

/**
 * Log de erros (camada 6.4, super-admin). Últimos 50 erros capturados em pontos cravados
 * (envio Evolution fatal, falha fatal da IA), com filtro por origem. READ-only.
 */
export default function ErrorsPage() {
  const [source, setSource] = useState('')

  const { data, isPending, isError } = useQuery({
    queryKey: ['admin-errors', source],
    queryFn: () => getErrors(source || undefined),
  })

  const columns: Column<ErrorEntry>[] = [
    {
      key: 'createdAt',
      header: 'Quando',
      render: (r) => new Date(r.createdAt).toLocaleString('pt-BR'),
    },
    { key: 'source', header: 'Origem', render: (r) => <Badge variant="muted">{r.source}</Badge> },
    {
      key: 'message',
      header: 'Mensagem',
      render: (r) => <span className="line-clamp-2">{r.message}</span>,
    },
    {
      key: 'context',
      header: 'Contexto',
      render: (r) => (
        <span className="line-clamp-1 text-xs text-muted-foreground">
          {r.context && JSON.stringify(r.context) !== '{}' ? JSON.stringify(r.context) : '—'}
        </span>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Log de erros"
        description="Erros capturados em pontos críticos da plataforma."
      />

      <div className="flex items-end gap-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Origem</label>
          <select
            value={source}
            onChange={(e) => setSource(e.target.value)}
            className="rounded-md border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="">Todas</option>
            <option value="OutboundService">OutboundService (envio)</option>
            <option value="GeminiProvider">GeminiProvider (IA)</option>
          </select>
        </div>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o log de erros.</p>
      ) : (
        <DataTable<ErrorEntry>
          data={data?.items ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum erro registrado."
        />
      )}
    </div>
  )
}
