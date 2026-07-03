'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MessageSquareText, Pencil } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { Modal } from '@/components/ui/modal'
import { getMe } from '@/lib/api/me'
import {
  createSavedReply,
  deleteSavedReply,
  getMySavedReplies,
  updateSavedReply,
  type SavedReply,
} from '@/lib/api/saved-replies'
import { useResetWhen } from '@/lib/use-synced-form'

// title 1..80, body 1..2000 — espelha os CHECKs do banco (saved_replies).
const replySchema = z.object({
  title: z.string().trim().min(1, 'Informe o título').max(80, 'Máximo 80 caracteres'),
  body: z.string().trim().min(1, 'Informe o corpo').max(2000, 'Máximo 2000 caracteres'),
})

type ReplyForm = z.infer<typeof replySchema>

const columns: Column<SavedReply>[] = [
  { key: 'title', header: 'Título' },
  {
    key: 'body',
    header: 'Prévia',
    render: (r) => <span className="line-clamp-1 text-muted-foreground">{r.body}</span>,
  },
  {
    key: 'createdAt',
    header: 'Criada em',
    render: (r) => new Date(r.createdAt).toLocaleDateString('pt-BR'),
  },
]

/**
 * Respostas prontas da empresa do tenant (backend /admin/saved-replies), camada 5.22 #88.
 * CRUD: criar/editar via modal, remover via confirm(). Super-admin não usa: redireciona
 * para /dashboard. Consistente com a tela de tags.
 */
export default function SavedRepliesPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<SavedReply | undefined>(undefined)
  const [serverError, setServerError] = useState<string | null>(null)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['my-saved-replies'],
    queryFn: getMySavedReplies,
    enabled: isTenant,
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ReplyForm>({ resolver: zodResolver(replySchema) })

  useResetWhen(dialogOpen ? (editing?.id ?? 'create') : null, () => {
    reset({ title: editing?.title ?? '', body: editing?.body ?? '' })
    setServerError(null)
  })

  const isEdit = editing != null

  const saveMutation = useMutation({
    mutationFn: async (values: ReplyForm): Promise<void> => {
      if (isEdit) {
        await updateSavedReply(editing!.id, values.title, values.body)
      } else {
        await createSavedReply(values.title, values.body)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-saved-replies'] })
      closeDialog()
    },
    onError: (err) => {
      console.error(isEdit ? 'updateSavedReply failed:' : 'createSavedReply failed:', err)
      setServerError(
        isEdit
          ? 'Erro ao salvar alterações. Tente novamente.'
          : 'Erro ao criar resposta. Tente novamente.',
      )
    },
  })

  const removeMutation = useMutation({
    mutationFn: (id: string) => deleteSavedReply(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-saved-replies'] }),
    onError: (err) => console.error('deleteSavedReply failed:', err),
  })

  function openCreate() {
    setEditing(undefined)
    setDialogOpen(true)
  }

  function openEdit(r: SavedReply) {
    setEditing(r)
    setDialogOpen(true)
  }

  function closeDialog() {
    setDialogOpen(false)
    setEditing(undefined)
    setServerError(null)
  }

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
  }

  if (isError) {
    console.error('failed to load saved replies:', error)
    return (
      <div className="space-y-4">
        <PageHeader title="Respostas prontas" />
        <p className="text-sm text-destructive">Erro ao carregar respostas prontas.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Respostas prontas"
        description="Textos reutilizáveis para responder seus clientes mais rápido."
        actions={<Button onClick={openCreate}>Nova resposta</Button>}
      />

      {isEmpty ? (
        <EmptyState
          icon={<MessageSquareText />}
          title="Sem respostas prontas ainda"
          description="Crie textos reutilizáveis para responder mais rápido (ex.: saudação, horário de funcionamento, formas de pagamento)."
          action={<Button onClick={openCreate}>Criar primeira resposta</Button>}
        />
      ) : (
        <DataTable<SavedReply>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhuma resposta pronta cadastrada."
          searchPlaceholder="Buscar resposta…"
          searchFn={(r, q) => r.title.toLowerCase().includes(q) || r.body.toLowerCase().includes(q)}
          actions={(r) => (
            <div className="flex items-center gap-1.5">
              <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(r)}>
                <Pencil className="size-3" />
                Editar
              </Button>
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={removeMutation.isPending && removeMutation.variables === r.id}
                onClick={() => {
                  if (confirm(`Remover a resposta "${r.title}"?`)) {
                    removeMutation.mutate(r.id)
                  }
                }}
              >
                Remover
              </Button>
            </div>
          )}
        />
      )}

      <Modal
        open={dialogOpen}
        onClose={closeDialog}
        title={isEdit ? 'Editar resposta' : 'Nova resposta'}
      >
        <form onSubmit={handleSubmit((v) => saveMutation.mutate(v))} className="space-y-4">
          <div>
            <label htmlFor="reply-title" className="mb-1 block text-sm font-medium">
              Título
            </label>
            <input
              id="reply-title"
              {...register('title')}
              className="w-full rounded-md border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-ring focus:outline-none"
              placeholder="Ex.: Saudação"
            />
            {errors.title && (
              <p className="mt-1 text-xs text-destructive">{errors.title.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="reply-body" className="mb-1 block text-sm font-medium">
              Corpo
            </label>
            <textarea
              id="reply-body"
              {...register('body')}
              rows={5}
              className="w-full rounded-md border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-ring focus:outline-none"
              placeholder="Texto da resposta…"
            />
            {errors.body && <p className="mt-1 text-xs text-destructive">{errors.body.message}</p>}
          </div>

          {serverError && <p className="text-sm text-destructive">{serverError}</p>}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={closeDialog}>
              Cancelar
            </Button>
            <Button type="submit" disabled={isSubmitting || saveMutation.isPending}>
              {isEdit ? 'Salvar' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
