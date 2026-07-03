'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { listAppointments } from '@/lib/api/dental/appointments'
import {
  createPatient,
  deletePatient,
  listPatients,
  updatePatient,
} from '@/lib/api/dental/patients'
import { statusLabel } from '@/profiles/dental/appointment-status'
import { calcularIdade, formatDate, formatTime, type Patient } from '@/profiles/dental/dental-types'

type FormState = {
  name: string
  email: string
  phone: string
  document: string
  birthDate: string
  notes: string
}

const EMPTY: FormState = { name: '', email: '', phone: '', document: '', birthDate: '', notes: '' }

/**
 * Pacientes do DentalBot (camada 7.4). Lista com busca, criação/edição via Modal, badge de vínculo
 * WhatsApp, e detalhe com as próximas consultas. notes é administrativo (LGPD — não clínico).
 */
export default function PatientsPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Patient | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [detail, setDetail] = useState<Patient | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['dental-patients', search],
    queryFn: () => listPatients({ search: search || undefined }),
    placeholderData: keepPreviousData,
  })

  // Consultas do paciente aberto no detalhe.
  const detailAppointments = useQuery({
    queryKey: ['dental-patient-appointments', detail?.id],
    queryFn: () => listAppointments({ patientId: detail!.id, pageSize: 50 }),
    enabled: detail !== null,
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        email: form.email || null,
        phone: form.phone || null,
        document: form.document || null,
        birthDate: form.birthDate || null,
        notes: form.notes || null,
      }
      if (editing) return updatePatient(editing.id, payload)
      return createPatient(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['dental-patients'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o paciente.'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePatient(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dental-patients'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'patient_in_use') {
        alert('Este paciente tem consultas — não pode ser excluído.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }

  function openEdit(p: Patient) {
    setEditing(p)
    setForm({
      name: p.name,
      email: p.email ?? '',
      phone: p.phone ?? '',
      document: p.document ?? '',
      birthDate: p.birthDate ?? '',
      notes: p.notes ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const patients = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Pacientes"
        description="Pacientes da clínica. A IA reconhece o paciente pelo telefone para responder e agendar."
        actions={<Button onClick={openCreate}>Novo paciente</Button>}
      />

      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Buscar por nome, telefone, email ou CPF…"
        className="w-full max-w-sm rounded-md border border-border bg-background px-3 py-2 text-sm"
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os pacientes.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : patients.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum paciente encontrado.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {patients.map((p) => (
            <Card key={p.id} className="space-y-1 p-4">
              <div className="flex items-center justify-between">
                <button onClick={() => setDetail(p)} className="font-medium hover:underline">
                  {p.name}
                </button>
                {p.contactId && <Badge variant="info">vinculado</Badge>}
              </div>
              <p className="text-xs text-muted-foreground">
                {p.phone ?? 'sem telefone'}
                {p.document ? ` · ${p.document}` : ''}
                {calcularIdade(p.birthDate) !== null ? ` · ${calcularIdade(p.birthDate)} anos` : ''}
              </p>
              <div className="flex gap-2 pt-1">
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
            </Card>
          ))}
        </div>
      )}

      {/* Modal criar/editar */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar paciente' : 'Novo paciente'}
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
                Telefone
              </label>
              <input
                value={form.phone}
                onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Email</label>
              <input
                type="email"
                value={form.email}
                onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">CPF</label>
              <input
                value={form.document}
                onChange={(e) => setForm((f) => ({ ...f, document: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Nascimento
              </label>
              <input
                type="date"
                value={form.birthDate}
                onChange={(e) => setForm((f) => ({ ...f, birthDate: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações (administrativo)
            </label>
            <textarea
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              placeholder="Preferências de horário, contato… (NÃO clínico)"
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

      {/* Modal detalhe */}
      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Paciente" size="md">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{detail.name}</span>
              {detail.contactId && <Badge variant="info">vinculado ao WhatsApp</Badge>}
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Telefone</dt>
                  <dd>{detail.phone ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Email</dt>
                  <dd>{detail.email ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">CPF</dt>
                  <dd>{detail.document ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Idade</dt>
                  <dd>{calcularIdade(detail.birthDate) ?? '—'}</dd>
                </div>
                {detail.notes && (
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">Observações</dt>
                    <dd>{detail.notes}</dd>
                  </div>
                )}
              </dl>
            </Card>
            <Card>
              <Section title="Consultas">
                <span />
              </Section>
              {detailAppointments.isPending ? (
                <p className="text-sm text-muted-foreground">Carregando…</p>
              ) : (detailAppointments.data?.items.length ?? 0) === 0 ? (
                <p className="text-sm text-muted-foreground">Nenhuma consulta.</p>
              ) : (
                <ul className="space-y-1 text-sm">
                  {detailAppointments.data!.items.map((a) => (
                    <li key={a.id} className="flex items-center justify-between gap-2">
                      <span>
                        {formatDate(a.startAt)} {formatTime(a.startAt)} · {a.type}
                      </span>
                      <Badge variant="muted">{statusLabel(a.status)}</Badge>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        )}
      </Modal>
    </div>
  )
}
