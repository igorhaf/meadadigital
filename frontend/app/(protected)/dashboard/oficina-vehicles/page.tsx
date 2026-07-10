'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  archiveVehicle,
  createVehicle,
  deleteVehicle,
  listVehicles,
  updateVehicle,
} from '@/lib/api/oficina/vehicles'
import { getMyContacts } from '@/lib/supabase/contacts'
import type { OsVehicle } from '@/profiles/oficina/oficina-types'

type FormState = {
  contactId: string
  plate: string
  brand: string
  model: string
  year: string
  color: string
  mileageKm: string
  notes: string
}
const EMPTY: FormState = {
  contactId: '',
  plate: '',
  brand: '',
  model: '',
  year: '',
  color: '',
  mileageKm: '',
  notes: '',
}

/**
 * Veículos do OficinaBot (camada 7.9). SUB-ENTIDADE do cliente (contato). Lista com busca/filtro,
 * CRUD via Modal (cliente obrigatório na criação — vem dos contatos do WhatsApp), arquivar
 * (preferido a excluir) e excluir protegido (409 vehicle_in_use). Placa UNIQUE → 409 plate_taken.
 */
export default function OficinaVehiclesPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState<string>('')
  const [showInactive, setShowInactive] = useState(false)

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<OsVehicle | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['oficina-vehicles', search, showInactive],
    queryFn: () =>
      listVehicles({ active: showInactive ? undefined : true, search: search || undefined }),
  })

  const contacts = useQuery({ queryKey: ['oficina-contacts'], queryFn: () => getMyContacts() })
  const clientLabel = useMemo(() => {
    const m = new Map<string, string>()
    for (const c of contacts.data ?? []) m.set(c.id, c.name ?? c.phoneNumber)
    return m
  }, [contacts.data])

  const saveMutation = useMutation({
    mutationFn: () => {
      const year = form.year.trim() === '' ? null : Math.round(Number(form.year))
      const mileageKm = form.mileageKm.trim() === '' ? null : Math.round(Number(form.mileageKm))
      if (editing) {
        return updateVehicle(editing.id, {
          plate: form.plate,
          brand: form.brand || null,
          model: form.model || null,
          year,
          color: form.color || null,
          mileageKm,
          notes: form.notes || null,
        })
      }
      return createVehicle({
        contactId: form.contactId,
        plate: form.plate,
        brand: form.brand || null,
        model: form.model || null,
        year,
        color: form.color || null,
        mileageKm,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['oficina-vehicles'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'plate_taken') {
        setFormError('Já existe um veículo com essa placa.')
      } else if (e instanceof ApiError && e.reason === 'contact_not_found') {
        setFormError('Selecione um cliente válido (contato do WhatsApp).')
      } else {
        setFormError('Erro ao salvar o veículo.')
      }
    },
  })

  const archiveMutation = useMutation({
    mutationFn: (id: string) => archiveVehicle(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['oficina-vehicles'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteVehicle(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['oficina-vehicles'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'vehicle_in_use') {
        alert('Este veículo tem ordens de serviço — não pode ser excluído. Arquive-o em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(v: OsVehicle) {
    setEditing(v)
    setForm({
      contactId: v.contactId,
      plate: v.plate,
      brand: v.brand ?? '',
      model: v.model ?? '',
      year: v.year != null ? String(v.year) : '',
      color: v.color ?? '',
      mileageKm: v.mileageKm != null ? String(v.mileageKm) : '',
      notes: v.notes ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Veículos"
        description="Veículos cadastrados, vinculados ao cliente (contato do WhatsApp)."
        actions={<Button onClick={openCreate}>Novo veículo</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar por placa/modelo/marca…"
          className="rounded-md border border-border bg-background px-3 py-1.5 text-xs"
        />
        <label className="ml-auto flex items-center gap-1 text-xs text-muted-foreground">
          <input
            type="checkbox"
            checked={showInactive}
            onChange={(e) => setShowInactive(e.target.checked)}
          />
          mostrar arquivados
        </label>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os veículos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum veículo encontrado.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((v) => (
            <div key={v.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{v.plate}</span>
                  {!v.active && <Badge variant="muted">arquivado</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  {[v.brand, v.model, v.year].filter(Boolean).join(' ') || '—'}
                  {' · '}Cliente: {clientLabel.get(v.contactId) ?? '—'}
                  {v.mileageKm != null ? ` · ${v.mileageKm.toLocaleString('pt-BR')} km` : ''}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(v)}>
                  Editar
                </Button>
                {v.active && (
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    disabled={archiveMutation.isPending}
                    onClick={() => archiveMutation.mutate(v.id)}
                  >
                    Arquivar
                  </Button>
                )}
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(v.id)}
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
        title={editing ? 'Editar veículo' : 'Novo veículo'}
        size="md"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          {!editing && (
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Cliente (contato)
              </label>
              <select
                value={form.contactId}
                onChange={(e) => setForm((f) => ({ ...f, contactId: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Selecione…</option>
                {(contacts.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name ?? c.phoneNumber}
                  </option>
                ))}
              </select>
            </div>
          )}
          {editing && (
            <p className="text-xs text-muted-foreground">
              Cliente: <strong>{clientLabel.get(form.contactId) ?? '—'}</strong> (não editável)
            </p>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Placa</label>
              <input
                value={form.plate}
                onChange={(e) => setForm((f) => ({ ...f, plate: e.target.value.toUpperCase() }))}
                required
                maxLength={10}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Ano</label>
              <input
                type="number"
                min="1900"
                max="2100"
                value={form.year}
                onChange={(e) => setForm((f) => ({ ...f, year: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Marca</label>
              <input
                value={form.brand}
                onChange={(e) => setForm((f) => ({ ...f, brand: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Modelo</label>
              <input
                value={form.model}
                onChange={(e) => setForm((f) => ({ ...f, model: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Cor</label>
              <input
                value={form.color}
                onChange={(e) => setForm((f) => ({ ...f, color: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Quilometragem
              </label>
              <input
                type="number"
                min="0"
                value={form.mileageKm}
                onChange={(e) => setForm((f) => ({ ...f, mileageKm: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
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
