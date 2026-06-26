'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createProcedureType,
  deleteProcedureType,
  listProcedureTypes,
  toggleProcedureType,
  updateProcedureType,
} from '@/lib/api/dermatologia/procedure-types'
import type { DermatologiaProcedureType } from '@/profiles/dermatologia/dermatologia-types'

type FormState = { name: string; durationMinutes: number; prepInstructions: string; notes: string }
const EMPTY: FormState = { name: '', durationMinutes: 30, prepInstructions: '', notes: '' }

/**
 * Tipos de atendimento (camada 8.11, ESCAPADA). Cada tipo tem SUA duração e, opcionalmente, uma
 * NOTA DE PREPARO entregue VERBATIM ao paciente pela IA — orientação PRÉ-procedimento, NÃO
 * prontuário. CRUD via Modal, toggle ativo, exclusão protegida (409 procedure_type_in_use).
 * Rota /dashboard/dermatologia-procedures.
 */
export default function DermatologiaProceduresPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<DermatologiaProcedureType | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['dermatologia-procedure-types'],
    queryFn: () => listProcedureTypes(),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      if (editing) {
        const prepCleared = form.prepInstructions.trim() === '' && (editing.prepInstructions ?? '') !== ''
        return updateProcedureType(editing.id, {
          name: form.name,
          durationMinutes: form.durationMinutes,
          notes: form.notes || null,
          ...(prepCleared ? { clearPrep: true } : { prepInstructions: form.prepInstructions || null }),
        })
      }
      return createProcedureType({
        name: form.name,
        durationMinutes: form.durationMinutes,
        prepInstructions: form.prepInstructions || null,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['dermatologia-procedure-types'] })
      setModalOpen(false); setEditing(null); setForm(EMPTY); setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_duration') {
        setFormError('A duração precisa estar entre 5 e 480 minutos.')
      } else {
        setFormError('Erro ao salvar o tipo de atendimento.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (p: DermatologiaProcedureType) => toggleProcedureType(p.id, !p.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dermatologia-procedure-types'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteProcedureType(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dermatologia-procedure-types'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'procedure_type_in_use') {
        alert('Este tipo tem consultas — não pode ser excluído. Desative-o.')
      }
    },
  })

  function openCreate() { setEditing(null); setForm(EMPTY); setFormError(null); setModalOpen(true) }
  function openEdit(p: DermatologiaProcedureType) {
    setEditing(p)
    setForm({
      name: p.name,
      durationMinutes: p.durationMinutes,
      prepInstructions: p.prepInstructions ?? '',
      notes: p.notes ?? '',
    })
    setFormError(null); setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tipos de atendimento"
        description="Cada tipo tem sua duração própria e, se quiser, uma nota de preparo entregue ao paciente."
        actions={<Button onClick={openCreate}>Novo tipo</Button>}
      />

      <div className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-xs text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
        A <strong>nota de preparo</strong> é enviada ao paciente <strong>exatamente como escrita</strong>, sem que a IA
        reescreva. Use-a só para orientações pré-procedimento (ex.: jejum, suspender ácido). Ela <strong>não é prontuário</strong> —
        não inclua diagnóstico, evolução ou dado clínico sensível.
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os tipos de atendimento.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum tipo de atendimento cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{p.name}</span>
                  <Badge variant="info">{p.durationMinutes} min</Badge>
                  {p.prepInstructions && p.prepInstructions.trim() !== ''
                    ? <Badge variant="success">com preparo</Badge>
                    : <Badge variant="muted">sem preparo</Badge>}
                  {!p.active && <Badge variant="muted">inativo</Badge>}
                </div>
                {p.notes && <p className="text-xs text-muted-foreground">{p.notes}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input type="checkbox" checked={p.active} disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(p)} />
                  ativo
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(p)}>Editar</Button>
                <Button variant="outline" className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending} onClick={() => deleteMutation.mutate(p.id)}>Excluir</Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Editar tipo' : 'Novo tipo de atendimento'} size="lg">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
              <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required
                maxLength={120} placeholder="Consulta, Botox, Limpeza de pele…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Duração (minutos)</label>
              <input type="number" min={5} max={480} step={5} value={form.durationMinutes} required
                onChange={(e) => setForm((f) => ({ ...f, durationMinutes: Number(e.target.value) }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Nota de preparo (opcional)</label>
            <textarea value={form.prepInstructions} onChange={(e) => setForm((f) => ({ ...f, prepInstructions: e.target.value }))}
              rows={8}
              placeholder="Orientações pré-procedimento entregues EXATAMENTE como escritas ao paciente. Ex.: Suspender ácido 5 dias antes; evitar exposição solar; comparecer sem maquiagem."
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm font-mono" />
            <p className="mt-1 text-xs text-muted-foreground">
              A IA entrega este texto <strong>verbatim</strong> quando o paciente pedir o preparo da consulta — nunca o
              reescreve. <strong>Não é prontuário</strong>: sem diagnóstico ou dado clínico sensível.
            </p>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Observações internas</label>
            <textarea value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
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
