'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { listClasses } from '@/lib/api/academia/classes'
import { createDayPass, listDayPasses, payDayPass } from '@/lib/api/academia/day-passes'
import { formatDate, formatPrice } from '@/profiles/academia/academia-types'

type FormState = { guestName: string; guestPhone: string; classId: string; passDate: string; price: string }

/** "YYYY-MM-DD" de hoje no fuso local do browser. */
function todayIso(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * Avulsos (day pass) do AcademiaBot: registro manual de aula avulsa/day-use com marcação de
 * pagamento. Cobrança online espera o gateway (#50).
 */
export default function AcademiaDayPassesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>({ guestName: '', guestPhone: '', classId: '', passDate: todayIso(), price: '' })
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['academia-day-passes'],
    queryFn: () => listDayPasses(),
  })

  // Todas as aulas (resolve o nome mesmo de aula inativa na lista); o select filtra as ativas.
  const { data: classesData } = useQuery({
    queryKey: ['academia-classes'],
    queryFn: () => listClasses(),
  })
  const classes = classesData?.items ?? []
  const classNameById = new Map(classes.map((c) => [c.id, c.name]))

  const createMutation = useMutation({
    mutationFn: () =>
      createDayPass({
        guestName: form.guestName,
        guestPhone: form.guestPhone || null,
        classId: form.classId || null,
        passDate: form.passDate || null,
        priceCents: Math.round(Number(form.price || '0') * 100),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['academia-day-passes'] })
      setModalOpen(false); setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_date') setFormError('Data inválida.')
      else setFormError('Erro ao registrar o avulso.')
    },
  })

  const payMutation = useMutation({
    mutationFn: (id: string) => payDayPass(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['academia-day-passes'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'day_pass_not_found') {
        alert('Avulso não encontrado — recarregue a página.')
      }
    },
  })

  function openCreate() {
    setForm({ guestName: '', guestPhone: '', classId: '', passDate: todayIso(), price: '' })
    setFormError(null); setModalOpen(true)
  }

  const passes = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Avulsos"
        description="Aulas avulsas e day-use de visitantes, com controle de pagamento."
        actions={<Button onClick={openCreate}>Novo avulso</Button>}
      />

      <p className="text-xs text-muted-foreground">
        A cobrança online espera o gateway de pagamento (#50) — por enquanto o registro do avulso é manual.
      </p>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os avulsos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : passes.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum avulso registrado ainda.</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs text-muted-foreground">
                <th className="px-3 py-2 font-medium">Visitante</th>
                <th className="px-3 py-2 font-medium">Telefone</th>
                <th className="px-3 py-2 font-medium">Aula</th>
                <th className="px-3 py-2 font-medium">Data</th>
                <th className="px-3 py-2 font-medium">Valor</th>
                <th className="px-3 py-2 font-medium">Pagamento</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {passes.map((p) => (
                <tr key={p.id} className="border-b border-border last:border-b-0">
                  <td className="px-3 py-2 font-medium">{p.guestName}</td>
                  <td className="px-3 py-2 text-muted-foreground">{p.guestPhone ?? '—'}</td>
                  <td className="px-3 py-2">{p.classId ? classNameById.get(p.classId) ?? '—' : 'Livre'}</td>
                  <td className="px-3 py-2 tabular-nums">{formatDate(p.passDate)}</td>
                  <td className="px-3 py-2 tabular-nums">{formatPrice(p.priceCents)}</td>
                  <td className="px-3 py-2">
                    {p.paid ? <Badge variant="success">Pago</Badge> : <Badge variant="warning">A receber</Badge>}
                  </td>
                  <td className="px-3 py-2 text-right">
                    {!p.paid && (
                      <Button variant="outline" className="h-7 px-2 text-xs"
                        disabled={payMutation.isPending} onClick={() => payMutation.mutate(p.id)}>
                        Marcar pago
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Novo avulso" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); createMutation.mutate() }}>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome do visitante</label>
            <input value={form.guestName} onChange={(e) => setForm((f) => ({ ...f, guestName: e.target.value }))}
              required maxLength={200}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Telefone</label>
            <input value={form.guestPhone} onChange={(e) => setForm((f) => ({ ...f, guestPhone: e.target.value }))}
              maxLength={30}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Aula (opcional)</label>
            <select value={form.classId} onChange={(e) => setForm((f) => ({ ...f, classId: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
              <option value="">Livre (sem aula específica)</option>
              {classes.filter((c) => c.active).map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Data</label>
            <input type="date" value={form.passDate} onChange={(e) => setForm((f) => ({ ...f, passDate: e.target.value }))}
              required className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Preço (R$)</label>
            <input type="number" min="0" step="0.01" value={form.price} required
              onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Salvando…' : 'Registrar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
