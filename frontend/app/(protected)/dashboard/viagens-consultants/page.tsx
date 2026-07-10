'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createConsultant,
  deleteConsultant,
  listConsultants,
  toggleConsultant,
  updateConsultant,
} from '@/lib/api/viagens/consultants'
import type { Consultant } from '@/profiles/viagens/viagens-types'

type FormState = { name: string; specialty: string; notes: string }
const EMPTY: FormState = { name: '', specialty: '', notes: '' }

/**
 * Consultores de viagem do ViagensBot (camada 8.18). CRUD via Modal, toggle ativo, exclusão protegida
 * (409 consultant_in_use → orienta a desativar). Catálogo simples — sem agenda.
 */
export default function ViagensConsultantsPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Consultant | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['viagens-consultants'],
    queryFn: () => listConsultants(),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      if (editing) {
        return updateConsultant(editing.id, {
          name: form.name,
          specialty: form.specialty || null,
          notes: form.notes || null,
        })
      }
      return createConsultant({
        name: form.name,
        specialty: form.specialty || null,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['viagens-consultants'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o consultor.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (c: Consultant) => toggleConsultant(c.id, !c.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['viagens-consultants'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteConsultant(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['viagens-consultants'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'consultant_in_use') {
        alert('Este consultor está atribuído a propostas — não pode ser excluído. Desative-o.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(c: Consultant) {
    setEditing(c)
    setForm({ name: c.name, specialty: c.specialty ?? '', notes: c.notes ?? '' })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Consultores"
        description="Equipe de consultores de viagem. Atribuir um consultor a uma proposta é opcional."
        actions={<Button onClick={openCreate}>Novo consultor</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os consultores.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum consultor cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((c) => (
            <div key={c.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{c.name}</span>
                  {!c.active && <Badge variant="muted">inativo</Badge>}
                </div>
                {c.specialty && <p className="text-xs text-muted-foreground">{c.specialty}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={c.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(c)}
                  />
                  ativo
                </label>
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
            </div>
          ))}
        </div>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar consultor' : 'Novo consultor'}
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
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Especialidade
            </label>
            <input
              value={form.specialty}
              onChange={(e) => setForm((f) => ({ ...f, specialty: e.target.value }))}
              placeholder="lua de mel, intercâmbio, cruzeiros…"
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
