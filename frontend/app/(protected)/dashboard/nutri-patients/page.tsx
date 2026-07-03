'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  archivePatient,
  createPatient,
  deletePatient,
  listPatients,
  updatePatient,
} from '@/lib/api/nutri/patients'
import { getMyContacts } from '@/lib/supabase/contacts'
import { formatDate, type NutriPatient } from '@/profiles/nutri/nutri-types'

type FormState = {
  contactId: string
  name: string
  goal: string
  dietaryRestrictions: string
  birthDate: string
  notes: string
}
const EMPTY: FormState = {
  contactId: '',
  name: '',
  goal: '',
  dietaryRestrictions: '',
  birthDate: '',
  notes: '',
}

/**
 * Pacientes (camada 8.0). SUB-ENTIDADE do contato (cliente). Lista com busca por nome /
 * filtro de arquivados, CRUD via Modal (cliente obrigatório na criação — vem dos contatos do
 * WhatsApp), arquivar (preferido a excluir) e excluir protegido (409 patient_in_use).
 */
export default function NutriPatientsPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState<string>('')
  const [showInactive, setShowInactive] = useState(false)

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<NutriPatient | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['nutri-patients', search, showInactive],
    queryFn: () =>
      listPatients({
        active: showInactive ? undefined : true,
        search: search || undefined,
      }),
  })

  // clientes: contatos do WhatsApp (Supabase + RLS). Mapa id→nome/telefone para exibir + selecionar.
  const contacts = useQuery({ queryKey: ['nutri-contacts'], queryFn: () => getMyContacts() })
  const clientLabel = useMemo(() => {
    const m = new Map<string, string>()
    for (const c of contacts.data ?? []) m.set(c.id, c.name ?? c.phoneNumber)
    return m
  }, [contacts.data])

  const saveMutation = useMutation({
    mutationFn: () => {
      if (editing) {
        const birthChanged = form.birthDate.trim() === '' && editing.birthDate != null
        return updatePatient(editing.id, {
          name: form.name,
          goal: form.goal || null,
          dietaryRestrictions: form.dietaryRestrictions || null,
          notes: form.notes || null,
          ...(birthChanged ? { clearBirthDate: true } : { birthDate: form.birthDate || null }),
        })
      }
      return createPatient({
        contactId: form.contactId,
        name: form.name,
        goal: form.goal || null,
        dietaryRestrictions: form.dietaryRestrictions || null,
        birthDate: form.birthDate || null,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['nutri-patients'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'contact_not_found') {
        setFormError('Selecione um cliente válido (contato do WhatsApp).')
      } else {
        setFormError('Erro ao salvar o paciente.')
      }
    },
  })

  const archiveMutation = useMutation({
    mutationFn: (id: string) => archivePatient(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['nutri-patients'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePatient(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['nutri-patients'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'patient_in_use') {
        alert('Este paciente tem consultas ou planos — não pode ser excluído. Arquive-o.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(p: NutriPatient) {
    setEditing(p)
    setForm({
      contactId: p.contactId,
      name: p.name,
      goal: p.goal ?? '',
      dietaryRestrictions: p.dietaryRestrictions ?? '',
      birthDate: p.birthDate ?? '',
      notes: p.notes ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Pacientes"
        description="Pacientes cadastrados, vinculados ao cliente (contato do WhatsApp). A IA usa estes dados ao atender."
        actions={<Button onClick={openCreate}>Novo paciente</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar por nome…"
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
        <p className="text-sm text-destructive">Erro ao carregar os pacientes.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum paciente encontrado.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{p.name}</span>
                  {!p.active && <Badge variant="muted">arquivado</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  Cliente: {clientLabel.get(p.contactId) ?? '—'}
                  {p.goal ? ` · ${p.goal}` : ''}
                  {p.birthDate ? ` · ${formatDate(p.birthDate)}` : ''}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(p)}>
                  Editar
                </Button>
                {p.active && (
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    disabled={archiveMutation.isPending}
                    onClick={() => archiveMutation.mutate(p.id)}
                  >
                    Arquivar
                  </Button>
                )}
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
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Objetivo</label>
            <input
              value={form.goal}
              onChange={(e) => setForm((f) => ({ ...f, goal: e.target.value }))}
              placeholder="emagrecimento, ganho de massa, manutenção…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Restrições alimentares
            </label>
            <input
              value={form.dietaryRestrictions}
              onChange={(e) => setForm((f) => ({ ...f, dietaryRestrictions: e.target.value }))}
              placeholder="restrições alimentares (texto livre)"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Data de nascimento
            </label>
            <input
              type="date"
              value={form.birthDate}
              onChange={(e) => setForm((f) => ({ ...f, birthDate: e.target.value }))}
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
              placeholder="administrativo — sem dado clínico"
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
