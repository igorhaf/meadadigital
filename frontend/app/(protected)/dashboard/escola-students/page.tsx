'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createStudent,
  deleteStudent,
  listStudents,
  updateStudent,
} from '@/lib/api/escola/students'
import { getMyContacts } from '@/lib/supabase/contacts'
import { formatDate, type EscolaStudent } from '@/profiles/escola/escola-types'

type FormState = {
  contactId: string
  name: string
  birthDate: string
  intendedGrade: string
  notes: string
}
const EMPTY: FormState = { contactId: '', name: '', birthDate: '', intendedGrade: '', notes: '' }

/**
 * Alunos do EscolaBot (camada 8.19). SUB-ENTIDADE do responsável (contato do WhatsApp). Lista com
 * busca e filtro por responsável, CRUD via Modal (responsável obrigatório na criação), excluir
 * protegido (409 student_in_use). A IA também cadastra alunos ao matricular.
 */
export default function EscolaStudentsPage() {
  const qc = useQueryClient()
  const [contactFilter, setContactFilter] = useState<string>('')
  const [search, setSearch] = useState<string>('')
  const [showInactive, setShowInactive] = useState(false)

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<EscolaStudent | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['escola-students', contactFilter, search, showInactive],
    queryFn: () => listStudents({
      contactId: contactFilter || undefined,
      active: showInactive ? undefined : true,
      search: search || undefined,
    }),
  })

  // responsáveis: contatos do WhatsApp (Supabase + RLS). Mapa id→nome/telefone.
  const contacts = useQuery({ queryKey: ['escola-contacts'], queryFn: () => getMyContacts() })
  const responsibleLabel = useMemo(() => {
    const m = new Map<string, string>()
    for (const c of contacts.data ?? []) m.set(c.id, c.name ?? c.phoneNumber)
    return m
  }, [contacts.data])

  const saveMutation = useMutation({
    mutationFn: () => {
      if (editing) {
        return updateStudent(editing.id, {
          name: form.name,
          birthDate: form.birthDate || null,
          intendedGrade: form.intendedGrade || null,
          notes: form.notes || null,
        })
      }
      return createStudent({
        contactId: form.contactId,
        name: form.name,
        birthDate: form.birthDate || null,
        intendedGrade: form.intendedGrade || null,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['escola-students'] })
      setModalOpen(false); setEditing(null); setForm(EMPTY); setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'contact_not_found') {
        setFormError('Selecione um responsável válido (contato do WhatsApp).')
      } else {
        setFormError('Erro ao salvar o aluno.')
      }
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteStudent(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['escola-students'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'student_in_use') {
        alert('Este aluno tem matrículas — não pode ser excluído. Desative-o em vez disso.')
      }
    },
  })

  function openCreate() { setEditing(null); setForm(EMPTY); setFormError(null); setModalOpen(true) }
  function openEdit(s: EscolaStudent) {
    setEditing(s)
    setForm({
      contactId: s.contactId,
      name: s.name,
      birthDate: s.birthDate ?? '',
      intendedGrade: s.intendedGrade ?? '',
      notes: s.notes ?? '',
    })
    setFormError(null); setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Alunos"
        description="Crianças cadastradas, vinculadas ao responsável (contato do WhatsApp). A IA usa estes dados ao matricular."
        actions={<Button onClick={openCreate}>Novo aluno</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <select value={contactFilter} onChange={(e) => setContactFilter(e.target.value)}
          className="rounded-md border border-border bg-background px-3 py-1.5 text-xs">
          <option value="">Todos os responsáveis</option>
          {(contacts.data ?? []).map((c) => (
            <option key={c.id} value={c.id}>{c.name ?? c.phoneNumber}</option>
          ))}
        </select>
        <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Buscar por nome…"
          className="rounded-md border border-border bg-background px-3 py-1.5 text-xs" />
        <label className="ml-auto flex items-center gap-1 text-xs text-muted-foreground">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          mostrar inativos
        </label>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os alunos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum aluno encontrado.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((s) => (
            <div key={s.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{s.name}</span>
                  {s.intendedGrade && <Badge variant="info">{s.intendedGrade}</Badge>}
                  {!s.active && <Badge variant="muted">inativo</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  Responsável: {responsibleLabel.get(s.contactId) ?? '—'}
                  {s.birthDate ? ` · nasc. ${formatDate(s.birthDate)}` : ''}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(s)}>Editar</Button>
                <Button variant="outline" className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending} onClick={() => deleteMutation.mutate(s.id)}>Excluir</Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Editar aluno' : 'Novo aluno'} size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
          {!editing && (
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Responsável (contato)</label>
              <select value={form.contactId} onChange={(e) => setForm((f) => ({ ...f, contactId: e.target.value }))} required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                <option value="">Selecione…</option>
                {(contacts.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>{c.name ?? c.phoneNumber}</option>
                ))}
              </select>
            </div>
          )}
          {editing && (
            <p className="text-xs text-muted-foreground">
              Responsável: <strong>{responsibleLabel.get(form.contactId) ?? '—'}</strong> (não editável)
            </p>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
              <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required
                maxLength={200} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Data de nascimento</label>
              <input type="date" value={form.birthDate} onChange={(e) => setForm((f) => ({ ...f, birthDate: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Série pretendida</label>
            <input value={form.intendedGrade} onChange={(e) => setForm((f) => ({ ...f, intendedGrade: e.target.value }))}
              maxLength={100} placeholder="Maternal II, Pré I…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Observações</label>
            <textarea value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2} placeholder="administrativo — sem dado sensível"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Salvando…' : editing ? 'Salvar' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
