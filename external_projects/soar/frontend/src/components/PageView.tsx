import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { apiFetch } from '../api'
import type { PageDetail } from '../types'
import { KIND_INFO } from '../types'
import CalendarPanel from './panels/CalendarPanel'
import DietPanel from './panels/DietPanel'
import GastosPanel from './panels/GastosPanel'
import MedsPanel from './panels/MedsPanel'
import RegistroItemPanel from './panels/RegistroItemPanel'
import RegistroPanel from './panels/RegistroPanel'
import TasksPanel from './panels/TasksPanel'
import VaultPanel from './panels/VaultPanel'

export default function PageView({ pageId }: { pageId: number }) {
  const queryClient = useQueryClient()
  const { data: page, isLoading, isError } = useQuery({
    queryKey: ['page', pageId],
    queryFn: () => apiFetch<PageDetail>(`/pages/${pageId}`),
  })

  const [title, setTitle] = useState('')
  const [titleDirty, setTitleDirty] = useState(false)

  useEffect(() => {
    if (page) {
      setTitle(page.title)
      setTitleDirty(false)
    }
  }, [page?.id, page?.title]) // eslint-disable-line react-hooks/exhaustive-deps

  const saveTitle = useMutation({
    mutationFn: (newTitle: string) =>
      apiFetch<PageDetail>(`/pages/${pageId}`, {
        method: 'PUT',
        body: JSON.stringify({ title: newTitle }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tree'] }),
  })

  useEffect(() => {
    if (!titleDirty || title.trim() === '') return
    const timer = setTimeout(() => {
      saveTitle.mutate(title.trim())
      setTitleDirty(false)
    }, 800)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [titleDirty, title])

  if (isLoading) return <div className="p-16 text-sm text-[#9b9a97]">Carregando página…</div>
  if (isError || !page) return <div className="p-16 text-sm text-red-600">Não foi possível carregar esta página.</div>

  const kindInfo = KIND_INFO[page.kind]

  return (
    <div className="mx-auto w-full max-w-4xl px-6 pt-10 pb-24 md:px-10">
      <div className="mb-2 flex items-center gap-2 text-xs text-[#9b9a97]">
        <span className="rounded bg-[#f1f1ef] px-1.5 py-0.5">
          {page.scope === 'shared' ? '👥 Compartilhado' : '🔒 Pessoal'}
        </span>
        {page.kind !== 'note' && (
          <span className="rounded bg-[#f1f1ef] px-1.5 py-0.5">
            {kindInfo.icon} {kindInfo.label}
          </span>
        )}
      </div>

      <div className="mb-1 text-5xl">{page.icon ?? kindInfo.icon}</div>

      <input
        value={title}
        onChange={(e) => {
          setTitle(e.target.value)
          setTitleDirty(true)
        }}
        placeholder="Sem título"
        className="mb-6 w-full border-none bg-transparent text-4xl font-bold text-[#37352f] outline-none placeholder:text-[#d3d1cb]"
      />

      {page.kind === 'note' && (
        <>
          <SubpagesList page={page} />
          <NoteEditor page={page} />
        </>
      )}
      {page.kind === 'tasks' && <TasksPanel pageId={page.id} />}
      {page.kind === 'calendar' && <CalendarPanel pageId={page.id} />}
      {page.kind === 'vault' && <VaultPanel pageId={page.id} />}
      {page.kind === 'registro' && <RegistroPanel page={page} />}
      {page.kind === 'registro_item' && <RegistroItemPanel page={page} />}
      {page.kind === 'meds' && <MedsPanel pageId={page.id} />}
      {page.kind === 'diet' && <DietPanel page={page} />}
      {page.kind === 'gastos' && <GastosPanel pageId={page.id} />}
    </div>
  )
}

function SubpagesList({ page }: { page: PageDetail }) {
  if (page.children.length === 0) return null
  return (
    <div className="mb-6 grid grid-cols-2 gap-2 md:grid-cols-3">
      {page.children.map((child) => (
        <Link
          key={child.id}
          to={`/p/${child.id}`}
          className="rounded-lg border border-[#e9e9e7] px-3 py-2.5 text-sm font-medium transition hover:bg-[#f7f7f5]"
        >
          {child.icon ?? KIND_INFO[child.kind].icon} {child.title}
        </Link>
      ))}
    </div>
  )
}

function NoteEditor({ page }: { page: PageDetail }) {
  const [content, setContent] = useState(page.content ?? '')
  const [dirty, setDirty] = useState(false)
  const [savedAt, setSavedAt] = useState<Date | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const save = useMutation({
    mutationFn: (newContent: string) =>
      apiFetch(`/pages/${page.id}`, { method: 'PUT', body: JSON.stringify({ content: newContent }) }),
    onSuccess: () => setSavedAt(new Date()),
  })

  useEffect(() => {
    if (!dirty) return
    const timer = setTimeout(() => {
      save.mutate(content)
      setDirty(false)
    }, 800)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dirty, content])

  useEffect(() => {
    const el = textareaRef.current
    if (el) {
      el.style.height = 'auto'
      el.style.height = `${el.scrollHeight}px`
    }
  }, [content])

  return (
    <div>
      <div className="mb-1 h-4 text-xs text-[#9b9a97]">
        {save.isPending
          ? 'Salvando…'
          : savedAt
            ? `Salvo às ${savedAt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`
            : ''}
      </div>
      <textarea
        ref={textareaRef}
        value={content}
        onChange={(e) => {
          setContent(e.target.value)
          setDirty(true)
        }}
        placeholder="Escreva algo… (as alterações são salvas automaticamente)"
        className="w-full resize-none border-none bg-transparent text-[15px] leading-7 text-[#37352f] outline-none placeholder:text-[#d3d1cb]"
        rows={6}
      />
    </div>
  )
}
