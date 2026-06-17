'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { use, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import {
  addUpdate,
  deleteUpdate,
  getCase,
  updateCaseStatus,
} from '@/lib/api/legal/cases'
import {
  LEGAL_CASE_STATUSES,
  statusLabel,
  type LegalCaseStatusId,
} from '@/profiles/legal/legal-case-status'
import type { LegalCaseUpdate } from '@/profiles/legal/legal-types'

/**
 * Detalhe de um processo (camada 7.2): cabeçalho + status + cliente + timeline de andamentos
 * + detalhes. Mudar status notifica o cliente (se vinculado ao WhatsApp).
 */
export default function CaseDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const qc = useQueryClient()
  const [statusTarget, setStatusTarget] = useState<LegalCaseStatusId | null>(null)
  const [updateModalOpen, setUpdateModalOpen] = useState(false)
  const [updateForm, setUpdateForm] = useState({ title: '', body: '', occurredAt: '' })
  const [deleteTarget, setDeleteTarget] = useState<LegalCaseUpdate | null>(null)

  const { data: c, isPending, isError } = useQuery({
    queryKey: ['legal-case', id],
    queryFn: () => getCase(id),
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: LegalCaseStatusId) => updateCaseStatus(id, newStatus),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['legal-case', id] }); setStatusTarget(null) },
  })

  const addUpdateMutation = useMutation({
    mutationFn: () => addUpdate(id, {
      title: updateForm.title,
      body: updateForm.body || null,
      occurredAt: updateForm.occurredAt ? new Date(updateForm.occurredAt).toISOString() : null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['legal-case', id] })
      setUpdateModalOpen(false)
      setUpdateForm({ title: '', body: '', occurredAt: '' })
    },
  })

  const deleteUpdateMutation = useMutation({
    mutationFn: (updateId: string) => deleteUpdate(id, updateId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['legal-case', id] }); setDeleteTarget(null) },
  })

  if (isPending) return <div className="space-y-6"><PageHeader title="Carregando…" /></div>
  if (isError || !c) {
    return (
      <div className="space-y-6">
        <PageHeader title="Processo" />
        <p className="text-sm text-destructive">Erro ao carregar o processo.</p>
        <Link href="/dashboard/cases"><Button variant="outline">Voltar</Button></Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={c.title}
        breadcrumb={[{ label: 'Processos', href: '/dashboard/cases' }, { label: c.title }]}
        actions={
          <select
            value=""
            onChange={(e) => { if (e.target.value) setStatusTarget(e.target.value as LegalCaseStatusId) }}
            className="rounded-md border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="">Mudar status…</option>
            {LEGAL_CASE_STATUSES.filter((s) => s.id !== c.status).map((s) => (
              <option key={s.id} value={s.id}>{s.label}</option>
            ))}
          </select>
        }
      />

      <div className="flex items-center gap-2">
        <Badge variant={c.status === 'ativo' ? 'success' : 'muted'}>{statusLabel(c.status)}</Badge>
        <span className="font-mono text-xs text-muted-foreground">{c.cnjNumberFormatted}</span>
      </div>

      <Card>
        <Section title="Cliente">
          <Link href={`/dashboard/clients`} className="text-sm font-medium hover:underline">
            {c.legalClientName}
          </Link>
        </Section>
      </Card>

      <Card>
        <div className="mb-3 flex items-center justify-between">
          <Section title="Andamentos"><span /></Section>
          <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => setUpdateModalOpen(true)}>
            Novo andamento
          </Button>
        </div>
        {c.updates.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nenhum andamento registrado.</p>
        ) : (
          <ol className="space-y-3 border-l border-border pl-4">
            {c.updates.map((u) => (
              <li key={u.id} className="relative">
                <span className="absolute -left-[21px] top-1.5 size-2 rounded-full bg-primary" />
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-medium">{u.title}</p>
                    {u.body && <p className="text-xs text-muted-foreground">{u.body}</p>}
                    <p className="text-xs text-muted-foreground">
                      {new Date(u.occurredAt).toLocaleDateString('pt-BR')}
                    </p>
                  </div>
                  <Button variant="outline" className="h-6 px-2 text-xs" onClick={() => setDeleteTarget(u)}>
                    Excluir
                  </Button>
                </div>
              </li>
            ))}
          </ol>
        )}
      </Card>

      <Card>
        <Section title="Detalhes">
          <dl className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
            <div><dt className="text-xs text-muted-foreground">Vara</dt><dd>{c.court ?? '—'}</dd></div>
            <div><dt className="text-xs text-muted-foreground">Fórum</dt><dd>{c.forum ?? '—'}</dd></div>
            <div><dt className="text-xs text-muted-foreground">Matéria</dt><dd>{c.subject ?? '—'}</dd></div>
            <div className="sm:col-span-2"><dt className="text-xs text-muted-foreground">Descrição</dt><dd>{c.description ?? '—'}</dd></div>
          </dl>
        </Section>
      </Card>

      {/* Confirmação de mudança de status */}
      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget) : ''}"?`}
        description="O cliente será notificado automaticamente, se houver vínculo com o WhatsApp."
        confirmLabel="Mudar status"
        destructive={false}
        loading={statusMutation.isPending}
        onConfirm={() => { if (statusTarget) statusMutation.mutate(statusTarget) }}
      />

      {/* Excluir andamento */}
      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Excluir andamento?"
        description="Esta ação não pode ser desfeita."
        confirmLabel="Excluir"
        loading={deleteUpdateMutation.isPending}
        onConfirm={() => { if (deleteTarget) deleteUpdateMutation.mutate(deleteTarget.id) }}
      />

      {/* Novo andamento */}
      <Modal open={updateModalOpen} onClose={() => setUpdateModalOpen(false)} title="Novo andamento" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); addUpdateMutation.mutate() }}>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Título</label>
            <input value={updateForm.title} onChange={(e) => setUpdateForm((f) => ({ ...f, title: e.target.value }))} required
              maxLength={200} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Descrição (opcional)</label>
            <textarea value={updateForm.body} onChange={(e) => setUpdateForm((f) => ({ ...f, body: e.target.value }))}
              rows={3} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Data do andamento</label>
            <input type="date" value={updateForm.occurredAt}
              onChange={(e) => setUpdateForm((f) => ({ ...f, occurredAt: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            <p className="mt-1 text-xs text-muted-foreground">Em branco = hoje.</p>
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setUpdateModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={addUpdateMutation.isPending}>
              {addUpdateMutation.isPending ? 'Salvando…' : 'Adicionar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
