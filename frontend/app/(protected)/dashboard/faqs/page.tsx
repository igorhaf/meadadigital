'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { HelpCircle, Lightbulb, Pencil, Plus } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getMe } from '@/lib/api/me'
import { getFaqSuggestions } from '@/lib/supabase/faq-suggestions'
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
  // Pergunta pré-preenchida ao abrir a criação a partir de uma sugestão da IA (5.18 #54).
  const [initialQuestion, setInitialQuestion] = useState<string | undefined>(undefined)

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

  // Sugestões da IA (5.18 #54): perguntas recentes de conversas que caíram para humano.
  // Mesmo gate de tenant. Falha silenciosa (a seção apenas não aparece) — é auxiliar.
  const { data: suggestions } = useQuery({
    queryKey: ['faq-suggestions'],
    queryFn: getFaqSuggestions,
    enabled: isTenant,
  })

  // Abre a criação pré-preenchida com a pergunta da sugestão.
  function openCreateFromSuggestion(content: string) {
    setEditingFaq(undefined)
    setInitialQuestion(content)
    setDialogOpen(true)
  }

  // Estado vazio elevado (5.8): lista carregada, sem erro, zero registros.
  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
  }

  if (isError) {
    console.error('failed to load faqs:', error)
    return (
      <div className="space-y-4">
        <PageHeader title="FAQs" />

        {toggleActive.isError && (
          <p className="text-sm text-destructive">
            Erro ao salvar a alteração da FAQ. Tente novamente.
          </p>
        )}
        <p className="text-sm text-destructive">Erro ao carregar FAQs.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="FAQs"
        description="A IA usa as FAQs como conhecimento ao responder seus clientes."
        actions={
          <Button
            onClick={() => {
              setEditingFaq(undefined)
              setInitialQuestion(undefined)
              setDialogOpen(true)
            }}
            disabled={!me?.companyId}
          >
            Nova FAQ
          </Button>
        }
      />
      {(suggestions?.length ?? 0) > 0 && (
        <Card>
          <Section
            title="Sugestões da IA"
            description="Perguntas recentes de clientes em conversas que passaram para atendimento humano — candidatas a virar FAQ."
            actions={<Lightbulb className="size-4 text-muted-foreground" />}
          >
            <ul className="space-y-2">
              {suggestions!.map((s) => (
                <li
                  key={s.conversationId + s.lastAt}
                  className="flex items-center justify-between gap-3 rounded-md border border-border px-3 py-2"
                >
                  <span className="min-w-0 flex-1 truncate text-sm" title={s.content}>
                    {s.content}
                  </span>
                  <Button
                    variant="outline"
                    className="h-7 shrink-0 px-2 text-xs"
                    disabled={!me?.companyId}
                    onClick={() => openCreateFromSuggestion(s.content)}
                  >
                    <Plus className="size-3" />
                    Criar FAQ
                  </Button>
                </li>
              ))}
            </ul>
          </Section>
        </Card>
      )}
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
                  setInitialQuestion(undefined)
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
                  setInitialQuestion(undefined)
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
            setInitialQuestion(undefined)
          }}
          companyId={me.companyId}
          faq={editingFaq}
          initialQuestion={initialQuestion}
        />
      )}
    </div>
  )
}
