import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { apiFetch } from '../../api'
import type { PageChild, PageDetail, RegistroField } from '../../types'
import { Btn, GhostBtn, TextInput, inputCls } from '../ui'

/**
 * CATEGORIA de registro (ex.: Cartões): define os campos (template) e lista
 * cada item — que é uma PÁGINA FILHA (aparece na árvore e abre como ficha).
 */
export default function RegistroPanel({ page }: { page: PageDetail }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [editingTemplate, setEditingTemplate] = useState(false)
  const [fields, setFields] = useState<RegistroField[]>([])

  const template = page.meta?.template ?? []
  const items = page.children.filter((child) => child.kind === 'registro_item')

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['page', page.id] })
    queryClient.invalidateQueries({ queryKey: ['tree'] })
  }

  const saveTemplate = useMutation({
    mutationFn: () =>
      apiFetch(`/pages/${page.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          meta: {
            ...(page.meta ?? {}),
            template: fields
              .filter((f) => f.label.trim())
              .map((f) => ({ ...f, key: f.key || slug(f.label) })),
          },
        }),
      }),
    onSuccess: () => {
      setEditingTemplate(false)
      invalidate()
    },
  })

  const createItem = useMutation({
    mutationFn: () =>
      apiFetch<PageDetail>('/pages', {
        method: 'POST',
        body: JSON.stringify({
          scope: page.scope,
          parent_id: page.id,
          kind: 'registro_item',
          title: `Novo item`,
          meta: { data: {} },
        }),
      }),
    onSuccess: (item) => {
      invalidate()
      navigate(`/p/${item.id}`)
    },
  })

  function slug(text: string): string {
    return text.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '')
  }

  function startTemplateEdit() {
    setFields(template.length ? [...template] : [{ key: '', label: '' }])
    setEditingTemplate(true)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-xs text-[#9b9a97]">
          Cada item é uma subpágina — clique pra abrir a ficha. Dá pra criar pelo Telegram também.
        </p>
        <div className="flex gap-2">
          <GhostBtn onClick={startTemplateEdit}>⚙ Campos</GhostBtn>
          {template.length > 0 && (
            <Btn type="button" onClick={() => createItem.mutate()} disabled={createItem.isPending}>
              + Novo item
            </Btn>
          )}
        </div>
      </div>

      {editingTemplate && (
        <div className="space-y-2 rounded-lg border border-[#e9e9e7] bg-[#fbfbfa] p-4">
          <p className="text-xs font-medium text-[#787774]">Campos deste registro:</p>
          {fields.map((field, i) => (
            <div key={i} className="flex gap-2">
              <TextInput
                value={field.label}
                onChange={(e) => setFields(fields.map((f, j) => (j === i ? { ...f, label: e.target.value } : f)))}
                placeholder={`Campo ${i + 1}`}
                className="flex-1"
              />
              <select
                value={field.type ?? 'text'}
                onChange={(e) => setFields(fields.map((f, j) => (j === i ? { ...f, type: e.target.value as RegistroField['type'] } : f)))}
                className={inputCls}
              >
                <option value="text">texto</option>
                <option value="number">número</option>
                <option value="date">data</option>
              </select>
              <button type="button" onClick={() => setFields(fields.filter((_, j) => j !== i))} className="text-[#9b9a97] hover:text-red-600">✕</button>
            </div>
          ))}
          <div className="flex gap-2 pt-1">
            <GhostBtn onClick={() => setFields([...fields, { key: '', label: '' }])}>+ campo</GhostBtn>
            <Btn type="button" onClick={() => saveTemplate.mutate()} disabled={saveTemplate.isPending}>Salvar campos</Btn>
            <GhostBtn onClick={() => setEditingTemplate(false)}>Cancelar</GhostBtn>
          </div>
        </div>
      )}

      {template.length === 0 ? (
        <p className="rounded-lg border border-dashed border-[#e9e9e7] p-6 text-center text-sm text-[#9b9a97]">
          Defina os campos deste registro em <b>⚙ Campos</b> (ex.: Banco, Bandeira, Vencimento…).
        </p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-[#e9e9e7]">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[#e9e9e7] bg-[#f7f7f5] text-left text-xs text-[#787774]">
                <th className="px-3 py-2 font-medium">Item</th>
                {template.map((f) => <th key={f.key} className="px-3 py-2 font-medium">{f.label}</th>)}
              </tr>
            </thead>
            <tbody>
              {items.map((item: PageChild) => (
                <tr
                  key={item.id}
                  onClick={() => navigate(`/p/${item.id}`)}
                  className="cursor-pointer border-b border-[#f1f1ef] last:border-0 hover:bg-[#f7f7f5]"
                >
                  <td className="px-3 py-2 font-medium">{item.icon ?? '📄'} {item.title}</td>
                  {template.map((f) => (
                    <td key={f.key} className="px-3 py-2 text-[#5f5e5b]">{item.meta?.data?.[f.key]}</td>
                  ))}
                </tr>
              ))}
              {items.length === 0 && (
                <tr>
                  <td colSpan={template.length + 1} className="px-3 py-6 text-center text-sm text-[#9b9a97]">
                    Nenhum item ainda — use “+ Novo item”.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
