'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createCourse,
  deleteCourse,
  listCourses,
  toggleCourse,
  updateCourse,
} from '@/lib/api/cursos/courses'
import {
  createModule,
  deleteModule,
  listModules,
  reorderModules,
  updateModule,
} from '@/lib/api/cursos/modules'
import { formatBrl, type Course, type Module } from '@/profiles/cursos/cursos-types'

type FormState = { title: string; category: string; price: string; description: string }
const EMPTY: FormState = { title: '', category: '', price: '', description: '' }

type ModuleForm = { title: string; content: string }
const EMPTY_MODULE: ModuleForm = { title: '', content: '' }

/**
 * Cursos do CursosBot (camada 8.20). Lista com toggle ativo inline, CRUD via Modal. Editar um curso
 * também gerencia seus MÓDULOS ordenados (a escapada do perfil: position + título + conteúdo). A IA
 * oferece os cursos ativos ao matricular e entrega os módulos como material.
 */
export default function CursosCoursesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Course | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  // Editor de módulos: aberto pelo botão "Módulos" de um curso.
  const [modulesCourse, setModulesCourse] = useState<Course | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['cursos-courses'],
    queryFn: () => listCourses(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        title: form.title,
        category: form.category,
        monthlyCents: Math.round(Number(form.price || '0') * 100),
        description: form.description || null,
      }
      if (editing) return updateCourse(editing.id, payload)
      return createCourse(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cursos-courses'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o curso.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (c: Course) => toggleCourse(c.id, !c.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cursos-courses'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCourse(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cursos-courses'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'course_in_use') {
        alert('Este curso tem matrículas — não pode ser excluído. Desative-o em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(c: Course) {
    setEditing(c)
    setForm({
      title: c.title,
      category: c.category,
      price: String(c.monthlyCents / 100),
      description: c.description ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const courses = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Cursos"
        description="Cursos e seus módulos ordenados. A IA oferece os cursos ativos ao matricular."
        actions={<Button onClick={openCreate}>Novo curso</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os cursos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : courses.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum curso cadastrado ainda.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {courses.map((c) => (
            <div key={c.id} className="space-y-1 rounded-lg border border-border p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{c.title}</span>
                  <Badge variant="muted">{c.category}</Badge>
                  {!c.active && <Badge variant="muted">inativo</Badge>}
                </div>
                <span className="text-sm tabular-nums">{formatBrl(c.monthlyCents)}/mês</span>
              </div>
              {c.description && <p className="text-xs text-muted-foreground">{c.description}</p>}
              <div className="flex flex-wrap items-center gap-3 pt-1">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={c.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(c)}
                  />
                  ativo
                </label>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  onClick={() => setModulesCourse(c)}
                >
                  Módulos
                </Button>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(c)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(c.id)}
                >
                  Excluir
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal: criar/editar curso */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar curso' : 'Novo curso'}
        size="md"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Título</label>
              <input
                value={form.title}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                required
                maxLength={200}
                placeholder="Inglês Intermediário, Violão…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Categoria
              </label>
              <input
                value={form.category}
                onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                required
                maxLength={100}
                placeholder="idiomas, música, tecnologia…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Valor mensal (R$)
            </label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={form.price}
              required
              onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Descrição
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={2}
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

      {/* Modal: módulos ordenados do curso */}
      <ModulesModal course={modulesCourse} onClose={() => setModulesCourse(null)} />
    </div>
  )
}

/** Editor de módulos ordenados de um curso (a escapada do perfil cursos). */
function ModulesModal({ course, onClose }: { course: Course | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [editingModule, setEditingModule] = useState<Module | null>(null)
  const [moduleForm, setModuleForm] = useState<ModuleForm>(EMPTY_MODULE)
  const [moduleError, setModuleError] = useState<string | null>(null)

  const courseId = course?.id ?? null

  const { data, isPending } = useQuery({
    queryKey: ['cursos-modules', courseId],
    queryFn: () => listModules(courseId!),
    enabled: courseId !== null,
  })

  function invalidate() {
    qc.invalidateQueries({ queryKey: ['cursos-modules', courseId] })
  }

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = { title: moduleForm.title, content: moduleForm.content || null }
      if (editingModule) return updateModule(courseId!, editingModule.id, payload)
      return createModule(courseId!, payload)
    },
    onSuccess: () => {
      invalidate()
      setEditingModule(null)
      setModuleForm(EMPTY_MODULE)
      setModuleError(null)
    },
    onError: () => setModuleError('Erro ao salvar o módulo.'),
  })

  const deleteMutation = useMutation({
    mutationFn: (moduleId: string) => deleteModule(courseId!, moduleId),
    onSuccess: () => invalidate(),
  })

  const reorderMutation = useMutation({
    mutationFn: (ids: string[]) => reorderModules(courseId!, ids),
    onSuccess: () => invalidate(),
  })

  const modules = data?.items ?? []

  function move(index: number, dir: -1 | 1) {
    const target = index + dir
    if (target < 0 || target >= modules.length) return
    const ids = modules.map((m) => m.id)
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    reorderMutation.mutate(ids)
  }

  function startEdit(m: Module) {
    setEditingModule(m)
    setModuleForm({ title: m.title, content: m.content ?? '' })
    setModuleError(null)
  }
  function startCreate() {
    setEditingModule(null)
    setModuleForm(EMPTY_MODULE)
    setModuleError(null)
  }

  return (
    <Modal
      open={course !== null}
      onClose={onClose}
      title={course ? `Módulos — ${course.title}` : 'Módulos'}
      size="lg"
    >
      <div className="space-y-4">
        {isPending ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : modules.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nenhum módulo ainda. Adicione abaixo.</p>
        ) : (
          <div className="divide-y divide-border rounded-lg border border-border">
            {modules.map((m, i) => (
              <div key={m.id} className="flex items-center justify-between gap-3 px-3 py-2">
                <div className="flex min-w-0 items-center gap-2">
                  <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground tabular-nums">
                    {i + 1}
                  </span>
                  <div className="min-w-0">
                    <span className="block truncate text-sm font-medium">{m.title}</span>
                    {m.content && (
                      <span className="block truncate text-xs text-muted-foreground">
                        {m.content}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <Button
                    variant="outline"
                    className="h-7 w-7 p-0 text-xs"
                    disabled={i === 0 || reorderMutation.isPending}
                    onClick={() => move(i, -1)}
                    title="Subir"
                  >
                    ↑
                  </Button>
                  <Button
                    variant="outline"
                    className="h-7 w-7 p-0 text-xs"
                    disabled={i === modules.length - 1 || reorderMutation.isPending}
                    onClick={() => move(i, 1)}
                    title="Descer"
                  >
                    ↓
                  </Button>
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    onClick={() => startEdit(m)}
                  >
                    Editar
                  </Button>
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    disabled={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(m.id)}
                  >
                    Excluir
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}

        <form
          className="space-y-3 rounded-lg border border-border p-3"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <p className="text-xs font-semibold text-muted-foreground">
            {editingModule ? 'Editar módulo' : 'Novo módulo'}
          </p>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Título</label>
            <input
              value={moduleForm.title}
              onChange={(e) => setModuleForm((f) => ({ ...f, title: e.target.value }))}
              required
              maxLength={200}
              placeholder="Aula 1 — Apresentação…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Conteúdo (material entregue)
            </label>
            <textarea
              value={moduleForm.content}
              onChange={(e) => setModuleForm((f) => ({ ...f, content: e.target.value }))}
              rows={4}
              placeholder="Texto/material do módulo entregue ao aluno…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {moduleError && <p className="text-sm text-destructive">{moduleError}</p>}
          <div className="flex justify-end gap-2">
            {editingModule && (
              <Button type="button" variant="outline" onClick={startCreate}>
                Cancelar edição
              </Button>
            )}
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending
                ? 'Salvando…'
                : editingModule
                  ? 'Salvar módulo'
                  : 'Adicionar módulo'}
            </Button>
          </div>
        </form>
      </div>
    </Modal>
  )
}
