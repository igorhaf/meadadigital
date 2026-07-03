'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createProfessional,
  deleteProfessional,
  listProfessionals,
  toggleProfessional,
  updateProfessional,
} from '@/lib/api/otica/professionals'
import type { Professional } from '@/profiles/otica/otica-types'

type FormState = { name: string; notes: string }
const EMPTY: FormState = { name: '', notes: '' }

/**
 * Optometristas (camada 8.12). CRUD via Modal, toggle ativo, exclusão protegida (409
 * professional_in_use — orienta a desativar). Rota /dashboard/otica-professionals.
 */
export default function OticaProfessionalsPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Professional | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['otica-professionals'],
    queryFn: () => listProfessionals(),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = { name: form.name, notes: form.notes || null }
      if (editing) return updateProfessional(editing.id, payload)
      return createProfessional(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['otica-professionals'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o optometrista.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (p: Professional) => toggleProfessional(p.id, !p.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['otica-professionals'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteProfessional(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['otica-professionals'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'professional_in_use') {
        alert('Este profissional tem exames — não pode ser excluído. Desative-o.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(p: Professional) {
    setEditing(p)
    setForm({ name: p.name, notes: p.notes ?? '' })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Optometristas"
        description="Equipe que faz os exames de vista. A IA agenda na agenda de cada um."
        actions={<Button onClick={openCreate}>Novo optometrista</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os optometristas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum optometrista cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{p.name}</span>
                  {!p.active && <Badge variant="muted">inativo</Badge>}
                </div>
                {p.notes && <p className="truncate text-xs text-muted-foreground">{p.notes}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={p.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(p)}
                  />
                  ativo
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(p)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(p.id)}
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
        title={editing ? 'Editar optometrista' : 'Novo optometrista'}
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
