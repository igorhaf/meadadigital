'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createSalesperson,
  deleteSalesperson,
  listSalespeople,
  toggleSalesperson,
  updateSalesperson,
} from '@/lib/api/concessionaria/salespeople'
import type { Salesperson } from '@/profiles/concessionaria/concessionaria-types'

type FormState = { name: string; phone: string; notes: string }
const EMPTY: FormState = { name: '', phone: '', notes: '' }

/**
 * Vendedores do ConcessionariaBot (camada 8.17). Lista com toggle ativo inline, criação/edição via
 * Modal. A IA atribui vendedores ativos aos test-drives/leads; excluir é protegido (409
 * salesperson_in_use) — desative em vez disso.
 */
export default function ConcessionariaSalespeoplePage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Salesperson | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['concessionaria-salespeople'],
    queryFn: () => listSalespeople(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = { name: form.name, phone: form.phone || null, notes: form.notes || null }
      if (editing) return updateSalesperson(editing.id, payload)
      return createSalesperson(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['concessionaria-salespeople'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o vendedor.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (s: Salesperson) => toggleSalesperson(s.id, !s.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['concessionaria-salespeople'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteSalesperson(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['concessionaria-salespeople'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'salesperson_in_use') {
        alert(
          'Este vendedor tem test-drives ou leads — não pode ser excluído. Desative-o em vez disso.',
        )
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(s: Salesperson) {
    setEditing(s)
    setForm({ name: s.name, phone: s.phone ?? '', notes: s.notes ?? '' })
    setFormError(null)
    setModalOpen(true)
  }

  const people = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Vendedores"
        description="Equipe de vendas. A IA atribui os vendedores ativos aos test-drives e leads."
        actions={<Button onClick={openCreate}>Novo vendedor</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os vendedores.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : people.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum vendedor cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {people.map((s) => (
            <div key={s.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{s.name}</span>
                  {!s.active && <Badge variant="muted">inativo</Badge>}
                </div>
                <p className="truncate text-xs text-muted-foreground">
                  {[s.phone, s.notes].filter(Boolean).join(' · ') || '—'}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={s.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(s)}
                  />
                  ativo
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(s)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(s.id)}
                >
                  Excluir
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar vendedor' : 'Novo vendedor'}
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
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Telefone</label>
            <input
              value={form.phone}
              onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações
            </label>
            <textarea
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
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
