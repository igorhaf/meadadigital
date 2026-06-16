'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { ApiError } from '@/lib/api/client'
import { getCompanies, type Company } from '@/lib/api/companies'
import { CreateCompanyDialog } from './create-company-dialog'

/**
 * Lista GLOBAL de empresas (rota canônica do super-admin). A autorização é do backend:
 * um tenant-admin que digite esta URL recebe 403 forbidden_not_super_admin → tratado
 * inline ("acesso restrito"), sem duplicar a checagem de papel no front.
 */
const columns: Column<Company>[] = [
  { key: 'name', header: 'Nome' },
  { key: 'slug', header: 'Slug' },
  {
    key: 'status',
    header: 'Status',
    render: (c) => (
      <Badge variant={c.status === 'active' ? 'success' : 'danger'}>{c.status}</Badge>
    ),
  },
  {
    key: 'createdAt',
    header: 'Criada em',
    render: (c) => new Date(c.createdAt).toLocaleString('pt-BR'),
  },
]

export default function CompaniesPage() {
  const [dialogOpen, setDialogOpen] = useState(false)
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['companies'],
    queryFn: getCompanies,
  })

  // 403: tenant-admin acessou a rota direto. Trata inline (não quebra), sem duplicar
  // a checagem de papel — o backend é a fonte de verdade da autorização.
  if (isError && error instanceof ApiError && error.status === 403) {
    return (
      <div className="space-y-6">
        <PageHeader title="Acesso restrito" description="Esta área é restrita ao super-admin." />
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  if (isError) {
    console.error('failed to load /admin/companies:', error)
    return (
      <div className="space-y-6">
        <PageHeader title="Empresas" />
        <p className="text-sm text-destructive">Erro ao carregar empresas.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Empresas"
        description="Todas as empresas cadastradas na plataforma."
        actions={<Button onClick={() => setDialogOpen(true)}>Nova empresa</Button>}
      />
      <DataTable<Company>
        data={data ?? []}
        columns={columns}
        loading={isPending}
        emptyMessage="Nenhuma empresa cadastrada."
      />
      <CreateCompanyDialog open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </div>
  )
}
