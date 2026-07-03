'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createService,
  deleteService,
  listServices,
  toggleService,
  updateService,
} from '@/lib/api/pet/services'
import {
  formatPrice,
  speciesLabel,
  type PetService,
  type PetSpecies,
} from '@/profiles/pet/pet-types'

type FormState = {
  name: string
  category: string
  durationMinutes: string
  price: string // reais (vazio = sem preço)
  speciesRestriction: '' | PetSpecies // '' = qualquer espécie
  description: string
}
const EMPTY: FormState = {
  name: '',
  category: '',
  durationMinutes: '45',
  price: '',
  speciesRestriction: '',
  description: '',
}

/** Agrupa serviços por categoria (null vira "Sem categoria"). */
function groupByCategory(items: PetService[]): { category: string; items: PetService[] }[] {
  const map = new Map<string, PetService[]>()
  for (const o of items) {
    const c = o.category?.trim() || 'Sem categoria'
    if (!map.has(c)) map.set(c, [])
    map.get(c)!.push(o)
  }
  return [...map.entries()].map(([category, items]) => ({ category, items }))
}

/**
 * Serviços do PetBot (camada 7.8). Rota /dashboard/pet-services. Agrupados por categoria, toggle
 * ativo, CRUD via Modal. Duração por serviço (snapshot no agendamento), preço opcional e
 * RESTRIÇÃO DE ESPÉCIE opcional (cão/gato/outro) — a IA valida o fit serviço↔animal.
 */
export default function PetServicesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<PetService | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['pet-services'],
    queryFn: () => listServices(),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      const species = form.speciesRestriction === '' ? null : form.speciesRestriction
      if (editing) {
        return updateService(editing.id, {
          name: form.name,
          category: form.category || null,
          durationMinutes: Math.round(Number(form.durationMinutes)),
          // null limpa o preço; valor grava (backend lê o JSON null como "limpar").
          priceCents: form.price.trim() === '' ? null : Math.round(Number(form.price) * 100),
          // sempre enviamos speciesRestriction no edit (chave presente): null limpa, valor restringe.
          speciesRestriction: species,
          description: form.description || null,
        })
      }
      return createService({
        name: form.name,
        category: form.category || null,
        durationMinutes: Math.round(Number(form.durationMinutes)),
        priceCents: form.price.trim() === '' ? null : Math.round(Number(form.price) * 100),
        speciesRestriction: species,
        description: form.description || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pet-services'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o serviço.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (o: PetService) => toggleService(o.id, !o.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pet-services'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteService(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pet-services'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'service_in_use') {
        alert('Este serviço tem agendamentos — não pode ser excluído. Desative-o em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(o: PetService) {
    setEditing(o)
    setForm({
      name: o.name,
      category: o.category ?? '',
      durationMinutes: String(o.durationMinutes),
      price: o.priceCents != null ? String(o.priceCents / 100) : '',
      speciesRestriction: o.speciesRestriction ?? '',
      description: o.description ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const groups = groupByCategory(data?.items ?? [])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Serviços"
        description="Catálogo de serviços do pet shop. A IA oferece estes serviços ao atender; a restrição de espécie limita quem pode agendar."
        actions={<Button onClick={openCreate}>Novo serviço</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os serviços.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : groups.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum serviço cadastrado ainda.</p>
      ) : (
        <div className="space-y-8">
          {groups.map((g) => (
            <section key={g.category} className="space-y-2">
              <h2 className="text-sm font-semibold text-muted-foreground">{g.category}</h2>
              <div className="divide-y divide-border rounded-lg border border-border">
                {g.items.map((o) => (
                  <div key={o.id} className="flex items-center justify-between gap-3 px-4 py-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{o.name}</span>
                        {o.speciesRestriction && (
                          <Badge variant="info">{speciesLabel(o.speciesRestriction)}</Badge>
                        )}
                        {!o.active && <Badge variant="muted">inativo</Badge>}
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {o.durationMinutes} min · {formatPrice(o.priceCents)}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-3">
                      <label className="flex items-center gap-1 text-xs text-muted-foreground">
                        <input
                          type="checkbox"
                          checked={o.active}
                          disabled={toggleMutation.isPending}
                          onChange={() => toggleMutation.mutate(o)}
                        />
                        ativo
                      </label>
                      <Button
                        variant="outline"
                        className="h-7 px-2 text-xs"
                        onClick={() => openEdit(o)}
                      >
                        Editar
                      </Button>
                      <Button
                        variant="outline"
                        className="h-7 px-2 text-xs"
                        disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(o.id)}
                      >
                        Excluir
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar serviço' : 'Novo serviço'}
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
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Categoria
              </label>
              <input
                value={form.category}
                onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                placeholder="Banho, Tosa, Consulta…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Duração (min)
              </label>
              <input
                type="number"
                min="15"
                max="240"
                step="15"
                value={form.durationMinutes}
                required
                onChange={(e) => setForm((f) => ({ ...f, durationMinutes: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Preço (R$, opcional)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.price}
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                placeholder="vazio = sem preço"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Restrição de espécie
              </label>
              <select
                value={form.speciesRestriction}
                onChange={(e) =>
                  setForm((f) => ({ ...f, speciesRestriction: e.target.value as '' | PetSpecies }))
                }
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Qualquer espécie</option>
                <option value="cao">Só cães</option>
                <option value="gato">Só gatos</option>
                <option value="outro">Só outros</option>
              </select>
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
