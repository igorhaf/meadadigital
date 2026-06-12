'use client'

import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'

import { getMe } from '@/lib/api/me'
import { DEFAULT_PALETTE_ID, getPalette } from '@/lib/themes/palettes'

/**
 * Injeta as CSS vars do tema no :root conforme a paleta do usuário logado (camada 5.0).
 *
 * Fonte do paletteId (decisão Opção A): GET /admin/me já devolve paletteId resolvido por
 * papel — "meada-default" para super-admin (constante), users.palette_id para
 * tenant-admin. Uma fonte única, sem segundo fetch.
 *
 * TODO(5.1): quando a tela de settings existir e companies.palette_id virar editável, o
 * tenant-admin deve passar a refletir a paleta da EMPRESA (getMyCompany.paletteId), não a
 * do próprio user. Hoje (5.0, sem tela) me.paletteId é suficiente e correto para a
 * fundação — o tenant do seed tem ambos em 'meada-default'.
 *
 * Anti-flash: enquanto a query não resolve, aplica meada-default (mesmo default do
 * banco), então nunca há ":root sem tema". Ao desmontar, remove as vars (cleanup) para
 * não vazar tema entre sessões/contas num mesmo documento.
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })

  const paletteId = me?.paletteId ?? DEFAULT_PALETTE_ID

  useEffect(() => {
    const palette = getPalette(paletteId)
    const root = document.documentElement
    const vars: Record<string, string> = {
      '--palette-primary': palette.primary,
      '--palette-primary-hover': palette.primaryHover,
      '--palette-accent': palette.accent,
      '--palette-surface': palette.surface,
      '--palette-text-on-primary': palette.textOnPrimary,
    }
    for (const [k, v] of Object.entries(vars)) {
      root.style.setProperty(k, v)
    }
    return () => {
      for (const k of Object.keys(vars)) {
        root.style.removeProperty(k)
      }
    }
  }, [paletteId])

  return <>{children}</>
}
