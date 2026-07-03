'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { listPatients } from '@/lib/api/nutri/patients'
import { activatePlan, archivePlan, createPlan, listPlans, updatePlan } from '@/lib/api/nutri/plans'
import { listProfessionals } from '@/lib/api/nutri/professionals'
import { formatDate, type NutriPlan } from '@/profiles/nutri/nutri-types'

type FormState = {
  title: string
  professionalId: string
  body: string
  startsOn: string
  endsOn: string
  active: boolean
  notes: string
}
const EMPTY: FormState = {
  title: '',
  professionalId: '',
  body: '',
  startsOn: '',
  endsOn: '',
  active: true,
  notes: '',
}

/**
 * Planos alimentares (camada 8.0). Editor: seleciona um paciente e gerencia os planos dele.
 * O body do plano é o texto que a IA entrega VERBATIM ao paciente — nunca edita. Exatamente UM
 * plano fica ativo por paciente (ativar um arquiva o anterior). Rota /dashboard/nutri-plans.
 */
export default function NutriPlansPage() {
  const qc = useQueryClient()
  const [patientId, setPatientId] = useState<string>('')

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<NutriPlan | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const patients = useQuery({
    queryKey: ['nutri-plan-patients'],
    queryFn: () => listPatients({ active: true }),
  })

  const professionals = useQuery({
    queryKey: ['nutri-plan-professionals'],
    queryFn: () => listProfessionals({ onlyActive: true }),
  })

  const plans = useQuery({
    queryKey: ['nutri-plans', patientId],
    queryFn: () => listPlans(patientId),
    enabled: patientId !== '',
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      if (editing) {
        return updatePlan(editing.id, {
          title: form.title,
          body: form.body,
          notes: form.notes || null,
          ...(form.professionalId
            ? { professionalId: form.professionalId }
            : { clearProfessional: true }),
          ...(form.startsOn ? { startsOn: form.startsOn } : { clearStarts: true }),
          ...(form.endsOn ? { endsOn: form.endsOn } : { clearEnds: true }),
        })
      }
      return createPlan({
        patientId,
        professionalId: form.professionalId || null,
        title: form.title,
        body: form.body,
        startsOn: form.startsOn || null,
        endsOn: form.endsOn || null,
        active: form.active,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['nutri-plans', patientId] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o plano.'),
  })

  const activateMutation = useMutation({
    mutationFn: (id: string) => activatePlan(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['nutri-plans', patientId] }),
  })

  const archiveMutation = useMutation({
    mutationFn: (id: string) => archivePlan(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['nutri-plans', patientId] }),
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(p: NutriPlan) {
    setEditing(p)
    setForm({
      title: p.title,
      professionalId: p.professionalId ?? '',
      body: p.body,
      startsOn: p.startsOn ?? '',
      endsOn: p.endsOn ?? '',
      active: p.status === 'ativo',
      notes: p.notes ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = plans.data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Planos alimentares"
        description="Selecione um paciente para ver e gerenciar os planos dele. A IA entrega o texto do plano exatamente como escrito."
        actions={patientId !== '' ? <Button onClick={openCreate}>Novo plano</Button> : undefined}
      />

      <div className="flex flex-wrap items-center gap-2">
        <label className="text-xs font-medium text-muted-foreground">Paciente</label>
        <select
          value={patientId}
          onChange={(e) => setPatientId(e.target.value)}
          className="rounded-md border border-border bg-background px-3 py-1.5 text-sm"
        >
          <option value="">Selecione…</option>
          {(patients.data?.items ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </div>

      {patientId === '' ? (
        <p className="text-sm text-muted-foreground">Selecione um paciente para ver os planos.</p>
      ) : plans.isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os planos.</p>
      ) : plans.isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum plano para este paciente ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{p.title}</span>
                  {p.status === 'ativo' ? (
                    <Badge variant="success">ativo</Badge>
                  ) : (
                    <Badge variant="muted">arquivado</Badge>
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  {p.professionalName ?? '—'}
                  {p.startsOn || p.endsOn
                    ? ` · ${p.startsOn ? formatDate(p.startsOn) : '…'}–${p.endsOn ? formatDate(p.endsOn) : '…'}`
                    : ''}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(p)}>
                  Editar
                </Button>
                {p.status === 'arquivado' && (
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    disabled={activateMutation.isPending}
                    onClick={() => activateMutation.mutate(p.id)}
                  >
                    Ativar
                  </Button>
                )}
                {p.status === 'ativo' && (
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    disabled={archiveMutation.isPending}
                    onClick={() => archiveMutation.mutate(p.id)}
                  >
                    Arquivar
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar plano' : 'Novo plano'}
        size="lg"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Título</label>
            <input
              value={form.title}
              onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              required
              maxLength={200}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Nutricionista responsável
            </label>
            <select
              value={form.professionalId}
              onChange={(e) => setForm((f) => ({ ...f, professionalId: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">(nenhum)</option>
              {(professionals.data?.items ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Plano alimentar (markdown)
            </label>
            <textarea
              value={form.body}
              onChange={(e) => setForm((f) => ({ ...f, body: e.target.value }))}
              required
              rows={12}
              placeholder="Escreva aqui o plano alimentar completo do paciente. A IA entrega este texto exatamente como está, sem editar."
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm"
            />
            <p className="mt-1 text-xs text-muted-foreground">
              A IA entrega este texto <strong>verbatim</strong> ao paciente — nunca o reescreve nem
              resume.
            </p>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Início da vigência
              </label>
              <input
                type="date"
                value={form.startsOn}
                onChange={(e) => setForm((f) => ({ ...f, startsOn: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Fim da vigência
              </label>
              <input
                type="date"
                value={form.endsOn}
                onChange={(e) => setForm((f) => ({ ...f, endsOn: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          {!editing && (
            <label className="flex items-center gap-2 text-xs text-muted-foreground">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
              />
              Plano ativo (arquiva o plano ativo anterior deste paciente)
            </label>
          )}
          <p className="text-xs text-muted-foreground">
            Exatamente <strong>um</strong> plano fica ativo por paciente. Ativar um plano arquiva
            automaticamente o anterior.
          </p>
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
