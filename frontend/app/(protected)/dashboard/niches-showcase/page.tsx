'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, ChevronDown } from 'lucide-react'
import Link from 'next/link'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api/client'
import {
  getNicheShowcase,
  setNicheShowcase,
  type ShowcaseGrid,
  type ShowcaseRow,
} from '@/lib/api/admin/niche-showcase'
import { formatMonthlyPrice, getCatalogEntry } from '@/lib/profiles/profile-catalog'
import { PALETTES } from '@/lib/themes/palettes'

/**
 * Vitrine de nichos (super-admin). O root marca quais nichos são DESTAQUE (aparecem na home, até
 * maxFeatured) e ordena a lista (↑/↓) — a ordem vale no grid e na página /produtos. Persiste por
 * PUT /admin/niches/showcase/{profileId}. Autorização: backend (403 tratado inline).
 */
function colorFor(paletteId: string): string {
  return PALETTES.find((p) => p.id === paletteId)?.primary ?? '#3b82f6'
}

export default function NichesShowcasePage() {
  const qc = useQueryClient()
  const [err, setErr] = useState<string | null>(null)
  const [expanded, setExpanded] = useState<string | null>(null)

  const { data, isPending, isError, error } = useQuery<ShowcaseGrid>({
    queryKey: ['niche-showcase'],
    queryFn: getNicheShowcase,
  })

  const setMut = useMutation({
    mutationFn: ({ profileId, featured, displayOrder }: ShowcaseRow) =>
      setNicheShowcase(profileId, featured, displayOrder),
    onError: (e) => {
      setErr(
        e instanceof ApiError && e.reason === 'too_many_featured'
          ? `Máximo de ${data?.maxFeatured ?? 6} nichos em destaque. Desmarque um antes.`
          : 'Erro ao salvar. Tente novamente.',
      )
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['niche-showcase'] }),
  })

  // Reordenar = troca display_order dos dois vizinhos e persiste ambos.
  async function move(rows: ShowcaseRow[], index: number, dir: -1 | 1) {
    const target = index + dir
    if (target < 0 || target >= rows.length) return
    setErr(null)
    const a = { ...rows[index], displayOrder: target }
    const b = { ...rows[target], displayOrder: index }
    await setNicheShowcase(a.profileId, a.featured, a.displayOrder)
    await setNicheShowcase(b.profileId, b.featured, b.displayOrder)
    qc.invalidateQueries({ queryKey: ['niche-showcase'] })
  }

  function toggleFeatured(row: ShowcaseRow) {
    setErr(null)
    setMut.mutate({ ...row, featured: !row.featured })
  }

  if (isError && error instanceof ApiError && error.status === 403) {
    return (
      <div className="space-y-6">
        <PageHeader title="Acesso restrito" description="Esta área é restrita ao super-admin." />
        <Link href="/dashboard"><Button variant="outline">Voltar ao dashboard</Button></Link>
      </div>
    )
  }
  if (isPending) {
    return <div className="p-6 text-sm text-muted-foreground">Carregando…</div>
  }
  if (isError) {
    return <div className="p-6 text-sm text-destructive">Erro ao carregar a vitrine.</div>
  }

  const rows = data.niches
  const featuredCount = rows.filter((r) => r.featured).length

  return (
    <div className="space-y-6">
      <PageHeader
        title="Vitrine de Nichos"
        description={`Cada nicho é um produto com preço e descrição própria. Marque até ${data.maxFeatured} como destaque (aparecem na home) e ordene a lista (vale na página de Produtos). ${featuredCount}/${data.maxFeatured} em destaque.`}
      />
      {err && <p className="text-sm text-destructive">{err}</p>}

      <div className="divide-y divide-border rounded-lg border border-border">
        {rows.map((row, i) => {
          const catalog = getCatalogEntry(row.profileId)
          const isOpen = expanded === row.profileId
          return (
            <div key={row.profileId} className="px-4 py-3">
              <div className="flex items-center gap-3">
                <span className="w-6 text-right text-xs tabular-nums text-muted-foreground">{i + 1}</span>
                <span className="size-4 shrink-0 rounded" style={{ background: colorFor(row.paletteId) }} />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-baseline gap-x-2">
                    <span className="font-medium">{row.productName}</span>
                    <span className="text-xs text-muted-foreground">{row.subdomain}.meadadigital.com</span>
                  </div>
                  {catalog?.tagline && (
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">{catalog.tagline}</p>
                  )}
                </div>
                {catalog && (
                  <span className="shrink-0 text-sm font-semibold tabular-nums" style={{ color: colorFor(row.paletteId) }}>
                    {formatMonthlyPrice(catalog.priceMonthly)}
                  </span>
                )}
                {row.featured && <Badge>destaque</Badge>}
                <label className="flex cursor-pointer items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={row.featured}
                    disabled={setMut.isPending}
                    onChange={() => toggleFeatured(row)}
                  />
                  destaque
                </label>
                {catalog?.highlights?.length ? (
                  <Button
                    variant="outline"
                    className="h-7 w-7 p-0"
                    onClick={() => setExpanded(isOpen ? null : row.profileId)}
                    aria-label={isOpen ? 'Recolher detalhes' : 'Ver o que o produto faz'}
                  >
                    <ChevronDown className={`size-3.5 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
                  </Button>
                ) : null}
                <div className="flex shrink-0 gap-1">
                  <Button variant="outline" className="h-7 w-7 p-0" disabled={i === 0} onClick={() => move(rows, i, -1)} aria-label="Subir">
                    <ArrowUp className="size-3.5" />
                  </Button>
                  <Button variant="outline" className="h-7 w-7 p-0" disabled={i === rows.length - 1} onClick={() => move(rows, i, 1)} aria-label="Descer">
                    <ArrowDown className="size-3.5" />
                  </Button>
                </div>
              </div>
              {isOpen && catalog?.highlights?.length ? (
                <ul className="ml-9 mt-2 list-disc space-y-1 pl-4 text-xs text-muted-foreground">
                  {catalog.highlights.map((h, hi) => (
                    <li key={hi}>{h}</li>
                  ))}
                </ul>
              ) : null}
            </div>
          )
        })}
      </div>
    </div>
  )
}
