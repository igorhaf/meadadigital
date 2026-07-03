'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { createClient, deleteClient, listClients, updateClient } from '@/lib/api/legal/clients'
import type { LegalClient } from '@/profiles/legal/legal-types'

type FormState = {
  name: string
  email: string
  phone: string
  document: string
  notes: string
}

const EMPTY: FormState = { name: '', email: '', phone: '', document: '', notes: '' }

/**
 * Clientes do escritório (camada 7.2). CRUD + busca. O cliente é desacoplado do contato do
 * WhatsApp; o badge "WhatsApp" indica quando há vínculo (contactId).
 */
export default function ClientsPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<LegalClient | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)

  const { data, isPending, isError } = useQuery({
    queryKey: ['legal-clients', search],
    queryFn: () => listClients(search || undefined),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        email: form.email || null,
        phone: form.phone || null,
        document: form.document || null,
        notes: form.notes || null,
      }
      if (editing) return updateClient(editing.id, payload)
      return createClient(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['legal-clients'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteClient(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['legal-clients'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'client_in_use') {
        alert('Este cliente tem processos vinculados — não pode ser excluído.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setModalOpen(true)
  }

  function openEdit(c: LegalClient) {
    setEditing(c)
    setForm({
      name: c.name,
      email: c.email ?? '',
      phone: c.phone ?? '',
      document: c.document ?? '',
      notes: c.notes ?? '',
    })
    setModalOpen(true)
  }

  const columns: Column<LegalClient>[] = [
    { key: 'name', header: 'Nome', render: (c) => <span className="font-medium">{c.name}</span> },
    { key: 'email', header: 'Email', render: (c) => c.email ?? '—' },
    { key: 'phone', header: 'Telefone', render: (c) => c.phone ?? '—' },
    { key: 'document', header: 'CPF/CNPJ', render: (c) => c.document ?? '—' },
    {
      key: 'contactId',
      header: 'WhatsApp',
      render: (c) =>
        c.contactId ? (
          <Badge variant="success">vinculado</Badge>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        ),
    },
    {
      key: 'actions',
      header: '',
      render: (c) => (
        <div className="flex justify-end gap-1">
          <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(c)}>
            Editar
          </Button>
          <Button
            variant="outline"
            className="h-7 px-2 text-xs"
            disabled={deleteMutation.isPending}
            onClick={() => deleteMutation.mutate(c.id)}
          >
            Excluir
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Clientes"
        description="Clientes do escritório. Vincule ao WhatsApp para a IA reconhecer o cliente pelo telefone."
        actions={<Button onClick={openCreate}>Novo cliente</Button>}
      />

      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Buscar por nome, email, telefone ou CPF…"
        className="w-full max-w-sm rounded-md border border-border bg-background px-3 py-2 text-sm"
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os clientes.</p>
      ) : (
        <DataTable<LegalClient>
          data={data?.items ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum cliente cadastrado."
        />
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar cliente' : 'Novo cliente'}
        size="md"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              required
              maxLength={200}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Email</label>
              <input
                type="email"
                value={form.email}
                onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Telefone
              </label>
              <input
                value={form.phone}
                onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">CPF/CNPJ</label>
            <input
              value={form.document}
              onChange={(e) => setForm((f) => ({ ...f, document: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Notas</label>
            <textarea
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {saveMutation.isError && (
            <p className="text-sm text-destructive">Erro ao salvar o cliente.</p>
          )}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Salvando…' : editing ? 'Salvar' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
