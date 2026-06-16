'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Tag as TagIcon } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { CreateTagDialog } from '@/components/create-tag-dialog'
import { PageHeader } from '@/components/layout/page-header'
import { TagChip } from '@/components/tag-color-picker'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getMe } from '@/lib/api/me'
import { deleteTag, getMyTags, type Tag } from '@/lib/supabase/tags'

const columns: Column<Tag>[] = [
  { key: 'name', header: 'Nome' },
  {
    key: 'color',
    header: 'Cor',
    render: (t) => <TagChip name={t.name} color={t.color} />,
  },
  {
    key: 'createdAt',
    header: 'Criada em',
    render: (t) => new Date(t.createdAt).toLocaleDateString('pt-BR'),
  },
]

/**
 * Tags/etiquetas da empresa do tenant (SDK + RLS), camada 5.14 #22. CRUD: criar/editar via
 * modal dual (CreateTagDialog), remover via soft delete com confirm(). Super-admin não usa:
 * redireciona para /dashboard. Criar exige companyId (do /admin/me) por causa do WITH CHECK.
 */
export default function TagsPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingTag, setEditingTag] = useState<Tag | undefined>(undefined)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['my-tags'],
    queryFn: getMyTags,
    enabled: isTenant,
  })

  const removeTag = useMutation({
    mutationFn: (id: string) => deleteTag(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-tags'] }),
    onError: (err) => console.error('deleteTag failed:', err),
  })

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
  }

  if (isError) {
    console.error('failed to load tags:', error)
    return (
      <div className="space-y-4">
        <PageHeader title="Tags" />
        <p className="text-sm text-destructive">Erro ao carregar tags.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tags"
        description="Crie etiquetas coloridas para organizar suas conversas."
        actions={
          <Button
            onClick={() => {
              setEditingTag(undefined)
              setDialogOpen(true)
            }}
            disabled={!me?.companyId}
          >
            Nova tag
          </Button>
        }
      />
      {isEmpty ? (
        <EmptyState
          icon={<TagIcon />}
          title="Sem tags ainda"
          description="Crie etiquetas coloridas para organizar suas conversas (ex.: Cliente VIP, Aguardando pagamento)."
          action={
            me?.companyId ? (
              <Button
                onClick={() => {
                  setEditingTag(undefined)
                  setDialogOpen(true)
                }}
              >
                Criar primeira tag
              </Button>
            ) : undefined
          }
        />
      ) : (
        <DataTable<Tag>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhuma tag cadastrada."
          searchPlaceholder="Buscar tag…"
          searchFn={(t, q) => t.name.toLowerCase().includes(q)}
          actions={(t) => (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                onClick={() => {
                  setEditingTag(t)
                  setDialogOpen(true)
                }}
              >
                <Pencil className="size-3" />
                Editar
              </Button>
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={removeTag.isPending && removeTag.variables === t.id}
                onClick={() => {
                  if (confirm(`Remover a tag "${t.name}"? As conversas perdem esta etiqueta.`)) {
                    removeTag.mutate(t.id)
                  }
                }}
              >
                Remover
              </Button>
            </div>
          )}
        />
      )}
      {me?.companyId && (
        <CreateTagDialog
          open={dialogOpen}
          onClose={() => {
            setDialogOpen(false)
            setEditingTag(undefined)
          }}
          companyId={me.companyId}
          tag={editingTag}
        />
      )}
    </div>
  )
}
