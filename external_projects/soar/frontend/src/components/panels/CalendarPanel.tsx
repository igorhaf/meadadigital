import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { apiFetch } from '../../api'
import type { CalendarEvent } from '../../types'
import { Btn, TextInput, inputCls } from '../ui'

const WEEKDAYS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb']
const MONTHS = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

export default function CalendarPanel({ pageId }: { pageId: number }) {
  const queryClient = useQueryClient()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth()) // 0-based
  const [title, setTitle] = useState('')
  const [date, setDate] = useState(now.toISOString().slice(0, 10))
  const [time, setTime] = useState('')
  const [duration, setDuration] = useState('60')

  const from = new Date(year, month, 1).toISOString()
  const to = new Date(year, month + 1, 1).toISOString()

  const { data: events = [] } = useQuery({
    queryKey: ['events', pageId, year, month],
    queryFn: () => apiFetch<CalendarEvent[]>(`/pages/${pageId}/events?from=${from}&to=${to}`),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['events', pageId] })

  const create = useMutation({
    mutationFn: () => {
      const starts = time ? `${date}T${time}:00` : `${date}T00:00:00`
      const ends = time
        ? new Date(new Date(starts).getTime() + Number(duration) * 60000).toISOString()
        : null
      return apiFetch(`/pages/${pageId}/events`, {
        method: 'POST',
        body: JSON.stringify({ title, starts_at: starts, ends_at: ends, all_day: !time }),
      })
    },
    onSuccess: () => {
      setTitle('')
      invalidate()
    },
  })

  const remove = useMutation({
    mutationFn: (id: number) => apiFetch(`/pages/${pageId}/events/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })

  function prevMonth() {
    if (month === 0) { setMonth(11); setYear(year - 1) } else setMonth(month - 1)
  }
  function nextMonth() {
    if (month === 11) { setMonth(0); setYear(year + 1) } else setMonth(month + 1)
  }

  const firstWeekday = new Date(year, month, 1).getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: (number | null)[] = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ]
  const todayStr = new Date().toISOString().slice(0, 10)

  const byDay = new Map<number, CalendarEvent[]>()
  for (const event of events) {
    const day = new Date(event.starts_at).getDate()
    byDay.set(day, [...(byDay.get(day) ?? []), event])
  }

  return (
    <div className="space-y-6">
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          if (title.trim()) create.mutate()
        }}
      >
        <TextInput value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Novo evento…" className="min-w-48 flex-1" />
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className={inputCls} required />
        <input type="time" value={time} onChange={(e) => setTime(e.target.value)} className={inputCls} title="Vazio = dia inteiro" />
        <select value={duration} onChange={(e) => setDuration(e.target.value)} className={inputCls} disabled={!time}>
          <option value="30">30 min</option>
          <option value="60">1h</option>
          <option value="90">1h30</option>
          <option value="120">2h</option>
        </select>
        <Btn disabled={!title.trim() || create.isPending}>Adicionar</Btn>
      </form>

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h3 className="text-sm font-semibold">{MONTHS[month]} {year}</h3>
          <div className="flex gap-1">
            <button onClick={prevMonth} className="rounded px-2 py-0.5 text-sm hover:bg-[#f1f1ef]">←</button>
            <button onClick={() => { setYear(now.getFullYear()); setMonth(now.getMonth()) }} className="rounded px-2 py-0.5 text-xs hover:bg-[#f1f1ef]">hoje</button>
            <button onClick={nextMonth} className="rounded px-2 py-0.5 text-sm hover:bg-[#f1f1ef]">→</button>
          </div>
        </div>

        <div className="grid grid-cols-7 overflow-hidden rounded-lg border border-[#e9e9e7] text-xs">
          {WEEKDAYS.map((d) => (
            <div key={d} className="border-b border-[#e9e9e7] bg-[#f7f7f5] px-1 py-1 text-center font-medium text-[#787774]">
              {d}
            </div>
          ))}
          {cells.map((day, i) => {
            const dayStr = day ? `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}` : ''
            return (
              <div
                key={i}
                onClick={() => day && setDate(dayStr)}
                className={`min-h-16 cursor-pointer border-b border-r border-[#f1f1ef] p-1 align-top hover:bg-[#f7f7f5] ${day ? '' : 'bg-[#fbfbfa]'}`}
              >
                {day && (
                  <>
                    <span className={`inline-block h-5 w-5 rounded-full text-center leading-5 ${dayStr === todayStr ? 'bg-[#2383e2] text-white' : 'text-[#787774]'}`}>
                      {day}
                    </span>
                    {(byDay.get(day) ?? []).map((event) => (
                      <div key={event.id} className="group mt-0.5 flex items-start gap-1 rounded bg-[#e7f0fb] px-1 py-0.5 text-[11px] leading-tight text-[#1a5da0]" title={event.notes ?? ''}>
                        <span className="flex-1 truncate">
                          {!event.all_day && <b>{new Date(event.starts_at).toTimeString().slice(0, 5)} </b>}
                          {event.title}
                        </span>
                        <button
                          onClick={(e) => { e.stopPropagation(); if (confirm(`Excluir "${event.title}"?`)) remove.mutate(event.id) }}
                          className="invisible group-hover:visible hover:text-red-600"
                        >
                          ✕
                        </button>
                      </div>
                    ))}
                  </>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
