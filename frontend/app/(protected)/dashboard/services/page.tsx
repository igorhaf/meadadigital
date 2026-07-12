'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Briefcase, Pencil } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getMe } from '@/lib/api/me'
import { getMyServices, setServiceActive, type Service } from '@/lib/supabase/services'

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
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingService, setEditingService] = useState<Service | undefined>(undefined)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  // Toggle ativo/inativo (camada 5.6). Invalida a lista no sucesso (padrão do projeto —
  // sem optimistic update). variables.id permite desabilitar só o botão da linha em voo.
  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => setServiceActive(id, active),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-services'] })
    },
    onError: (err) => console.error('setServiceActive failed:', err),
  })

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

  // Estado vazio elevado (5.8): lista carregada, sem erro, zero registros.
  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
  }

  if (isError) {
    console.error('failed to load services:', error)
    return (
      <div className="space-y-4">
        <PageHeader title="Serviços" />

        {toggleActive.isError && (
          <p className="text-sm text-destructive">
            Erro ao salvar a alteração do serviço. Tente novamente.
          </p>
        )}
        <p className="text-sm text-destructive">Erro ao carregar serviços.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Serviços"
        description="Cadastre os serviços que a IA informa aos clientes (nome, descrição e preço)."
        actions={
          <Button
            onClick={() => {
              setEditingService(undefined)
              setDialogOpen(true)
            }}
            disabled={!me?.companyId}
          >
            Novo serviço
          </Button>
        }
      />
      {isEmpty ? (
        <EmptyState
          icon={<Briefcase />}
          title="Sem serviços ainda"
          description="Cadastre seus serviços para a IA informar nome, descrição e preço aos clientes."
          action={
            me?.companyId ? (
              <Button
                onClick={() => {
                  setEditingService(undefined)
                  setDialogOpen(true)
                }}
              >
                Criar primeiro serviço
              </Button>
            ) : undefined
          }
        />
      ) : (
        <DataTable<Service>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum serviço cadastrado."
          actions={(s) => (
            <div className="flex items-center gap-1.5">
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
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={toggleActive.isPending && toggleActive.variables?.id === s.id}
                onClick={() => toggleActive.mutate({ id: s.id, active: !s.active })}
              >
                {s.active ? 'Desativar' : 'Ativar'}
              </Button>
            </div>
          )}
        />
      )}
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
