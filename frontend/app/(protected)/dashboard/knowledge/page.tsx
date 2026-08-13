'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FileText } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { KnowledgeUploadDialog } from '@/components/knowledge-upload-dialog'
import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getMe } from '@/lib/api/me'
import {
  deleteDocument,
  getMyDocuments,
  setDocumentActive,
  type KnowledgeDocument,
} from '@/lib/supabase/knowledge'

// Badge por status do processamento síncrono. 'ready' verde, 'processing' neutro
// (em voo), 'failed' vermelho (com o motivo no title, se houver).
function statusBadge(d: KnowledgeDocument) {
  if (d.status === 'ready') return <Badge variant="success">pronto</Badge>
  if (d.status === 'processing') return <Badge>processando…</Badge>
  // O Badge não aceita title; o span externo carrega o motivo da falha no hover.
  return (
    <span title={d.errorMessage ?? undefined}>
      <Badge variant="danger">falhou</Badge>
    </span>
  )
}

const columns: Column<KnowledgeDocument>[] = [
  { key: 'title', header: 'Documento' },
  { key: 'status', header: 'Status', render: statusBadge },
  {
    key: 'chunkCount',
    header: 'Trechos',
    render: (d) => (d.status === 'ready' ? String(d.chunkCount) : '—'),
  },
  {
    key: 'active',
    header: 'Ativo',
    render: (d) => (
      <Badge variant={d.active ? 'success' : 'danger'}>{d.active ? 'sim' : 'não'}</Badge>
    ),
  },
]

/**
 * Base de conhecimento (RAG, camada 5.13). Tela do tenant: envia PDFs que a IA usa como
 * contexto ao responder clientes. Super-admin não usa — se cair aqui, redireciona para
 * /dashboard (mesmo padrão das demais telas de tenant).
 *
 * Polling: enquanto QUALQUER documento estiver 'processing', refaz a lista a cada 5s
 * (refetchInterval condicional). O upload é síncrono no backend, mas se a aba ficar aberta
 * durante o processamento de outro upload, isto reflete a transição processing→ready/failed
 * sem refresh manual. Quando nada está processando, o polling desliga (false).
 */
export default function KnowledgePage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['my-knowledge'],
    queryFn: getMyDocuments,
    enabled: isTenant,
    // Polla só quando há documento em processamento; caso contrário desliga.
    refetchInterval: (query) =>
      (query.state.data ?? []).some((d) => d.status === 'processing') ? 5000 : false,
  })

  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => setDocumentActive(id, active),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-knowledge'] }),
    onError: (err) => console.error('setDocumentActive failed:', err),
  })

  const remove = useMutation({
    mutationFn: (id: string) => deleteDocument(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-knowledge'] }),
    onError: (err) => console.error('deleteDocument failed:', err),
  })

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return <div className="text-sm text-muted-foreground">Redirecionando…</div>
  }

  if (isError) {
    console.error('failed to load knowledge:', error)
    return (
      <div className="space-y-4">
        <PageHeader title="Conhecimento" />
        <p className="text-sm text-destructive">Erro ao carregar documentos.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Conhecimento"
        description="Envie PDFs (manuais, catálogos, políticas) que a IA usa como contexto ao responder."
        actions={
          <Button onClick={() => setDialogOpen(true)} disabled={!me?.companyId}>
            Enviar documento
          </Button>
        }
      />
      {isEmpty ? (
        <EmptyState
          icon={<FileText />}
          title="Sem documentos ainda"
          description="Envie PDFs (manuais, catálogos, políticas) e a IA passa a usá-los como contexto ao responder seus clientes."
          action={
            me?.companyId ? (
              <Button onClick={() => setDialogOpen(true)}>Enviar primeiro documento</Button>
            ) : undefined
          }
        />
      ) : (
        <DataTable<KnowledgeDocument>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum documento enviado."
          searchPlaceholder="Buscar documento…"
          searchFn={(d, q) => d.title.toLowerCase().includes(q)}
          actions={(d) => (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={
                  d.status === 'processing' ||
                  (toggleActive.isPending && toggleActive.variables?.id === d.id)
                }
                onClick={() => toggleActive.mutate({ id: d.id, active: !d.active })}
              >
                {d.active ? 'Desativar' : 'Ativar'}
              </Button>
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={remove.isPending && remove.variables === d.id}
                onClick={() => {
                  if (confirm(`Remover o documento "${d.title}"?`)) remove.mutate(d.id)
                }}
              >
                Remover
              </Button>
            </div>
          )}
        />
      )}
      {me?.companyId && (
        <KnowledgeUploadDialog open={dialogOpen} onClose={() => setDialogOpen(false)} />
      )}
    </div>
  )
}
