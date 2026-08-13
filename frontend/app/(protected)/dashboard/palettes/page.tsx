'use client'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { DEFAULT_PALETTE_ID, PALETTES, type Palette } from '@/lib/themes/palettes'

/** Um swatch de cor com rótulo embaixo. */
function Swatch({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex flex-col items-center gap-1">
      <span
        className="size-8 rounded-md border border-border"
        style={{ backgroundColor: color }}
        title={color}
      />
      <span className="text-[10px] text-muted-foreground">{label}</span>
    </div>
  )
}

/** Card de uma paleta: nome (+ tag default) e os 5 swatches do contrato. */
function PaletteCard({ palette }: { palette: Palette }) {
  return (
    <Card>
      <div className="mb-3 flex items-center justify-between">
        <span className="text-sm font-medium">{palette.name}</span>
        {palette.id === DEFAULT_PALETTE_ID && <Badge variant="info">padrão</Badge>}
      </div>
      <div className="flex flex-wrap gap-3">
        <Swatch color={palette.primary} label="primary" />
        <Swatch color={palette.primaryHover} label="hover" />
        <Swatch color={palette.accent} label="accent" />
        <Swatch color={palette.surface} label="surface" />
        <Swatch color={palette.textOnPrimary} label="texto" />
      </div>
      <p className="mt-3 font-mono text-[10px] text-muted-foreground">{palette.id}</p>
    </Card>
  )
}

/**
 * Catálogo de paletas (camada 6.8, super-admin). LEITURA pura: renderiza o catálogo
 * hardcoded de lib/themes/palettes.ts (camada 5.0). Sem CRUD — adicionar paleta é editar
 * o arquivo e abrir PR. Sem SDK backend, sem tabela no banco.
 */
export default function PalettesPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Paletas"
        description={`Catálogo de temas disponíveis (${PALETTES.length}). Leitura — sem edição pela interface.`}
      />

      <Card className="bg-muted/40">
        <p className="text-sm text-muted-foreground">
          Para adicionar uma paleta nova, edite{' '}
          <code className="font-mono text-xs">frontend/lib/themes/palettes.ts</code> e abra um PR.
          Uma versão em tabela (com edição) virá em fase futura.
        </p>
      </Card>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {PALETTES.map((p) => (
          <PaletteCard key={p.id} palette={p} />
        ))}
      </div>
    </div>
  )
}
