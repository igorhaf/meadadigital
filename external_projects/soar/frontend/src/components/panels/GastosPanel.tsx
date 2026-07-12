import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { apiFetch } from '../../api'
import type { ExpensesResponse } from '../../types'
import { Btn, TextInput, dateBr, inputCls, money, parseMoney } from '../ui'

export default function GastosPanel({ pageId }: { pageId: number }) {
  const queryClient = useQueryClient()
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7))
  const [form, setForm] = useState({
    date: new Date().toISOString().slice(0, 10),
    description: '',
    amount: '',
    category: '',
    card: '',
    paid_by: '',
  })

  const { data } = useQuery({
    queryKey: ['expenses', pageId, month],
    queryFn: () => apiFetch<ExpensesResponse>(`/pages/${pageId}/expenses?month=${month}`),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['expenses', pageId] })

  const create = useMutation({
    mutationFn: () =>
      apiFetch(`/pages/${pageId}/expenses`, {
        method: 'POST',
        body: JSON.stringify({
          date: form.date,
          description: form.description,
          amount_cents: parseMoney(form.amount),
          category: form.category || null,
          card: form.card || null,
          paid_by: form.paid_by || null,
        }),
      }),
    onSuccess: () => {
      setForm({ ...form, description: '', amount: '' })
      invalidate()
    },
  })

  const remove = useMutation({
    mutationFn: (id: number) => apiFetch(`/pages/${pageId}/expenses/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })

  const entries = data?.entries ?? []

  return (
    <div className="space-y-5">
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          if (form.description.trim() && parseMoney(form.amount) > 0) create.mutate()
        }}
      >
        <input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} className={inputCls} />
        <TextInput value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Descrição…" className="min-w-40 flex-1" />
        <TextInput value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} placeholder="R$ 0,00" className="w-24" />
        <TextInput value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} placeholder="Categoria" className="w-28" list="categorias" />
        <datalist id="categorias">
          {Object.keys(data?.by_category ?? {}).map((c) => <option key={c} value={c} />)}
        </datalist>
        <TextInput value={form.card} onChange={(e) => setForm({ ...form, card: e.target.value })} placeholder="Cartão" className="w-24" />
        <Btn disabled={create.isPending}>Lançar</Btn>
      </form>

      <div className="flex flex-wrap items-center gap-3">
        <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} className={inputCls} />
        <span className="text-lg font-semibold">{money(data?.total_cents ?? 0)}</span>
        <div className="flex flex-wrap gap-1.5">
          {Object.entries(data?.by_category ?? {}).map(([category, cents]) => (
            <span key={category} className="rounded-full bg-[#f1f1ef] px-2 py-0.5 text-xs text-[#5f5e5b]">
              {category}: {money(cents)}
            </span>
          ))}
        </div>
      </div>

      <div className="overflow-x-auto rounded-lg border border-[#e9e9e7]">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-[#e9e9e7] bg-[#f7f7f5] text-left text-xs text-[#787774]">
              <th className="px-3 py-2 font-medium">Data</th>
              <th className="px-3 py-2 font-medium">Descrição</th>
              <th className="px-3 py-2 font-medium">Categoria</th>
              <th className="px-3 py-2 text-right font-medium">Valor</th>
              <th className="px-3 py-2 font-medium">Quem</th>
              <th className="px-3 py-2 font-medium">Cartão</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.id} className="group border-b border-[#f1f1ef] last:border-0 hover:bg-[#fbfbfa]">
                <td className="px-3 py-2 whitespace-nowrap text-[#5f5e5b]">{dateBr(entry.date)}</td>
                <td className="px-3 py-2">{entry.description}</td>
                <td className="px-3 py-2 text-xs text-[#787774]">{entry.category}</td>
                <td className="px-3 py-2 text-right font-medium whitespace-nowrap">{money(entry.amount_cents)}</td>
                <td className="px-3 py-2 text-xs text-[#787774]">{entry.paid_by}</td>
                <td className="px-3 py-2 text-xs text-[#787774]">{entry.card}</td>
                <td className="px-3 py-2 text-right">
                  <button onClick={() => remove.mutate(entry.id)} className="invisible text-xs text-[#9b9a97] group-hover:visible hover:text-red-600">✕</button>
                </td>
              </tr>
            ))}
            {entries.length === 0 && (
              <tr><td colSpan={7} className="px-3 py-6 text-center text-sm text-[#9b9a97]">Nenhum gasto neste mês.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
