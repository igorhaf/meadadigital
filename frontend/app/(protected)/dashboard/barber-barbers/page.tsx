'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createBarber,
  deleteBarber,
  listBarbers,
  toggleBarber,
  updateBarber,
} from '@/lib/api/barbearia/barbers'
import { ApiError } from '@/lib/api/client'
import type { Barber } from '@/profiles/barbearia/barber-types'

type FormState = { name: string; specialty: string; notes: string }
const EMPTY: FormState = { name: '', specialty: '', notes: '' }

/**
 * Barbeiros do BarbeariaBot (camada 8.1). Rota /dashboard/barber-barbers (slug "barbers" pra não
 * colidir com a tela genérica /dashboard/professionals do salon). O conflito de agenda é por barbeiro.
 */
export default function BarberBarbersPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Barber | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['barber-barbers'],
    queryFn: () => listBarbers(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        specialty: form.specialty || null,
        notes: form.notes || null,
      }
      return editing ? updateBarber(editing.id, payload) : createBarber(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['barber-barbers'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o barbeiro.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (b: Barber) => toggleBarber(b.id, !b.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['barber-barbers'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteBarber(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['barber-barbers'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'barber_in_use') {
        alert(
          'Este barbeiro tem agendamentos ou tickets de fila — não pode ser excluído. Desative-o em vez disso.',
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
  function openEdit(b: Barber) {
    setEditing(b)
    setForm({ name: b.name, specialty: b.specialty ?? '', notes: b.notes ?? '' })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Barbeiros"
        description="Quem atende na barbearia. A IA oferece estes barbeiros e a agenda é por barbeiro."
        actions={<Button onClick={openCreate}>Novo barbeiro</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os barbeiros.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum barbeiro cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((b) => (
            <div key={b.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{b.name}</span>
                  {!b.active && <Badge variant="muted">inativo</Badge>}
                </div>
                {b.specialty && <p className="text-xs text-muted-foreground">{b.specialty}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={b.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(b)}
                  />
                  ativo
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(b)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(b.id)}
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
        title={editing ? 'Editar barbeiro' : 'Novo barbeiro'}
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
              Especialidade (opcional)
            </label>
            <input
              value={form.specialty}
              onChange={(e) => setForm((f) => ({ ...f, specialty: e.target.value }))}
              placeholder="corte/barba, degradê, freestyle…"
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
