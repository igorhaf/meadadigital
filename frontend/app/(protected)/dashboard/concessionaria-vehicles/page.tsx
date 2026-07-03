'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createVehicle,
  deleteVehicle,
  listVehicles,
  updateVehicle,
  updateVehicleStatus,
} from '@/lib/api/concessionaria/vehicles'
import {
  formatBrl,
  FUELS,
  TRANSMISSIONS,
  type Vehicle,
} from '@/profiles/concessionaria/concessionaria-types'
import {
  ALLOWED_NEXT,
  statusLabel,
  type VehicleStatusId,
} from '@/profiles/concessionaria/concessionaria-vehicle-status'

function StatusBadge({ status }: { status: VehicleStatusId }) {
  const variant = status === 'disponivel' ? 'success' : status === 'reservado' ? 'warning' : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  brand: string
  model: string
  modelYear: string
  mileageKm: string
  price: string // R$ (string editável)
  color: string
  fuel: string
  transmission: string
  plate: string
  photoUrl: string
  description: string
}
const EMPTY: FormState = {
  brand: '',
  model: '',
  modelYear: '',
  mileageKm: '',
  price: '',
  color: '',
  fuel: '',
  transmission: '',
  plate: '',
  photoUrl: '',
  description: '',
}

/** Converte "12.500,00" / "12500" / "12500.5" em centavos. */
function brlToCents(v: string): number {
  const normalized = v.trim().replace(/\./g, '').replace(',', '.')
  const n = Number(normalized)
  return Number.isFinite(n) ? Math.round(n * 100) : 0
}

/**
 * Estoque de veículos do ConcessionariaBot (camada 8.17). CRUD do estoque + controle de ciclo de
 * status (disponível → reservado → vendido, validado por ALLOWED_NEXT; surfacia
 * invalid_status_transition). Mostra o badge de status e o preview da foto a partir do photoUrl.
 */
export default function ConcessionariaVehiclesPage() {
  const qc = useQueryClient()
  const [onlyAvailable, setOnlyAvailable] = useState(false)

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Vehicle | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const [statusVehicle, setStatusVehicle] = useState<Vehicle | null>(null)
  const [statusTarget, setStatusTarget] = useState<VehicleStatusId | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['concessionaria-vehicles', onlyAvailable],
    queryFn: () => listVehicles({ available: onlyAvailable || undefined }),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = {
        brand: form.brand,
        model: form.model,
        priceCents: brlToCents(form.price),
        modelYear: form.modelYear.trim() === '' ? null : Math.round(Number(form.modelYear)),
        mileageKm: form.mileageKm.trim() === '' ? null : Math.round(Number(form.mileageKm)),
        color: form.color || null,
        fuel: form.fuel || null,
        transmission: form.transmission || null,
        plate: form.plate || null,
        photoUrl: form.photoUrl || null,
        description: form.description || null,
      }
      if (editing) return updateVehicle(editing.id, payload)
      return createVehicle(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['concessionaria-vehicles'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'plate_taken') {
        setFormError('Já existe um veículo com essa placa.')
      } else {
        setFormError('Erro ao salvar o veículo.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: VehicleStatusId) => {
      if (!statusVehicle) throw new Error('sem veículo')
      return updateVehicleStatus(statusVehicle.id, newStatus)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['concessionaria-vehicles'] })
      setStatusVehicle(null)
      setStatusTarget(null)
      setStatusError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_status_transition') {
        setStatusError('Essa mudança de status não é permitida.')
      } else {
        setStatusError('Erro ao mudar o status.')
      }
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteVehicle(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['concessionaria-vehicles'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'vehicle_in_use') {
        alert('Este veículo tem test-drives ou leads — não pode ser excluído.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(v: Vehicle) {
    setEditing(v)
    setForm({
      brand: v.brand,
      model: v.model,
      modelYear: v.modelYear != null ? String(v.modelYear) : '',
      mileageKm: v.mileageKm != null ? String(v.mileageKm) : '',
      price: (v.priceCents / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2 }),
      color: v.color ?? '',
      fuel: v.fuel ?? '',
      transmission: v.transmission ?? '',
      plate: v.plate ?? '',
      photoUrl: v.photoUrl ?? '',
      description: v.description ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Estoque"
        description="Veículos à venda. A IA oferece os disponíveis no WhatsApp; controle aqui o ciclo de status."
        actions={<Button onClick={openCreate}>Novo veículo</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <label className="ml-auto flex items-center gap-1 text-xs text-muted-foreground">
          <input
            type="checkbox"
            checked={onlyAvailable}
            onChange={(e) => setOnlyAvailable(e.target.checked)}
          />
          só disponíveis
        </label>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o estoque.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum veículo no estoque ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((v) => (
            <div key={v.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex min-w-0 items-center gap-3">
                {v.photoUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={v.photoUrl}
                    alt={`${v.brand} ${v.model}`}
                    className="h-12 w-16 shrink-0 rounded object-cover"
                  />
                ) : (
                  <div className="flex h-12 w-16 shrink-0 items-center justify-center rounded bg-muted text-[10px] text-muted-foreground">
                    sem foto
                  </div>
                )}
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">
                      {v.brand} {v.model}
                    </span>
                    <StatusBadge status={v.status} />
                    {!v.active && <Badge variant="muted">inativo</Badge>}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {[v.modelYear, v.color, v.fuel, v.transmission].filter(Boolean).join(' · ') ||
                      '—'}
                    {v.mileageKm != null ? ` · ${v.mileageKm.toLocaleString('pt-BR')} km` : ''}
                    {' · '}
                    <strong>{formatBrl(v.priceCents)}</strong>
                  </p>
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {ALLOWED_NEXT[v.status].length > 0 && (
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    onClick={() => {
                      setStatusVehicle(v)
                      setStatusTarget(null)
                      setStatusError(null)
                    }}
                  >
                    Status
                  </Button>
                )}
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(v)}>
                  Editar
                </Button>
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

      {/* Modal: criar/editar veículo */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar veículo' : 'Novo veículo'}
        size="lg"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Marca</label>
              <input
                value={form.brand}
                onChange={(e) => setForm((f) => ({ ...f, brand: e.target.value }))}
                required
                maxLength={100}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Modelo</label>
              <input
                value={form.model}
                onChange={(e) => setForm((f) => ({ ...f, model: e.target.value }))}
                required
                maxLength={100}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Ano</label>
              <input
                type="number"
                min="1900"
                max="2100"
                value={form.modelYear}
                onChange={(e) => setForm((f) => ({ ...f, modelYear: e.target.value }))}
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
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Preço (R$)
              </label>
              <input
                value={form.price}
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                required
                placeholder="0,00"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
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
                Combustível
              </label>
              <input
                value={form.fuel}
                onChange={(e) => setForm((f) => ({ ...f, fuel: e.target.value }))}
                list="conc-fuels"
                placeholder="Flex, Diesel…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
              <datalist id="conc-fuels">
                {FUELS.map((x) => (
                  <option key={x} value={x} />
                ))}
              </datalist>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Câmbio</label>
              <input
                value={form.transmission}
                onChange={(e) => setForm((f) => ({ ...f, transmission: e.target.value }))}
                list="conc-transmissions"
                placeholder="Manual, Automático…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
              <datalist id="conc-transmissions">
                {TRANSMISSIONS.map((x) => (
                  <option key={x} value={x} />
                ))}
              </datalist>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Placa</label>
              <input
                value={form.plate}
                onChange={(e) => setForm((f) => ({ ...f, plate: e.target.value.toUpperCase() }))}
                maxLength={10}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Foto (URL)
              </label>
              <input
                value={form.photoUrl}
                onChange={(e) => setForm((f) => ({ ...f, photoUrl: e.target.value }))}
                type="url"
                placeholder="https://…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          {form.photoUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={form.photoUrl} alt="prévia" className="h-32 w-full rounded-md object-cover" />
          )}
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

      {/* Modal: ciclo de status do veículo */}
      <Modal
        open={statusVehicle !== null}
        onClose={() => setStatusVehicle(null)}
        title="Mudar status do veículo"
        size="sm"
      >
        {statusVehicle && (
          <div className="space-y-4">
            <p className="text-sm">
              <strong>
                {statusVehicle.brand} {statusVehicle.model}
              </strong>{' '}
              está <StatusBadge status={statusVehicle.status} />
            </p>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Mudar para…
              </label>
              <div className="flex flex-wrap gap-2">
                {ALLOWED_NEXT[statusVehicle.status].map((next) => (
                  <Button
                    key={next}
                    variant="outline"
                    className="h-8 px-3 text-xs"
                    onClick={() => setStatusTarget(next)}
                  >
                    {statusLabel(next)}
                  </Button>
                ))}
              </div>
            </div>
            {statusError && <p className="text-sm text-destructive">{statusError}</p>}
          </div>
        )}
      </Modal>

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget) : ''}"?`}
        description="O ciclo do estoque é disponível → reservado → vendido (vendido é final)."
        confirmLabel="Mudar status"
        destructive={false}
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (statusTarget) statusMutation.mutate(statusTarget)
        }}
      />
    </div>
  )
}
