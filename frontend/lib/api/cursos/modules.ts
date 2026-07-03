import { apiFetch } from '@/lib/api/client'
import type { Module } from '@/profiles/cursos/cursos-types'

export type CreateModuleInput = {
  title: string
  content?: string | null
}

export type UpdateModuleInput = Partial<CreateModuleInput>

/** Módulos são aninhados sob o curso: /api/cursos/courses/{courseId}/modules. */
export function listModules(courseId: string): Promise<{ items: Module[] }> {
  return apiFetch<{ items: Module[] }>(`/api/cursos/courses/${courseId}/modules`)
}

export function createModule(courseId: string, input: CreateModuleInput): Promise<Module> {
  return apiFetch<Module>(`/api/cursos/courses/${courseId}/modules`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateModule(
  courseId: string,
  moduleId: string,
  input: UpdateModuleInput,
): Promise<Module> {
  return apiFetch<Module>(`/api/cursos/courses/${courseId}/modules/${moduleId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteModule(courseId: string, moduleId: string): Promise<void> {
  return apiFetch<void>(`/api/cursos/courses/${courseId}/modules/${moduleId}`, { method: 'DELETE' })
}

/** Reordena os módulos do curso: envia a lista de ids na ordem desejada (position 0..N). */
export function reorderModules(
  courseId: string,
  moduleIds: string[],
): Promise<{ items: Module[] }> {
  return apiFetch<{ items: Module[] }>(`/api/cursos/courses/${courseId}/modules/reorder`, {
    method: 'PATCH',
    body: JSON.stringify({ moduleIds }),
  })
}
