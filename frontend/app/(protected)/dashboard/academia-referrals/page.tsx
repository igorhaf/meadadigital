'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { listMemberships } from '@/lib/api/academia/memberships'
import {
  convertReferral,
  createReferral,
  listReferrals,
  type ReferralStatusFilter,
} from '@/lib/api/academia/referrals'
import type { Referral } from '@/profiles/academia/academia-types'

type FormState = { referrerContactId: string; referredName: string; referredPhone: string; rewardPercent: string }
const EMPTY: FormState = { referrerContactId: '', referredName: '', referredPhone: '', rewardPercent: '' }

const STATUS_BADGE: Record<Referral['status'], { label: string; variant: 'warning' | 'success' | 'muted' }> = {
  pendente: { label: 'Pendente', variant: 'warning' },
  convertida: { label: 'Convertida', variant: 'success' },
  expirada: { label: 'Expirada', variant: 'muted' },
}

/** Timestamp ISO → data pt-BR (as datas da indicação vêm como timestamp, não "YYYY-MM-DD"). */
function formatTimestamp(ts: string | null): string {
  if (!ts) return '—'
  return new Date(ts).toLocaleDateString('pt-BR')
}

/**
 * Indicações (member-get-member) do AcademiaBot: o aluno divulga o CÓDIGO; quando o indicado
 * fecha matrícula, a academia marca "Converteu" e o indicador ganha o desconto.
 */
export default function AcademiaReferralsPage() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<'' | ReferralStatusFilter>('')
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['academia-referrals', statusFilter],
    queryFn: () => listReferrals(statusFilter ? { status: statusFilter } : {}),
  })

  // Todas as matrículas (resolve o nome do indicador pelo contactId, mesmo se já não está ativa).
  const { data: allMemberships } = useQuery({
    queryKey: ['academia-referrals-memberships'],
    queryFn: () => listMemberships({ pageSize: 100 }),
  })
  const referrerNameByContact = new Map(
    (allMemberships?.items ?? [])
      .filter((m) => m.contactId != null)
      .map((m) => [m.contactId as string, m.studentName]),
  )

  // Matrículas ativas com contato — candidatas a indicador no modal.
  const { data: activeMemberships } = useQuery({
    queryKey: ['academia-referrals-active-members'],
    queryFn: () => listMemberships({ status: 'ativa', pageSize: 100 }),
  })
  const referrerOptions = (activeMemberships?.items ?? []).filter((m) => m.contactId != null)

  const createMutation = useMutation({
    mutationFn: () =>
      createReferral({
        referrerContactId: form.referrerContactId || null,
        referredName: form.referredName,
        referredPhone: form.referredPhone || null,
        rewardPercent: form.rewardPercent ? Number(form.rewardPercent) : null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['academia-referrals'] })
      setModalOpen(false); setForm(EMPTY); setFormError(null)
    },
    onError: () => setFormError('Erro ao criar a indicação.'),
  })

  const convertMutation = useMutation({
    mutationFn: (id: string) => convertReferral(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['academia-referrals'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'referral_not_pending') {
        alert('Essa indicação já não está pendente.')
        qc.invalidateQueries({ queryKey: ['academia-referrals'] })
      }
    },
  })

  function openCreate() { setForm(EMPTY); setFormError(null); setModalOpen(true) }

  function confirmConvert(r: Referral) {
    if (confirm(`Marcar a indicação de ${r.referredName} como convertida?`)) convertMutation.mutate(r.id)
  }

  const referrals = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Indicações"
        description="Programa indique um amigo: o aluno divulga o código e ganha desconto quando o indicado se matricula."
        actions={<Button onClick={openCreate}>Nova indicação</Button>}
      />

      <div className="flex items-center gap-2">
        <label className="text-xs font-medium text-muted-foreground">Status</label>
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as '' | ReferralStatusFilter)}
          className="rounded-md border border-border bg-background px-3 py-1.5 text-sm">
          <option value="">Todas</option>
          <option value="pendente">Pendente</option>
          <option value="convertida">Convertida</option>
          <option value="expirada">Expirada</option>
        </select>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as indicações.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : referrals.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma indicação por aqui ainda.</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs text-muted-foreground">
                <th className="px-3 py-2 font-medium">Código</th>
                <th className="px-3 py-2 font-medium">Indicado</th>
                <th className="px-3 py-2 font-medium">Indicador</th>
                <th className="px-3 py-2 font-medium">Desconto</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium">Criada em</th>
                <th className="px-3 py-2 font-medium">Convertida em</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {referrals.map((r) => (
                <tr key={r.id} className="border-b border-border last:border-b-0">
                  <td className="px-3 py-2">
                    <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs font-semibold">{r.code}</span>
                  </td>
                  <td className="px-3 py-2">
                    <span className="font-medium">{r.referredName}</span>
                    {r.referredPhone && <span className="ml-2 text-xs text-muted-foreground">{r.referredPhone}</span>}
                  </td>
                  <td className="px-3 py-2">
                    {r.referrerContactId ? referrerNameByContact.get(r.referrerContactId) ?? '—' : '—'}
                  </td>
                  <td className="px-3 py-2 tabular-nums">{r.rewardPercent != null ? `${r.rewardPercent}%` : '—'}</td>
                  <td className="px-3 py-2">
                    <Badge variant={STATUS_BADGE[r.status].variant}>{STATUS_BADGE[r.status].label}</Badge>
                  </td>
                  <td className="px-3 py-2 tabular-nums">{formatTimestamp(r.createdAt)}</td>
                  <td className="px-3 py-2 tabular-nums">{formatTimestamp(r.convertedAt)}</td>
                  <td className="px-3 py-2 text-right">
                    {r.status === 'pendente' && (
                      <Button variant="outline" className="h-7 px-2 text-xs"
                        disabled={convertMutation.isPending} onClick={() => confirmConvert(r)}>
                        Converteu
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Nova indicação" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); createMutation.mutate() }}>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome do indicado</label>
            <input value={form.referredName} onChange={(e) => setForm((f) => ({ ...f, referredName: e.target.value }))}
              required maxLength={200}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Telefone do indicado</label>
            <input value={form.referredPhone} onChange={(e) => setForm((f) => ({ ...f, referredPhone: e.target.value }))}
              maxLength={30}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Indicador (opcional)</label>
            <select value={form.referrerContactId}
              onChange={(e) => setForm((f) => ({ ...f, referrerContactId: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
              <option value="">Sem indicador vinculado</option>
              {referrerOptions.map((m) => (
                <option key={m.id} value={m.contactId as string}>{m.studentName}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Desconto do indicador (%)</label>
            <input type="number" min="1" max="100" value={form.rewardPercent}
              onChange={(e) => setForm((f) => ({ ...f, rewardPercent: e.target.value }))}
              placeholder="Opcional (1 a 100)"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <p className="text-xs text-muted-foreground">O código da indicação é gerado automaticamente.</p>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Salvando…' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
