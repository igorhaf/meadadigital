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
import { getMyFaqs, type Faq } from '@/lib/supabase/faqs'
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
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingFaq, setEditingFaq] = useState<Faq | undefined>(undefined)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

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
          <SignOutButton />
        </div>
      </div>
      <DataTable<Faq>
        data={data ?? []}
        columns={columns}
        loading={isPending}
        emptyMessage="Nenhuma FAQ cadastrada."
        actions={(f) => (
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
        )}
      />
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
