'use client'

import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getAccessLogs, type AccessLogEntry } from '@/lib/api/access-logs'
import { getGlobalAccessLogs, type GlobalAccessLog } from '@/lib/api/admin/audit'
import { getMe } from '@/lib/api/me'

/** Rótulos legíveis das ações de acesso (o enum cru fica feio na tabela). */
const ACTION_LABELS: Record<AccessLogEntry['action'], string> = {
  login_success: 'Login bem-sucedido',
  login_failed: 'Login falhou',
  password_changed: 'Senha alterada',
}

const columns: Column<AccessLogEntry>[] = [
  {
    key: 'createdAt',
    header: 'Quando',
    render: (e) => new Date(e.createdAt).toLocaleString('pt-BR'),
  },
  {
    key: 'action',
    header: 'Ação',
    render: (e) => (
      <Badge variant={e.action === 'login_failed' ? 'danger' : 'success'}>
        {ACTION_LABELS[e.action]}
      </Badge>
    ),
  },
  { key: 'email', header: 'Email', render: (e) => e.email ?? '—' },
  {
    key: 'ip',
    header: 'IP',
    render: (e) => <span className="font-mono text-xs text-muted-foreground">{e.ip ?? '—'}</span>,
  },
]

/**
 * Tela de segurança do tenant (camada 5.24 #92): lista os logs de acesso da própria empresa
 * (login_success/failed, password_changed) via backend REST (/admin/access-logs). Super-admin
 * não usa: redireciona para /dashboard.
 */
export default function SecurityPage() {
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'
  const isSuperAdmin = me?.role === 'super_admin'

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['access-logs'],
    queryFn: getAccessLogs,
    enabled: isTenant,
  })

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (isError) {
    console.error('failed to load access logs:', error)
  }

  // Super-admin: segurança/acessos GLOBAL (camada 6.5). Tenant segue com a tela atual.
  if (isSuperAdmin) {
    return <GlobalSecurity />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Segurança"
        description="Tentativas de login e mudanças de senha da sua empresa, mais recentes primeiro."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os acessos.</p>
      ) : isEmpty ? (
        <EmptyState
          icon={<ShieldCheck />}
          title="Nenhum acesso registrado"
          description="Logins e alterações de senha da sua empresa aparecem aqui conforme acontecem."
        />
      ) : (
        <DataTable<AccessLogEntry>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum acesso registrado."
        />
      )}
    </div>
  )
}

/** Segurança/acessos GLOBAL da plataforma (camada 6.5, super-admin). Filtro por ação. */
function GlobalSecurity() {
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)

  const { data, isPending, isError } = useQuery({
    queryKey: ['admin-access-logs-all', action, page],
    queryFn: () => getGlobalAccessLogs({ action: action || undefined, page, pageSize: 20 }),
    placeholderData: keepPreviousData,
  })

  const columns: Column<GlobalAccessLog>[] = [
    {
      key: 'createdAt',
      header: 'Quando',
      render: (r) => new Date(r.createdAt).toLocaleString('pt-BR'),
    },
    { key: 'companyName', header: 'Empresa', render: (r) => r.companyName ?? '—' },
    { key: 'email', header: 'Email', render: (r) => r.email ?? '—' },
    {
      key: 'action',
      header: 'Ação',
      render: (r) => (
        <Badge variant={r.action === 'login_failed' ? 'danger' : 'success'}>
          {ACTION_LABELS[r.action as AccessLogEntry['action']] ?? r.action}
        </Badge>
      ),
    },
    {
      key: 'ip',
      header: 'IP',
      render: (r) => <span className="font-mono text-xs text-muted-foreground">{r.ip ?? '—'}</span>,
    },
  ]

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div className="space-y-6">
      <PageHeader title="Segurança" description="Acessos e segurança de toda a plataforma." />
      <div className="flex items-end gap-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Ação</label>
          <select
            value={action}
            onChange={(e) => {
              setAction(e.target.value)
              setPage(0)
            }}
            className="rounded-md border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="">Todas</option>
            <option value="login_success">Login bem-sucedido</option>
            <option value="login_failed">Login falhou</option>
            <option value="password_changed">Senha alterada</option>
          </select>
        </div>
      </div>
      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar acessos.</p>
      ) : (
        <>
          <DataTable<GlobalAccessLog>
            data={data?.items ?? []}
            columns={columns}
            loading={isPending}
            emptyMessage="Nenhum registro de acesso."
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
