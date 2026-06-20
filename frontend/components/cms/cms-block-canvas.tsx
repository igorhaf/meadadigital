'use client'

import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { FieldRenderer } from '@/components/cms/field-renderer'
import { renderCmsBlock, cmsShellStyle } from '@/components/cms/cms-render'
import { allBlockSchemas, blockSchema } from '@/lib/cms/cms-block-schemas'
import { blockTypeLabel, defaultProps, type CmsBlock, type CmsBlockTypeId } from '@/lib/cms/cms-block-type'

/**
 * Editor visual de 3 painéis do CMS — ESQUERDA: lista de blocos (selecionar/reordenar/excluir +
 * catálogo) · CENTRO: preview AO VIVO (renderiza os blocos REAIS via renderCmsBlock, com o tema do
 * tenant) · DIREITA: propriedades schema-driven (FieldRenderer) do bloco selecionado.
 *
 * Não faz fetch nem save — recebe `blocks`/`setBlocks` da página, que mantém o contrato com a API.
 * Edição muta `block.props` por id (preserva o id pro contrato JSON). `newId` vem da página.
 */
export function CmsBlockCanvas({
  blocks,
  setBlocks,
  theme,
  newId,
}: {
  blocks: CmsBlock[]
  setBlocks: React.Dispatch<React.SetStateAction<CmsBlock[]>>
  theme: { primaryColor: string; dark: boolean }
  newId: () => string
}) {
  const [selectedId, setSelectedId] = useState<string | null>(blocks[0]?.id ?? null)
  const [showCatalog, setShowCatalog] = useState(false)

  const selected = blocks.find((b) => b.id === selectedId) ?? null
  const schema = selected ? blockSchema(selected.type) : undefined
  const shell = cmsShellStyle({ primaryColor: theme.primaryColor, dark: theme.dark })

  function updateProps(id: string, props: CmsBlock['props']) {
    setBlocks((bs) => bs.map((b) => (b.id === id ? ({ ...b, props } as CmsBlock) : b)))
  }
  function move(id: string, dir: -1 | 1) {
    setBlocks((bs) => {
      const i = bs.findIndex((b) => b.id === id)
      const target = i + dir
      if (i < 0 || target < 0 || target >= bs.length) return bs
      const next = [...bs]
      ;[next[i], next[target]] = [next[target], next[i]]
      return next
    })
  }
  function remove(id: string) {
    setBlocks((bs) => bs.filter((b) => b.id !== id))
    if (selectedId === id) setSelectedId(null)
  }
  function addBlock(type: CmsBlockTypeId) {
    const block = { id: newId(), type, props: defaultProps(type) } as CmsBlock
    setBlocks((bs) => [...bs, block])
    setSelectedId(block.id)
    setShowCatalog(false)
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[240px_1fr_340px]">
      {/* ESQUERDA — lista de blocos */}
      <aside className="space-y-2">
        <Button type="button" className="w-full" onClick={() => setShowCatalog(true)}>+ Adicionar bloco</Button>
        {blocks.length === 0 && (
          <p className="rounded-lg border border-dashed border-border p-4 text-center text-xs text-muted-foreground">Nenhum bloco ainda.</p>
        )}
        <ul className="space-y-1">
          {blocks.map((b, i) => {
            const s = blockSchema(b.type)
            const isSel = b.id === selectedId
            return (
              <li key={b.id}>
                <div className={`group flex items-center gap-1 rounded-md border px-2 py-2 text-sm ${isSel ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted/50'}`}>
                  <button type="button" className="flex min-w-0 flex-1 items-center gap-2 text-left" onClick={() => setSelectedId(b.id)}>
                    <span aria-hidden>{s?.emoji ?? '▫️'}</span>
                    <span className="truncate">{s?.label ?? blockTypeLabel(b.type)}</span>
                  </button>
                  <span className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                    <button type="button" onClick={() => move(b.id, -1)} disabled={i === 0} className="rounded px-1 text-xs hover:bg-muted disabled:opacity-30" aria-label="Subir">↑</button>
                    <button type="button" onClick={() => move(b.id, 1)} disabled={i === blocks.length - 1} className="rounded px-1 text-xs hover:bg-muted disabled:opacity-30" aria-label="Descer">↓</button>
                    <button type="button" onClick={() => remove(b.id)} className="rounded px-1 text-xs text-destructive hover:bg-destructive/10" aria-label="Excluir">✕</button>
                  </span>
                </div>
              </li>
            )
          })}
        </ul>
      </aside>

      {/* CENTRO — preview ao vivo (blocos reais) */}
      <div className="overflow-hidden rounded-lg border border-border">
        {blocks.length === 0 ? (
          <div className="flex min-h-[20rem] items-center justify-center text-sm text-muted-foreground">
            O preview aparece aqui conforme você adiciona blocos.
          </div>
        ) : (
          <div style={shell}>
            {blocks.map((b) => {
              const isSel = b.id === selectedId
              return (
                <div
                  key={b.id}
                  onClick={() => setSelectedId(b.id)}
                  className={`relative cursor-pointer ${isSel ? 'ring-2 ring-inset ring-primary' : ''}`}
                >
                  {/* impede que cliques/links do bloco naveguem dentro do editor */}
                  <div className="pointer-events-none">{renderCmsBlock(b)}</div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* DIREITA — propriedades schema-driven */}
      <aside className="space-y-3">
        {!selected ? (
          <p className="rounded-lg border border-dashed border-border p-4 text-center text-xs text-muted-foreground">
            Selecione um bloco para editar.
          </p>
        ) : !schema ? (
          <p className="text-xs text-muted-foreground">Tipo de bloco sem editor: {blockTypeLabel(selected.type)}.</p>
        ) : (
          <div className="space-y-4 rounded-lg border border-border p-4">
            <div>
              <div className="flex items-center gap-2 font-medium">
                <span aria-hidden>{schema.emoji}</span> {schema.label}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">{schema.description}</p>
            </div>
            <div className="space-y-3">
              {schema.fields.map((f) => (
                <FieldRenderer
                  key={f.key}
                  field={f}
                  value={(selected.props as Record<string, unknown>)[f.key]}
                  onChange={(v) => updateProps(selected.id, { ...selected.props, [f.key]: v } as CmsBlock['props'])}
                />
              ))}
            </div>
          </div>
        )}
      </aside>

      {/* CATÁLOGO */}
      <Modal open={showCatalog} onClose={() => setShowCatalog(false)} title="Adicionar bloco" size="lg">
        <div className="grid gap-3 sm:grid-cols-2">
          {allBlockSchemas().map((s) => (
            <button
              key={s.type}
              type="button"
              onClick={() => addBlock(s.type)}
              className="rounded-lg border border-border p-4 text-left transition-colors hover:border-primary hover:bg-primary/5"
            >
              <div className="flex items-center gap-2 font-medium">
                <span aria-hidden className="text-lg">{s.emoji}</span> {s.label}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">{s.description}</p>
            </button>
          ))}
        </div>
      </Modal>
    </div>
  )
}
