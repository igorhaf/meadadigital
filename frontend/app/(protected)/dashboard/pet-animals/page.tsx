'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  archiveAnimal,
  createAnimal,
  deleteAnimal,
  listAnimals,
  updateAnimal,
} from '@/lib/api/pet/animals'
import { getMyContacts } from '@/lib/supabase/contacts'
import {
  sexLabel,
  speciesLabel,
  type PetAnimal,
  type PetSex,
  type PetSpecies,
} from '@/profiles/pet/pet-types'

type FormState = {
  contactId: string
  name: string
  species: PetSpecies
  breed: string
  sex: PetSex
  birthYear: string
  notes: string
}
const EMPTY: FormState = { contactId: '', name: '', species: 'cao', breed: '', sex: 'desconhecido', birthYear: '', notes: '' }

/**
 * Animais do PetBot (camada 7.8). SUB-ENTIDADE do tutor (contato). Lista com filtro de espécie /
 * busca, CRUD via Modal (tutor obrigatório na criação — vem dos contatos do WhatsApp), arquivar
 * (preferido a excluir) e excluir protegido (409 animal_in_use). A IA também cadastra animais ao
 * agendar (modo new_animal).
 */
export default function PetAnimalsPage() {
  const qc = useQueryClient()
  const [species, setSpecies] = useState<string>('')
  const [search, setSearch] = useState<string>('')
  const [showInactive, setShowInactive] = useState(false)

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<PetAnimal | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['pet-animals', species, search, showInactive],
    queryFn: () => listAnimals({
      species: species || undefined,
      active: showInactive ? undefined : true,
      search: search || undefined,
    }),
  })

  // tutores: contatos do WhatsApp (Supabase + RLS). Mapa id→nome/telefone para exibir + selecionar.
  const contacts = useQuery({ queryKey: ['pet-contacts'], queryFn: () => getMyContacts() })
  const tutorLabel = useMemo(() => {
    const m = new Map<string, string>()
    for (const c of contacts.data ?? []) m.set(c.id, c.name ?? c.phoneNumber)
    return m
  }, [contacts.data])

  const saveMutation = useMutation({
    mutationFn: () => {
      const birthYear = form.birthYear.trim() === '' ? null : Math.round(Number(form.birthYear))
      if (editing) {
        return updateAnimal(editing.id, {
          name: form.name,
          species: form.species,
          breed: form.breed || null,
          sex: form.sex,
          birthYear,
          notes: form.notes || null,
        })
      }
      return createAnimal({
        contactId: form.contactId,
        name: form.name,
        species: form.species,
        breed: form.breed || null,
        sex: form.sex,
        birthYear,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pet-animals'] })
      setModalOpen(false); setEditing(null); setForm(EMPTY); setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'contact_not_found') {
        setFormError('Selecione um tutor válido (contato do WhatsApp).')
      } else if (e instanceof ApiError && e.reason === 'invalid_species') {
        setFormError('Espécie inválida.')
      } else {
        setFormError('Erro ao salvar o animal.')
      }
    },
  })

  const archiveMutation = useMutation({
    mutationFn: (id: string) => archiveAnimal(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pet-animals'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteAnimal(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pet-animals'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'animal_in_use') {
        alert('Este animal tem agendamentos — não pode ser excluído. Arquive-o em vez disso.')
      }
    },
  })

  function openCreate() { setEditing(null); setForm(EMPTY); setFormError(null); setModalOpen(true) }
  function openEdit(a: PetAnimal) {
    setEditing(a)
    setForm({
      contactId: a.contactId,
      name: a.name,
      species: a.species,
      breed: a.breed ?? '',
      sex: a.sex ?? 'desconhecido',
      birthYear: a.birthYear != null ? String(a.birthYear) : '',
      notes: a.notes ?? '',
    })
    setFormError(null); setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Animais"
        description="Pets cadastrados, vinculados ao tutor (contato do WhatsApp). A IA usa estes dados ao agendar."
        actions={<Button onClick={openCreate}>Novo animal</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <button onClick={() => setSpecies('')}
          className={`rounded-full border px-3 py-1 text-xs ${species === '' ? 'border-primary bg-primary/10' : 'border-border'}`}>
          Todas
        </button>
        {(['cao', 'gato', 'outro'] as PetSpecies[]).map((s) => (
          <button key={s} onClick={() => setSpecies(s)}
            className={`rounded-full border px-3 py-1 text-xs ${species === s ? 'border-primary bg-primary/10' : 'border-border'}`}>
            {speciesLabel(s)}
          </button>
        ))}
        <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Buscar por nome…"
          className="rounded-md border border-border bg-background px-3 py-1.5 text-xs" />
        <label className="ml-auto flex items-center gap-1 text-xs text-muted-foreground">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          mostrar arquivados
        </label>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os animais.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum animal encontrado.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((a) => (
            <div key={a.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{a.name}</span>
                  <Badge variant="info">{speciesLabel(a.species)}</Badge>
                  {!a.active && <Badge variant="muted">arquivado</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  Tutor: {tutorLabel.get(a.contactId) ?? '—'}
                  {a.breed ? ` · ${a.breed}` : ''}
                  {a.sex ? ` · ${sexLabel(a.sex)}` : ''}
                  {a.birthYear ? ` · ${a.birthYear}` : ''}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(a)}>Editar</Button>
                {a.active && (
                  <Button variant="outline" className="h-7 px-2 text-xs"
                    disabled={archiveMutation.isPending} onClick={() => archiveMutation.mutate(a.id)}>Arquivar</Button>
                )}
                <Button variant="outline" className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending} onClick={() => deleteMutation.mutate(a.id)}>Excluir</Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Editar animal' : 'Novo animal'} size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
          {!editing && (
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Tutor (contato)</label>
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
              Tutor: <strong>{tutorLabel.get(form.contactId) ?? '—'}</strong> (não editável)
            </p>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
              <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required
                maxLength={100} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Espécie</label>
              <select value={form.species} onChange={(e) => setForm((f) => ({ ...f, species: e.target.value as PetSpecies }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                <option value="cao">Cão</option>
                <option value="gato">Gato</option>
                <option value="outro">Outro</option>
              </select>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Raça</label>
              <input value={form.breed} onChange={(e) => setForm((f) => ({ ...f, breed: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Sexo</label>
              <select value={form.sex} onChange={(e) => setForm((f) => ({ ...f, sex: e.target.value as PetSex }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                <option value="desconhecido">Desconhecido</option>
                <option value="macho">Macho</option>
                <option value="femea">Fêmea</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Ano de nasc.</label>
              <input type="number" min="1990" max="2030" value={form.birthYear}
                onChange={(e) => setForm((f) => ({ ...f, birthYear: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Observações</label>
            <textarea value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2} placeholder="administrativo — sem dado clínico"
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
