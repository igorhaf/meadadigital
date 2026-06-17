'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { createCase, listCases } from '@/lib/api/legal/cases'
import { listClients } from '@/lib/api/legal/clients'
import { LEGAL_CASE_STATUSES, statusLabel, type LegalCaseStatusId } from '@/profiles/legal/legal-case-status'
import { cnjDigits, formatCnj, type LegalCase } from '@/profiles/legal/legal-types'

function StatusBadge({ status }: { status: LegalCaseStatusId }) {
  const variant =
    status === 'ativo' ? 'success' : status === 'encerrado' ? 'muted' : status === 'arquivado' ? 'info' : 'warning'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  legalClientId: string
  cnj: string
  title: string
  court: string
  forum: string
  subject: string
}

const EMPTY: FormState = { legalClientId: '', cnj: '', title: '', court: '', forum: '', subject: '' }

/**
 * Processos do escritório (camada 7.2). Lista com filtro por status + busca, criação via Modal
 * (CNJ com máscara, validado no backend mód 97).
 */
export default function CasesPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['legal-cases', status, search, page],
    queryFn: () => listCases({ status: status || undefined, search: search || undefined, page, pageSize: 20 }),
    placeholderData: keepPreviousData,
  })

  // Clientes para o select do Modal.
  const clients = useQuery({ queryKey: ['legal-clients-all'], queryFn: () => listClients() })

  const createMutation = useMutation({
    mutationFn: () =>
      createCase({
        legalClientId: form.legalClientId,
        cnjNumber: cnjDigits(form.cnj),
        title: form.title,
        court: form.court || null,
        forum: form.forum || null,
        subject: form.subject || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['legal-cases'] })
      setModalOpen(false)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_cnj') setFormError('CNJ inválido (verifique o dígito verificador).')
      else if (e instanceof ApiError && e.reason === 'duplicate_cnj') setFormError('Já existe um processo com este CNJ.')
      else setFormError('Erro ao criar o processo.')
    },
  })

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Processos"
        description="Processos do escritório. A IA usa esta lista para responder os clientes."
        actions={<Button onClick={() => { setForm(EMPTY); setFormError(null); setModalOpen(true) }}>Novo processo</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <button onClick={() => { setStatus(''); setPage(0) }}
          className={`rounded-full border px-3 py-1 text-xs ${status === '' ? 'border-primary bg-primary/10' : 'border-border'}`}>
          Todos
        </button>
        {LEGAL_CASE_STATUSES.map((s) => (
          <button key={s.id} onClick={() => { setStatus(s.id); setPage(0) }}
            className={`rounded-full border px-3 py-1 text-xs ${status === s.id ? 'border-primary bg-primary/10' : 'border-border'}`}>
            {s.label}
          </button>
        ))}
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          placeholder="Buscar por título, CNJ ou cliente…"
          className="ml-auto w-64 rounded-md border border-border bg-background px-3 py-2 text-sm" />
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os processos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (data?.items.length ?? 0) === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum processo encontrado.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {data!.items.map((c: LegalCase) => (
            <Link key={c.id} href={`/dashboard/cases/${c.id}`}>
              <Card className="space-y-1 p-4 transition-colors hover:bg-muted/40">
                <div className="flex items-center justify-between">
                  <span className="font-medium">{c.title}</span>
                  <StatusBadge status={c.status} />
                </div>
                <p className="font-mono text-xs text-muted-foreground">{c.cnjNumberFormatted}</p>
                <p className="text-sm text-muted-foreground">{c.legalClientName}</p>
                <p className="text-xs text-muted-foreground">
                  {c.updatesCount} andamento(s) · atualizado {new Date(c.updatedAt).toLocaleDateString('pt-BR')}
                </p>
              </Card>
            </Link>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>Página {page + 1} de {totalPages} · {total} processo(s)</span>
          <div className="flex gap-1">
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}>←</Button>
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}>→</Button>
          </div>
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Novo processo" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); createMutation.mutate() }}>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Cliente</label>
            <select value={form.legalClientId} onChange={(e) => setForm((f) => ({ ...f, legalClientId: e.target.value }))} required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
              <option value="">Selecione…</option>
              {(clients.data?.items ?? []).map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Número CNJ</label>
            <input value={form.cnj} onChange={(e) => setForm((f) => ({ ...f, cnj: formatCnj(e.target.value) }))} required
              placeholder="NNNNNNN-DD.AAAA.J.TR.OOOO"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm font-mono" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Título</label>
            <input value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} required
              maxLength={200} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Vara</label>
              <input value={form.court} onChange={(e) => setForm((f) => ({ ...f, court: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Fórum</label>
              <input value={form.forum} onChange={(e) => setForm((f) => ({ ...f, forum: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Matéria</label>
              <input value={form.subject} onChange={(e) => setForm((f) => ({ ...f, subject: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Criando…' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
