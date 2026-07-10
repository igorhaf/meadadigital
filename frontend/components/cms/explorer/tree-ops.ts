import {
  columnWithBlock,
  defaultProps,
  emptyRow,
  newBlockId,
  type CmsBlock,
  type CmsBlockTypeId,
  type CmsColumn,
  type CmsColumnWidth,
  type CmsRow,
  type CmsRowProps,
} from '@/lib/cms/cms-block-type'

/**
 * Operações PURAS e IMUTÁVEIS sobre a árvore do page builder (`CmsRow[]`). Cada função recebe a
 * árvore e devolve uma nova — sem mutação, sem React. Testáveis isoladamente; o editor só envolve
 * com `setTree`.
 */

function mapRow(tree: CmsRow[], rowId: string, fn: (r: CmsRow) => CmsRow): CmsRow[] {
  return tree.map((r) => (r.id === rowId ? fn(r) : r))
}
function mapColumn(
  tree: CmsRow[],
  rowId: string,
  colId: string,
  fn: (c: CmsColumn) => CmsColumn,
): CmsRow[] {
  return mapRow(tree, rowId, (r) => ({
    ...r,
    columns: r.columns.map((c) => (c.id === colId ? fn(c) : c)),
  }))
}
function moveInArray<T>(arr: T[], index: number, dir: -1 | 1): T[] {
  const target = index + dir
  if (index < 0 || target < 0 || target >= arr.length) return arr
  const next = [...arr]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

/** Move o item `dragId` para ANTES de `targetId` (semântica de drop / drag-drop). Mesmo id → no-op. */
function reorderInArray<T extends { id: string }>(arr: T[], dragId: string, targetId: string): T[] {
  if (dragId === targetId) return arr
  const from = arr.findIndex((x) => x.id === dragId)
  const to = arr.findIndex((x) => x.id === targetId)
  if (from < 0 || to < 0) return arr
  const next = [...arr]
  next.splice(from, 1)
  const insertAt = next.findIndex((x) => x.id === targetId) // recalcula após remover o arrastado
  next.splice(insertAt, 0, arr[from])
  return next
}

// ---- linhas ----
export function addRow(tree: CmsRow[]): CmsRow[] {
  return [...tree, emptyRow()]
}
export function removeRow(tree: CmsRow[], rowId: string): CmsRow[] {
  return tree.filter((r) => r.id !== rowId)
}
export function moveRow(tree: CmsRow[], rowId: string, dir: -1 | 1): CmsRow[] {
  const i = tree.findIndex((r) => r.id === rowId)
  return moveInArray(tree, i, dir)
}
/** Drag-drop: move a linha `dragId` pra antes de `targetId`. */
export function reorderRow(tree: CmsRow[], dragId: string, targetId: string): CmsRow[] {
  return reorderInArray(tree, dragId, targetId)
}
export function updateRowProps(tree: CmsRow[], rowId: string, props: CmsRowProps): CmsRow[] {
  return mapRow(tree, rowId, (r) => ({ ...r, props }))
}

// ---- colunas ----
/** Cria uma coluna nova (com um bloco do tipo dado) dentro da linha. width default = divide igual. */
export function addColumn(
  tree: CmsRow[],
  rowId: string,
  type: CmsBlockTypeId,
  width?: CmsColumnWidth,
): CmsRow[] {
  return mapRow(tree, rowId, (r) => {
    const count = r.columns.length + 1
    const w = width ?? (Math.max(1, Math.floor(12 / count)) as CmsColumnWidth)
    return { ...r, columns: [...r.columns, columnWithBlock(type, w)] }
  })
}
export function removeColumn(tree: CmsRow[], rowId: string, colId: string): CmsRow[] {
  return mapRow(tree, rowId, (r) => ({ ...r, columns: r.columns.filter((c) => c.id !== colId) }))
}
export function moveColumn(tree: CmsRow[], rowId: string, colId: string, dir: -1 | 1): CmsRow[] {
  return mapRow(tree, rowId, (r) => {
    const i = r.columns.findIndex((c) => c.id === colId)
    return { ...r, columns: moveInArray(r.columns, i, dir) }
  })
}
export function setColumnWidth(
  tree: CmsRow[],
  rowId: string,
  colId: string,
  width: CmsColumnWidth,
): CmsRow[] {
  return mapColumn(tree, rowId, colId, (c) => ({ ...c, width }))
}
/** Drag-drop: move a coluna `dragColId` pra antes de `targetColId` (mesma linha). */
export function reorderColumn(
  tree: CmsRow[],
  rowId: string,
  dragColId: string,
  targetColId: string,
): CmsRow[] {
  return mapRow(tree, rowId, (r) => ({
    ...r,
    columns: reorderInArray(r.columns, dragColId, targetColId),
  }))
}

// ---- blocos (folhas) ----
export function addBlockToColumn(
  tree: CmsRow[],
  rowId: string,
  colId: string,
  type: CmsBlockTypeId,
): CmsRow[] {
  const block = { id: newBlockId(), type, props: defaultProps(type) } as CmsBlock
  return mapColumn(tree, rowId, colId, (c) => ({ ...c, blocks: [...c.blocks, block] }))
}
export function removeBlock(
  tree: CmsRow[],
  rowId: string,
  colId: string,
  blockId: string,
): CmsRow[] {
  return mapColumn(tree, rowId, colId, (c) => ({
    ...c,
    blocks: c.blocks.filter((b) => b.id !== blockId),
  }))
}
export function moveBlockWithin(
  tree: CmsRow[],
  rowId: string,
  colId: string,
  blockId: string,
  dir: -1 | 1,
): CmsRow[] {
  return mapColumn(tree, rowId, colId, (c) => {
    const i = c.blocks.findIndex((b) => b.id === blockId)
    return { ...c, blocks: moveInArray(c.blocks, i, dir) }
  })
}
export function updateBlockProps(
  tree: CmsRow[],
  rowId: string,
  colId: string,
  blockId: string,
  props: CmsBlock['props'],
): CmsRow[] {
  return mapColumn(tree, rowId, colId, (c) => ({
    ...c,
    blocks: c.blocks.map((b) => (b.id === blockId ? ({ ...b, props } as CmsBlock) : b)),
  }))
}

/** Move um bloco de uma coluna pra outra (drag-drop entre colunas). Anexa no fim do destino. */
export function moveBlockAcross(
  tree: CmsRow[],
  from: { rowId: string; colId: string; blockId: string },
  to: { rowId: string; colId: string },
): CmsRow[] {
  let moved: CmsBlock | null = null
  const removed = mapColumn(tree, from.rowId, from.colId, (c) => {
    moved = c.blocks.find((b) => b.id === from.blockId) ?? null
    return { ...c, blocks: c.blocks.filter((b) => b.id !== from.blockId) }
  })
  if (!moved) return tree
  return mapColumn(removed, to.rowId, to.colId, (c) => ({
    ...c,
    blocks: [...c.blocks, moved as CmsBlock],
  }))
}
