import type { CmsBlock, CmsRow } from './cms-block-type'

/**
 * Adaptador de RETROCOMPAT do page builder: a página passou de uma lista FLAT de blocos
 * (`CmsBlock[]`) para uma ÁRVORE (`CmsRow[]` = linhas → colunas → blocos). Dados antigos têm o
 * formato flat; este módulo converte transparentemente na LEITURA (idempotente), sem reescrever o
 * banco — cada save reescreve a própria página no formato novo.
 *
 * Discriminação por SHAPE: um item flat tem `.type` (string) e NÃO tem `.columns`; um item da árvore
 * tem `.columns` (array). Espelhado no backend (CmsService.normalizeBlocks) como defesa.
 */

/** É o formato FLAT legado? (array não-vazio cujo 1º item tem `type` e não tem `columns`). */
export function isFlatBlocks(data: unknown): data is CmsBlock[] {
  if (!Array.isArray(data) || data.length === 0) return false
  const first = data[0] as Record<string, unknown> | null
  return typeof first?.type === 'string' && !Array.isArray(first?.columns)
}

/**
 * flat → árvore: cada bloco vira 1 linha de 1 coluna span-12, PASSTHROUGH (sem padding/container
 * próprio) — os blocos legados já trazem seu `<section>` interno, então a linha de migração é
 * transparente e o render fica idêntico ao formato antigo. ids de row/col derivados do id do bloco
 * (`r-{id}`/`c-{id}`) → re-render estável, sem remount no editor.
 */
export function flatToTree(blocks: CmsBlock[]): CmsRow[] {
  return blocks.map((b) => ({
    id: `r-${b.id}`,
    props: {
      bg: 'none' as const,
      paddingY: 'none' as const,
      gap: 'md' as const,
      align: 'stretch' as const,
      maxWidth: 'full' as const,
    },
    columns: [{ id: `c-${b.id}`, width: 12 as const, blocks: [b] }],
  }))
}

/** Ponto único: aceita o que vier do backend (flat OU árvore) e SEMPRE devolve árvore. Idempotente. */
export function normalizeToTree(data: unknown): CmsRow[] {
  if (isFlatBlocks(data)) return flatToTree(data)
  if (Array.isArray(data)) return data as CmsRow[]
  return []
}
