import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { apiFetch } from '../../api'
import type { PageDetail } from '../../types'
import { Btn, inputCls } from '../ui'

/**
 * ITEM de registro (ex.: um cartão): ficha individual com os campos definidos
 * no template da categoria-pai.
 */
export default function RegistroItemPanel({ page }: { page: PageDetail }) {
  const queryClient = useQueryClient()
  const template = page.parent?.meta?.template ?? []
  const [data, setData] = useState<Record<string, string>>(() => ({ ...(page.meta?.data ?? {}) }))
  const [savedAt, setSavedAt] = useState<Date | null>(null)

  const save = useMutation({
    mutationFn: () => {
      const firstValue = template.map((f) => data[f.key]).find((v) => v && v.trim())
      const body: Record<string, unknown> = { meta: { ...(page.meta ?? {}), data } }
      if (firstValue && (page.title === 'Novo item' || page.title === 'Item')) {
        body.title = firstValue
      }
      return apiFetch(`/pages/${page.id}`, { method: 'PUT', body: JSON.stringify(body) })
    },
    onSuccess: () => {
      setSavedAt(new Date())
      queryClient.invalidateQueries({ queryKey: ['page', page.id] })
      queryClient.invalidateQueries({ queryKey: ['page', page.parent?.id] })
      queryClient.invalidateQueries({ queryKey: ['tree'] })
    },
  })

  if (!page.parent || template.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-[#e9e9e7] p-6 text-center text-sm text-[#9b9a97]">
        A categoria deste item não tem campos definidos — abra a categoria e configure em ⚙ Campos.
      </p>
    )
  }

  return (
    <div className="space-y-4">
      <p className="text-xs text-[#9b9a97]">
        Item de{' '}
        <Link to={`/p/${page.parent.id}`} className="text-[#2383e2] hover:underline">
          {page.parent.title}
        </Link>
      </p>

      <form
        className="max-w-xl space-y-3 rounded-lg border border-[#e9e9e7] bg-[#fbfbfa] p-5"
        onSubmit={(e) => {
          e.preventDefault()
          save.mutate()
        }}
      >
        {template.map((field) => (
          <label key={field.key} className="flex flex-col gap-1 text-xs font-medium text-[#787774]">
            {field.label}
            <input
              type={field.type ?? 'text'}
              value={data[field.key] ?? ''}
              onChange={(e) => setData({ ...data, [field.key]: e.target.value })}
              className={inputCls}
            />
          </label>
        ))}
        <div className="flex items-center gap-3 pt-1">
          <Btn disabled={save.isPending}>Salvar</Btn>
          <span className="text-xs text-[#9b9a97]">
            {save.isPending
              ? 'Salvando…'
              : savedAt
                ? `Salvo às ${savedAt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`
                : ''}
          </span>
        </div>
      </form>
    </div>
  )
}
