'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { HelpCircle, Pencil } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getMe } from '@/lib/api/me'
import { getMyFaqs, setFaqActive, type Faq } from '@/lib/supabase/faqs'
import { CreateFaqDialog } from './create-faq-dialog'

const columns: Column<Faq>[] = [
  { key: 'question', header: 'Pergunta' },
  {
    key: 'answer',
    header: 'Resposta',
    render: (f) => (f.answer.length > 80 ? f.answer.slice(0, 80) + '…' : f.answer),
  },
  {
    key: 'active',
    header: 'Ativo',
    render: (f) => (
      <Badge variant={f.active ? 'success' : 'danger'}>{f.active ? 'sim' : 'não'}</Badge>
    ),
  },
]

/**
 * FAQs da empresa do tenant (SDK + RLS). Super-admin NÃO usa esta tela: se cair aqui,
 * redireciona para /dashboard (padrão inverso do hub). A criação exige companyId
 * (do /admin/me) por causa do WITH CHECK do RLS no INSERT.
 */
export default function FaqsPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingFaq, setEditingFaq] = useState<Faq | undefined>(undefined)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  // Toggle ativo/inativo (camada 5.6). Invalida a lista no sucesso (padrão do projeto —
  // sem optimistic update). variables.id permite desabilitar só o botão da linha em voo.
  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => setFaqActive(id, active),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-faqs'] })
    },
    onError: (err) => console.error('setFaqActive failed:', err),
  })

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['my-faqs'],
    queryFn: getMyFaqs,
    enabled: isTenant, // só busca quando confirmado tenant (evita 0 rows para super-admin)
  })

  // Estado vazio elevado (5.8): lista carregada, sem erro, zero registros.
  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-5xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  if (isError) {
    console.error('failed to load faqs:', error)
    return (
      <div className="mx-auto max-w-5xl p-8">
        <h1 className="mb-2 text-xl font-semibold">FAQs</h1>
        <p className="mb-4 text-sm text-destructive">Erro ao carregar FAQs.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">FAQs</h1>
        <div className="flex items-center gap-2">
          <Button
            onClick={() => {
              setEditingFaq(undefined)
              setDialogOpen(true)
            }}
            disabled={!me?.companyId}
          >
            Nova FAQ
          </Button>
          <Link href="/dashboard">
            <Button variant="outline">Voltar</Button>
          </Link>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </div>
      {isEmpty ? (
        <EmptyState
          icon={<HelpCircle />}
          title="Sem FAQs ainda"
          description="A IA usa as FAQs como conhecimento ao responder seus clientes."
          action={
            me?.companyId ? (
              <Button
                onClick={() => {
                  setEditingFaq(undefined)
                  setDialogOpen(true)
                }}
              >
                Criar primeira FAQ
              </Button>
            ) : undefined
          }
        />
      ) : (
        <DataTable<Faq>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhuma FAQ cadastrada."
          actions={(f) => (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                onClick={() => {
                  setEditingFaq(f)
                  setDialogOpen(true)
                }}
              >
                <Pencil className="size-3" />
                Editar
              </Button>
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={toggleActive.isPending && toggleActive.variables?.id === f.id}
                onClick={() => toggleActive.mutate({ id: f.id, active: !f.active })}
              >
                {f.active ? 'Desativar' : 'Ativar'}
              </Button>
            </div>
          )}
        />
      )}
      {me?.companyId && (
        <CreateFaqDialog
          open={dialogOpen}
          onClose={() => {
            setDialogOpen(false)
            setEditingFaq(undefined)
          }}
          companyId={me.companyId}
          faq={editingFaq}
        />
      )}
    </div>
  )
}
