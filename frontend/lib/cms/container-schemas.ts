import type { FieldDef } from './cms-block-schemas'

/**
 * Schemas dos CONTAINERS do page builder (linha e coluna). Ficam SEPARADOS de cms-block-schemas
 * (que é Record<CmsBlockTypeId,…>) porque row/column NÃO são block types — não entram em
 * CMS_BLOCK_TYPES nem no enum Java, e não devem disparar o CmsBlockTypeParityTest. Reusam o mesmo
 * FieldDef/FieldRenderer dos blocos.
 */

export const rowSchema: { label: string; emoji: string; fields: FieldDef[] } = {
  label: 'Linha (seção)',
  emoji: '🟰',
  fields: [
    {
      key: 'bg',
      label: 'Fundo',
      type: 'select',
      options: [
        { value: 'none', label: 'Transparente' },
        { value: 'muted', label: 'Suave' },
        { value: 'primary', label: 'Cor primária' },
      ],
    },
    {
      key: 'paddingY',
      label: 'Espaçamento vertical',
      type: 'select',
      options: [
        { value: 'none', label: 'Nenhum' },
        { value: 'sm', label: 'Pequeno' },
        { value: 'md', label: 'Médio' },
        { value: 'lg', label: 'Grande' },
      ],
    },
    {
      key: 'gap',
      label: 'Espaço entre colunas',
      type: 'select',
      options: [
        { value: 'sm', label: 'Pequeno' },
        { value: 'md', label: 'Médio' },
        { value: 'lg', label: 'Grande' },
      ],
    },
    {
      key: 'maxWidth',
      label: 'Largura do conteúdo',
      type: 'select',
      options: [
        { value: 'narrow', label: 'Estreito' },
        { value: 'wide', label: 'Largo' },
        { value: 'full', label: 'Tela cheia' },
      ],
    },
    {
      key: 'align',
      label: 'Alinhamento vertical',
      type: 'select',
      options: [
        { value: 'start', label: 'Topo' },
        { value: 'center', label: 'Centro' },
        { value: 'stretch', label: 'Esticar' },
      ],
    },
  ],
}

/** Largura da coluna no grid de 12 (1..12). O editor converte o value do select pra number. */
export const columnWidthOptions = Array.from({ length: 12 }, (_, i) => {
  const n = i + 1
  const frac =
    n === 12 ? 'tela cheia' : n === 6 ? 'metade' : n === 4 ? '1/3' : n === 3 ? '1/4' : `${n}/12`
  return { value: String(n), label: `${n} de 12 (${frac})` }
})

export const columnSchema: { label: string; emoji: string; fields: FieldDef[] } = {
  label: 'Coluna',
  emoji: '▏',
  fields: [{ key: 'width', label: 'Largura', type: 'select', options: columnWidthOptions }],
}
