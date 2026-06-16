'use client'

import { useQuery } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getAccessLogs, type AccessLogEntry } from '@/lib/api/access-logs'
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
  const router = useRouter()

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['access-logs'],
    queryFn: getAccessLogs,
    enabled: isTenant,
  })

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (isError) {
    console.error('failed to load access logs:', error)
  }

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
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
