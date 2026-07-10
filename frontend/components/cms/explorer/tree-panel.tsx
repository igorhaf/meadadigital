'use client'

import {
  ArrowDown,
  ArrowUp,
  BarChart3,
  Box,
  ChevronRight,
  Columns3,
  Footprints,
  Grid3x3,
  GripVertical,
  HelpCircle,
  Images,
  Layout,
  List,
  MapPin,
  Megaphone,
  Package,
  Phone,
  Plus,
  Quote,
  Rows3,
  Sparkles,
  Type,
  Wrench,
  X,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useState } from 'react'

import { blockSchema } from '@/lib/cms/cms-block-schemas'
import { repeaterFieldsOf, slotsForType } from '@/lib/cms/cms-block-slots'
import { blockTypeLabel, type CmsRow } from '@/lib/cms/cms-block-type'
import { getItems, itemTitle } from '@/lib/cms/repeater-ops'
import { cn } from '@/lib/utils'

/** Prefixo da chave de expansão de BLOCO no Set `expanded` (que também guarda rowIds). Isola o
 * namespace: rowId vs bloco-expandido nunca colidem, mesmo sendo ambos ids únicos. */
export const blockExpandKey = (blockId: string) => `blk:${blockId}`
/** Chave de expansão de um GRUPO repeater (ex. services.items) no Set `expanded` — namespace próprio. */
export const repeaterExpandKey = (blockId: string, fieldKey: string) => `rep:${blockId}:${fieldKey}`

/**
 * Painel "explorer" do page builder (árvore root → linhas → colunas → blocos). Substitui a lista flat
 * de blocos da SM-N. Cada nó tem ↑↓✕ (reordenar/excluir) — drag-drop é camada por cima (Fase 4), os
 * botões são o caminho à prova de furo (a11y + fallback). A seleção (linha/coluna/bloco) controla o
 * painel direito de propriedades (3 modos) lá no editor; aqui só emitimos os callbacks.
 *
 * VISUAL — VERSÃO FINAL ("vscode", padrão dos sites futuros do Meada). Réplica fiel do file-tree do
 * VSCode: linhas baixas (h-6 ≈ 24px) SEM borda/caixa por nó — densidade máxima, calmo. Hover sutil
 * (bg-muted/40); seleção por realce leve (bg-primary/10 + text-primary), NUNCA por borda; drop-target
 * por anel interno fino (ring-inset). Hierarquia carregada SÓ pelos rails de indentação (border-l
 * border-border/60, com pl pra os filhos respirarem off-guia). Caret discreto: UM chevron que gira
 * 90° ao expandir (sem par chevron-down/right). Folhas (coluna/bloco) reservam o slot do chevron pra
 * alinhar. Ícones lucide MONOCROMÁTICOS (size-3.5, text-muted-foreground) por TIPO de bloco — sem
 * emojis coloridos, mas mantendo a diferenciação visual por tipo. Meta (nº de colunas / width)
 * minúscula, tabular-nums, sem parênteses, calma no normal e revelada no hover/seleção. Grip + ↑↓✕
 * só surgem no hover/foco. "root" vira um rótulo discreto, sem caixa tracejada nem no empty-state.
 *
 * A LÓGICA É INTOCADA — só o markup/estilo mudou: props, Selection, expanded Set, os 5 handlers de
 * drag nos MESMOS 3 divs draggable (linha/coluna/bloco), dropOnRow/dropOnColumn/endDrag, o estado
 * drag/over e TODOS os callbacks continuam ligados nos mesmos lugares.
 */

export type Selection =
  | { kind: 'row'; rowId: string }
  | { kind: 'column'; rowId: string; colId: string }
  | { kind: 'block'; rowId: string; colId: string; blockId: string }
  | { kind: 'slot'; rowId: string; colId: string; blockId: string; slotId: string }
  | {
      kind: 'item'
      rowId: string
      colId: string
      blockId: string
      fieldKey: string
      itemIndex: number
    }
  | null

export type TreePanelProps = {
  tree: CmsRow[]
  selection: Selection
  expanded: Set<string>
  onSelect: (sel: Selection) => void
  onToggle: (rowId: string) => void
  // linhas
  onAddRow: () => void
  onMoveRow: (rowId: string, dir: -1 | 1) => void
  onRemoveRow: (rowId: string) => void
  // colunas
  onAddColumn: (rowId: string) => void
  onMoveColumn: (rowId: string, colId: string, dir: -1 | 1) => void
  onRemoveColumn: (rowId: string, colId: string) => void
  // blocos
  onAddBlock: (rowId: string, colId: string) => void
  onMoveBlock: (rowId: string, colId: string, blockId: string, dir: -1 | 1) => void
  onRemoveBlock: (rowId: string, colId: string, blockId: string) => void
  // itens de repeater (2ª família de sub-nós: services.items, gallery.images…)
  onAddItem: (rowId: string, colId: string, blockId: string, fieldKey: string) => void
  onMoveItem: (
    rowId: string,
    colId: string,
    blockId: string,
    fieldKey: string,
    itemIndex: number,
    dir: -1 | 1,
  ) => void
  onRemoveItem: (
    rowId: string,
    colId: string,
    blockId: string,
    fieldKey: string,
    itemIndex: number,
  ) => void
  // drag-drop (Fase 4) — reordenar linhas/colunas, mover bloco entre colunas. Os ↑↓✕ continuam como fallback.
  onReorderRow: (dragId: string, targetId: string) => void
  onReorderColumn: (rowId: string, dragColId: string, targetColId: string) => void
  onMoveBlockAcross: (
    from: { rowId: string; colId: string; blockId: string },
    to: { rowId: string; colId: string },
  ) => void
}

/** Descritor do arrasto em curso (HTML5 nativo). Só um por vez. */
type Drag =
  | { kind: 'row'; rowId: string }
  | { kind: 'column'; rowId: string; colId: string }
  | { kind: 'block'; rowId: string; colId: string; blockId: string }
  | null

/**
 * Mapa tipo-de-bloco → ícone lucide monocromático. Substitui os emojis coloridos do schema (s.emoji),
 * que eram visualmente ruidosos, MANTENDO a diferenciação por tipo (a folha não vira tudo "quadradinho
 * igual"). Fallback: Box (caixa genérica) pra qualquer tipo futuro ainda não mapeado. Decisão só
 * VISUAL — o label continua vindo do schema/blockTypeLabel.
 */
const BLOCK_ICONS: Record<string, LucideIcon> = {
  hero: Sparkles,
  text: Type,
  services: Wrench,
  contact: Phone,
  gallery: Images,
  faq: HelpCircle,
  testimonials: Quote,
  map: MapPin,
  banner_strip: Megaphone,
  stats: BarChart3,
  feature_grid: Grid3x3,
  image_text_split: Layout,
  steps: Footprints,
  columns: Columns3,
  packages: Package,
  marquee: Megaphone,
  quote: Quote,
  cta: Megaphone,
  meada_hero: Sparkles,
  meada_services: Wrench,
  meada_portfolio: Images,
  meada_cta: Megaphone,
  meada_navbar: Layout,
  meada_footer: Layout,
}

/**
 * Botões de fallback a11y do drag-drop (↑ ↓ ✕). Mesma API/lógica — só ficaram discretos: ícones lucide
 * size-3, escondidos por padrão e revelados no hover/foco do nó (group-hover / focus-within).
 */
function NodeButtons({
  onUp,
  onDown,
  onRemove,
  upDisabled,
  downDisabled,
  removeLabel,
}: {
  onUp: () => void
  onDown: () => void
  onRemove: () => void
  upDisabled: boolean
  downDisabled: boolean
  removeLabel: string
}) {
  return (
    <span className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation()
          onUp()
        }}
        disabled={upDisabled}
        className="grid size-4 place-items-center rounded-sm text-muted-foreground hover:bg-muted hover:text-foreground disabled:opacity-25 disabled:hover:bg-transparent"
        aria-label="Subir"
      >
        <ArrowUp className="size-3" aria-hidden />
      </button>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation()
          onDown()
        }}
        disabled={downDisabled}
        className="grid size-4 place-items-center rounded-sm text-muted-foreground hover:bg-muted hover:text-foreground disabled:opacity-25 disabled:hover:bg-transparent"
        aria-label="Descer"
      >
        <ArrowDown className="size-3" aria-hidden />
      </button>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation()
          onRemove()
        }}
        className="grid size-4 place-items-center rounded-sm text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
        aria-label={removeLabel}
      >
        <X className="size-3" aria-hidden />
      </button>
    </span>
  )
}

/**
 * Meta minúscula (ex.: contagem de colunas, width). tabular-nums pra alinhar dígitos, sem parênteses;
 * só aparece no hover ou quando o nó está selecionado (data-selected) — fica calma no estado normal.
 */
function Meta({ children }: { children: React.ReactNode }) {
  return (
    <span className="shrink-0 text-[10px] text-muted-foreground/70 tabular-nums opacity-0 transition-opacity group-hover:opacity-100 group-data-[selected=true]:opacity-100">
      {children}
    </span>
  )
}

export function TreePanel(p: TreePanelProps) {
  const sel = p.selection
  const [drag, setDrag] = useState<Drag>(null)
  const [over, setOver] = useState<string | null>(null) // id do nó sob o cursor (highlight)

  // só permite soltar onde o tipo arrastado faz sentido; devolve o handler de drop ou null.
  function dropOnRow(rowId: string) {
    if (drag?.kind !== 'row') return null
    return () => {
      if (drag.rowId !== rowId) p.onReorderRow(drag.rowId, rowId)
      setDrag(null)
      setOver(null)
    }
  }
  function dropOnColumn(rowId: string, colId: string) {
    if (drag?.kind === 'column' && drag.rowId === rowId) {
      return () => {
        if (drag.colId !== colId) p.onReorderColumn(rowId, drag.colId, colId)
        setDrag(null)
        setOver(null)
      }
    }
    if (drag?.kind === 'block') {
      // soltar um bloco SOBRE uma coluna = mover o bloco pro fim daquela coluna (entre colunas ou na mesma).
      return () => {
        if (drag.colId !== colId)
          p.onMoveBlockAcross(
            { rowId: drag.rowId, colId: drag.colId, blockId: drag.blockId },
            { rowId, colId },
          )
        setDrag(null)
        setOver(null)
      }
    }
    return null
  }
  function endDrag() {
    setDrag(null)
    setOver(null)
  }

  // Classe-base de QUALQUER nó (linha/coluna/bloco): row densa de IDE, h-6, sem borda/caixa própria.
  // A hierarquia vem dos rails dos contêineres filhos, não de fundo no nó. Realce de seleção leve;
  // hover sutil; drop-target marca um anel interno fino. transition-colors pra mudança calma.
  function nodeRow(selected: boolean, isOver: boolean) {
    return cn(
      'group flex h-6 cursor-pointer items-center gap-1 pr-1.5 pl-1 transition-colors select-none',
      selected ? 'bg-primary/10 text-primary' : 'text-foreground hover:bg-muted/40',
      isOver && 'ring-1 ring-primary ring-inset',
    )
  }
  // Slot vazio do tamanho do chevron — folhas (coluna/bloco) o usam pra alinhar com a linha.
  const chevronSlot = 'size-4 shrink-0'
  // Handle de drag só DECORATIVO — surge no hover à esquerda; o div pai inteiro é que é draggable.
  const grip =
    'size-3 shrink-0 cursor-grab text-muted-foreground/40 opacity-0 transition-opacity group-hover:opacity-100'
  // Rail de indentação: guia vertical de 1px + respiro pros filhos saírem de cima da linha.
  const rail = 'ml-[14px] border-l border-border/60 pl-1.5'
  // Botão "adicionar" (coluna/bloco): link discreto alinhado com os irmãos, sem caixa tracejada.
  const addLink =
    'flex h-6 w-full items-center gap-1.5 pl-1 pr-1.5 text-left text-muted-foreground/70 transition-colors hover:bg-muted/40 hover:text-foreground'

  return (
    <div className="flex h-full flex-col">
      {/* topo: ação primária única (criar linha), botão compacto */}
      <div className="border-b border-border p-2">
        <button
          type="button"
          onClick={p.onAddRow}
          className="flex w-full items-center justify-center gap-1.5 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90"
        >
          <Plus className="size-3.5" aria-hidden /> Adicionar linha
        </button>
      </div>

      <div className="flex-1 overflow-y-auto py-1 text-xs">
        {/* raiz minimizada: só um rótulo calmo (sem emoji, sem uppercase gritante, sem caixa) */}
        <div className="flex h-6 items-center px-2 text-[10px] font-medium tracking-wide text-muted-foreground/60">
          Estrutura
        </div>

        {p.tree.length === 0 && (
          <p className="px-2 py-1 text-[11px] text-muted-foreground/70">
            Nenhuma linha ainda. Clique em “Adicionar linha”.
          </p>
        )}

        <ul>
          {p.tree.map((row, ri) => {
            const open = p.expanded.has(row.id)
            const rowSel = sel?.kind === 'row' && sel.rowId === row.id
            return (
              <li key={row.id}>
                {/* nó LINHA — draggable + os 5 handlers de drag INTACTOS; markup é linha densa de IDE */}
                <div
                  draggable
                  data-selected={rowSel}
                  onDragStart={(e) => {
                    setDrag({ kind: 'row', rowId: row.id })
                    e.dataTransfer.effectAllowed = 'move'
                  }}
                  onDragEnd={endDrag}
                  onDragOver={(e) => {
                    if (dropOnRow(row.id)) {
                      e.preventDefault()
                      setOver(row.id)
                    }
                  }}
                  onDragLeave={() => setOver((o) => (o === row.id ? null : o))}
                  onDrop={(e) => {
                    const fn = dropOnRow(row.id)
                    if (fn) {
                      e.preventDefault()
                      fn()
                    }
                  }}
                  className={nodeRow(rowSel, over === row.id)}
                >
                  <GripVertical className={grip} aria-hidden />
                  {/* caret discreto: UM chevron que gira 90° ao expandir (estilo VSCode) */}
                  <button
                    type="button"
                    onClick={() => p.onToggle(row.id)}
                    aria-label={open ? 'Recolher' : 'Expandir'}
                    className="grid size-4 shrink-0 place-items-center rounded-sm text-muted-foreground hover:bg-muted"
                  >
                    <ChevronRight
                      className={cn('size-3.5 transition-transform', open && 'rotate-90')}
                      aria-hidden
                    />
                  </button>
                  <button
                    type="button"
                    onClick={() => p.onSelect({ kind: 'row', rowId: row.id })}
                    className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
                  >
                    <Rows3
                      className={cn(
                        'size-3.5 shrink-0',
                        rowSel ? 'text-primary' : 'text-muted-foreground',
                      )}
                      aria-hidden
                    />
                    <span className="truncate">Linha {ri + 1}</span>
                    <Meta>{row.columns.length} col</Meta>
                  </button>
                  <NodeButtons
                    onUp={() => p.onMoveRow(row.id, -1)}
                    onDown={() => p.onMoveRow(row.id, 1)}
                    onRemove={() => p.onRemoveRow(row.id)}
                    upDisabled={ri === 0}
                    downDisabled={ri === p.tree.length - 1}
                    removeLabel="Excluir linha"
                  />
                </div>

                {/* colunas da linha — rail vertical de 1px, indentação enxuta (alinha com o chevron da linha) */}
                {open && (
                  <div className={rail}>
                    {row.columns.map((col, ci) => {
                      const colSel = sel?.kind === 'column' && sel.colId === col.id
                      return (
                        <div key={col.id}>
                          {/* nó COLUNA — draggable + handlers INTACTOS */}
                          <div
                            draggable
                            data-selected={colSel}
                            onDragStart={(e) => {
                              e.stopPropagation()
                              setDrag({ kind: 'column', rowId: row.id, colId: col.id })
                              e.dataTransfer.effectAllowed = 'move'
                            }}
                            onDragEnd={endDrag}
                            onDragOver={(e) => {
                              if (dropOnColumn(row.id, col.id)) {
                                e.preventDefault()
                                setOver(col.id)
                              }
                            }}
                            onDragLeave={() => setOver((o) => (o === col.id ? null : o))}
                            onDrop={(e) => {
                              const fn = dropOnColumn(row.id, col.id)
                              if (fn) {
                                e.preventDefault()
                                e.stopPropagation()
                                fn()
                              }
                            }}
                            className={nodeRow(colSel, over === col.id)}
                          >
                            <GripVertical className={grip} aria-hidden />
                            {/* coluna não tem twisty — reserva o slot do chevron pra alinhar com a linha */}
                            <span className={chevronSlot} aria-hidden />
                            <button
                              type="button"
                              onClick={() =>
                                p.onSelect({ kind: 'column', rowId: row.id, colId: col.id })
                              }
                              className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
                            >
                              <Columns3
                                className={cn(
                                  'size-3.5 shrink-0',
                                  colSel ? 'text-primary' : 'text-muted-foreground',
                                )}
                                aria-hidden
                              />
                              <span className="truncate">Coluna {ci + 1}</span>
                              {/* width minúsculo, tabular, sem parênteses; "w" só p/ desambiguar do nº da coluna */}
                              <Meta>
                                {typeof col.width === 'number' ? `w${col.width}` : 'auto'}
                              </Meta>
                            </button>
                            <NodeButtons
                              onUp={() => p.onMoveColumn(row.id, col.id, -1)}
                              onDown={() => p.onMoveColumn(row.id, col.id, 1)}
                              onRemove={() => p.onRemoveColumn(row.id, col.id)}
                              upDisabled={ci === 0}
                              downDisabled={ci === row.columns.length - 1}
                              removeLabel="Excluir coluna"
                            />
                          </div>

                          {/* blocos da coluna — segundo nível de rail/indentação */}
                          <div className={rail}>
                            {col.blocks.map((b, bi) => {
                              const s = blockSchema(b.type)
                              const bSel = sel?.kind === 'block' && sel.blockId === b.id
                              const BlockIcon = BLOCK_ICONS[b.type] ?? Box
                              // SLOTS (partes fixas) + REPEATERS (listas: services.items…). Bloco vira
                              // expansível se tem qualquer um; os demais continuam folha. Key blk:id.
                              const slots = slotsForType(b.type)
                              const repeaters = repeaterFieldsOf(s?.fields ?? [])
                              const expandable = slots.length > 0 || repeaters.length > 0
                              const bOpen = expandable && p.expanded.has(blockExpandKey(b.id))
                              return (
                                <div key={b.id}>
                                  <div
                                    draggable
                                    data-selected={bSel}
                                    onDragStart={(e) => {
                                      e.stopPropagation()
                                      setDrag({
                                        kind: 'block',
                                        rowId: row.id,
                                        colId: col.id,
                                        blockId: b.id,
                                      })
                                      e.dataTransfer.effectAllowed = 'move'
                                    }}
                                    onDragEnd={endDrag}
                                    className={nodeRow(bSel, false)}
                                  >
                                    <GripVertical className={grip} aria-hidden />
                                    {expandable ? (
                                      // macro: chevron que expande slots + repeaters (mesmo padrão da linha)
                                      <button
                                        type="button"
                                        onClick={() => p.onToggle(blockExpandKey(b.id))}
                                        aria-label={bOpen ? 'Recolher' : 'Expandir'}
                                        className="grid size-4 shrink-0 place-items-center rounded-sm text-muted-foreground hover:bg-muted"
                                      >
                                        <ChevronRight
                                          className={cn(
                                            'size-3.5 transition-transform',
                                            bOpen && 'rotate-90',
                                          )}
                                          aria-hidden
                                        />
                                      </button>
                                    ) : (
                                      // bloco-folha — slot reservado pra alinhar
                                      <span className={chevronSlot} aria-hidden />
                                    )}
                                    <button
                                      type="button"
                                      onClick={() =>
                                        p.onSelect({
                                          kind: 'block',
                                          rowId: row.id,
                                          colId: col.id,
                                          blockId: b.id,
                                        })
                                      }
                                      className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
                                    >
                                      {/* ícone monocromático por TIPO de bloco (mapa lucide) — substitui o emoji do schema */}
                                      <BlockIcon
                                        className={cn(
                                          'size-3.5 shrink-0',
                                          bSel ? 'text-primary' : 'text-muted-foreground',
                                        )}
                                        aria-hidden
                                      />
                                      <span className="truncate">
                                        {s?.label ?? blockTypeLabel(b.type)}
                                      </span>
                                    </button>
                                    <NodeButtons
                                      onUp={() => p.onMoveBlock(row.id, col.id, b.id, -1)}
                                      onDown={() => p.onMoveBlock(row.id, col.id, b.id, 1)}
                                      onRemove={() => p.onRemoveBlock(row.id, col.id, b.id)}
                                      upDisabled={bi === 0}
                                      downDisabled={bi === col.blocks.length - 1}
                                      removeLabel="Excluir bloco"
                                    />
                                  </div>

                                  {/* SLOTS do bloco (sub-nós folha): seleção + ícone + label; SEM ↑↓✕ nem drag
                                      (partes fixas do macro, sempre presentes no schema). 3º nível de rail. */}
                                  {bOpen && (
                                    <div className={rail}>
                                      {slots.map((slot) => {
                                        const slotSel =
                                          sel?.kind === 'slot' &&
                                          sel.blockId === b.id &&
                                          sel.slotId === slot.id
                                        const SlotIcon = slot.icon
                                        return (
                                          <div
                                            key={slot.id}
                                            data-selected={slotSel}
                                            className={nodeRow(slotSel, false)}
                                          >
                                            <span className={chevronSlot} aria-hidden />
                                            <button
                                              type="button"
                                              onClick={() =>
                                                p.onSelect({
                                                  kind: 'slot',
                                                  rowId: row.id,
                                                  colId: col.id,
                                                  blockId: b.id,
                                                  slotId: slot.id,
                                                })
                                              }
                                              className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
                                            >
                                              <SlotIcon
                                                className={cn(
                                                  'size-3.5 shrink-0',
                                                  slotSel
                                                    ? 'text-primary'
                                                    : 'text-muted-foreground',
                                                )}
                                                aria-hidden
                                              />
                                              <span className="truncate">{slot.label}</span>
                                            </button>
                                          </div>
                                        )
                                      })}

                                      {/* REPEATERS (2ª família): cada lista do schema vira um grupo
                                          expansível → itens com ↑↓✕ + "adicionar". Profundidade 1 extra. */}
                                      {repeaters.map((rep) => {
                                        const items = getItems(
                                          b.props as Record<string, unknown>,
                                          rep.key,
                                        )
                                        const repOpen = p.expanded.has(
                                          repeaterExpandKey(b.id, rep.key),
                                        )
                                        const itemLabel = rep.itemLabel ?? 'item'
                                        return (
                                          <div key={rep.key}>
                                            {/* nó-grupo do repeater */}
                                            <div className={nodeRow(false, false)}>
                                              <button
                                                type="button"
                                                onClick={() =>
                                                  p.onToggle(repeaterExpandKey(b.id, rep.key))
                                                }
                                                aria-label={repOpen ? 'Recolher' : 'Expandir'}
                                                className="grid size-4 shrink-0 place-items-center rounded-sm text-muted-foreground hover:bg-muted"
                                              >
                                                <ChevronRight
                                                  className={cn(
                                                    'size-3.5 transition-transform',
                                                    repOpen && 'rotate-90',
                                                  )}
                                                  aria-hidden
                                                />
                                              </button>
                                              <span className="flex min-w-0 flex-1 items-center gap-1.5">
                                                <List
                                                  className="size-3.5 shrink-0 text-muted-foreground"
                                                  aria-hidden
                                                />
                                                <span className="truncate">{rep.label}</span>
                                                <span className="shrink-0 text-[10px] text-muted-foreground/70 tabular-nums">
                                                  {items.length}
                                                </span>
                                              </span>
                                            </div>
                                            {/* itens do repeater */}
                                            {repOpen && (
                                              <div className={rail}>
                                                {items.map((it, ii) => {
                                                  const itemSel =
                                                    sel?.kind === 'item' &&
                                                    sel.blockId === b.id &&
                                                    sel.fieldKey === rep.key &&
                                                    sel.itemIndex === ii
                                                  return (
                                                    <div
                                                      key={ii}
                                                      data-selected={itemSel}
                                                      className={nodeRow(itemSel, false)}
                                                    >
                                                      <span className={chevronSlot} aria-hidden />
                                                      <button
                                                        type="button"
                                                        onClick={() =>
                                                          p.onSelect({
                                                            kind: 'item',
                                                            rowId: row.id,
                                                            colId: col.id,
                                                            blockId: b.id,
                                                            fieldKey: rep.key,
                                                            itemIndex: ii,
                                                          })
                                                        }
                                                        className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
                                                      >
                                                        <Box
                                                          className={cn(
                                                            'size-3.5 shrink-0',
                                                            itemSel
                                                              ? 'text-primary'
                                                              : 'text-muted-foreground',
                                                          )}
                                                          aria-hidden
                                                        />
                                                        <span className="truncate">
                                                          {itemTitle(
                                                            it,
                                                            rep.itemSchema,
                                                            itemLabel,
                                                            ii,
                                                          )}
                                                        </span>
                                                      </button>
                                                      <NodeButtons
                                                        onUp={() =>
                                                          p.onMoveItem(
                                                            row.id,
                                                            col.id,
                                                            b.id,
                                                            rep.key,
                                                            ii,
                                                            -1,
                                                          )
                                                        }
                                                        onDown={() =>
                                                          p.onMoveItem(
                                                            row.id,
                                                            col.id,
                                                            b.id,
                                                            rep.key,
                                                            ii,
                                                            1,
                                                          )
                                                        }
                                                        onRemove={() =>
                                                          p.onRemoveItem(
                                                            row.id,
                                                            col.id,
                                                            b.id,
                                                            rep.key,
                                                            ii,
                                                          )
                                                        }
                                                        upDisabled={ii === 0}
                                                        downDisabled={ii === items.length - 1}
                                                        removeLabel={`Excluir ${itemLabel}`}
                                                      />
                                                    </div>
                                                  )
                                                })}
                                                <button
                                                  type="button"
                                                  onClick={() =>
                                                    p.onAddItem(row.id, col.id, b.id, rep.key)
                                                  }
                                                  className={addLink}
                                                >
                                                  <span className={chevronSlot} aria-hidden />
                                                  <Plus className="size-3.5 shrink-0" aria-hidden />
                                                  <span className="truncate">{itemLabel}</span>
                                                </button>
                                              </div>
                                            )}
                                          </div>
                                        )
                                      })}
                                    </div>
                                  )}
                                </div>
                              )
                            })}
                            {/* adicionar bloco — link discreto, alinhado com os blocos, sem caixa tracejada */}
                            <button
                              type="button"
                              onClick={() => p.onAddBlock(row.id, col.id)}
                              className={addLink}
                            >
                              <span className={chevronSlot} aria-hidden />
                              <Plus className="size-3.5 shrink-0" aria-hidden />
                              <span className="truncate">Bloco</span>
                            </button>
                          </div>
                        </div>
                      )
                    })}
                    {/* adicionar coluna — link discreto, alinhado com as colunas */}
                    <button type="button" onClick={() => p.onAddColumn(row.id)} className={addLink}>
                      <span className={chevronSlot} aria-hidden />
                      <Plus className="size-3.5 shrink-0" aria-hidden />
                      <span className="truncate">Coluna</span>
                    </button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
