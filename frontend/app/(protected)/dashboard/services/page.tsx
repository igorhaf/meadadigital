'use client'

import { useQuery } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { getMe } from '@/lib/api/me'
import { getMyServices, type Service } from '@/lib/supabase/services'
import { CreateServiceDialog } from './create-service-dialog'

const columns: Column<Service>[] = [
  { key: 'name', header: 'Nome' },
  {
    key: 'description',
    header: 'Descrição',
    render: (s) => s.description ?? '—',
  },
  {
    key: 'priceCents',
    header: 'Preço',
    render: (s) =>
      s.priceCents == null
        ? '—'
        : (s.priceCents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }),
  },
  {
    key: 'active',
    header: 'Ativo',
    render: (s) => (
      <Badge variant={s.active ? 'success' : 'danger'}>{s.active ? 'sim' : 'não'}</Badge>
    ),
  },
]

/**
 * Serviços da empresa do tenant (SDK + RLS). Super-admin NÃO usa esta tela: se cair aqui,
 * redireciona para /dashboard (padrão inverso do hub, que manda super-admin para
 * /dashboard/companies). A criação exige companyId (do /admin/me) por causa do
 * WITH CHECK do RLS no INSERT.
 */
export default function ServicesPage() {
  const router = useRouter()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingService, setEditingService] = useState<Service | undefined>(undefined)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['my-services'],
    queryFn: getMyServices,
    enabled: isTenant, // só busca quando confirmado tenant (evita 0 rows para super-admin)
  })

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-5xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  if (isError) {
    console.error('failed to load services:', error)
    return (
      <div className="mx-auto max-w-5xl p-8">
        <h1 className="mb-2 text-xl font-semibold">Serviços</h1>
        <p className="mb-4 text-sm text-destructive">Erro ao carregar serviços.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Serviços</h1>
        <div className="flex items-center gap-2">
          <Button
            onClick={() => {
              setEditingService(undefined)
              setDialogOpen(true)
            }}
            disabled={!me?.companyId}
          >
            Novo serviço
          </Button>
          <Link href="/dashboard">
            <Button variant="outline">Voltar</Button>
          </Link>
          <SignOutButton />
        </div>
      </div>
      <DataTable<Service>
        data={data ?? []}
        columns={columns}
        loading={isPending}
        emptyMessage="Nenhum serviço cadastrado."
        actions={(s) => (
          <Button
            variant="outline"
            className="h-7 px-2 text-xs"
            onClick={() => {
              setEditingService(s)
              setDialogOpen(true)
            }}
          >
            <Pencil className="size-3" />
            Editar
          </Button>
        )}
      />
      {me?.companyId && (
        <CreateServiceDialog
          open={dialogOpen}
          onClose={() => {
            setDialogOpen(false)
            setEditingService(undefined)
          }}
          companyId={me.companyId}
          service={editingService}
        />
      )}
    </div>
  )
}
