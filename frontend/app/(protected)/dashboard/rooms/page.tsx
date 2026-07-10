'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { createRoom, deleteRoom, listRooms, toggleRoom, updateRoom } from '@/lib/api/pousada/rooms'
import { formatPrice, type Room } from '@/profiles/pousada/pousada-types'

type FormState = {
  name: string
  capacity: string
  price: string // reais
  description: string
}
const EMPTY: FormState = { name: '', capacity: '2', price: '', description: '' }

/**
 * Quartos do PousadaBot (camada 7.6). Lista com toggle active inline, CRUD via Modal. A IA oferece
 * os quartos ativos ao reservar; o conflito de reserva é por quarto (overlap de intervalos).
 */
export default function RoomsPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Room | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['pousada-rooms'],
    queryFn: () => listRooms(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        capacity: Math.round(Number(form.capacity)),
        nightlyRateCents: Math.round(Number(form.price || '0') * 100),
        description: form.description || null,
      }
      if (editing) return updateRoom(editing.id, payload)
      return createRoom(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pousada-rooms'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o quarto.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (r: Room) => toggleRoom(r.id, !r.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pousada-rooms'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteRoom(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pousada-rooms'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'room_in_use') {
        alert('Este quarto tem reservas — não pode ser excluído. Desative-o em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(r: Room) {
    setEditing(r)
    setForm({
      name: r.name,
      capacity: String(r.capacity),
      price: String(r.nightlyRateCents / 100),
      description: r.description ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const rooms = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Quartos"
        description="Quartos da pousada. A IA oferece os quartos ativos por número de hóspedes e datas."
        actions={<Button onClick={openCreate}>Novo quarto</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os quartos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : rooms.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum quarto cadastrado ainda.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {rooms.map((r) => (
            <div key={r.id} className="space-y-1 rounded-lg border border-border p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{r.name}</span>
                  <Badge variant="muted">{r.capacity} hóspedes</Badge>
                  {!r.active && <Badge variant="muted">inativo</Badge>}
                </div>
                <span className="text-sm tabular-nums">
                  {formatPrice(r.nightlyRateCents)}/noite
                </span>
              </div>
              {r.description && <p className="text-xs text-muted-foreground">{r.description}</p>}
              <div className="flex items-center gap-3 pt-1">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={r.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(r)}
                  />
                  ativo
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(r)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(r.id)}
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
        title={editing ? 'Editar quarto' : 'Novo quarto'}
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
              placeholder="Standard, Suíte, Família…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Capacidade
              </label>
              <input
                type="number"
                min="1"
                max="20"
                value={form.capacity}
                required
                onChange={(e) => setForm((f) => ({ ...f, capacity: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Diária (R$)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.price}
                required
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Descrição
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={2}
              placeholder="A IA só promete o que estiver aqui."
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
