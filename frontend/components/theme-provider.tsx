'use client'

import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'

import { getMe } from '@/lib/api/me'
import { getMyCompany } from '@/lib/supabase/companies'
import { DEFAULT_PALETTE_ID, getPalette } from '@/lib/themes/palettes'

/**
 * Injeta as CSS vars do tema no :root conforme a paleta EFETIVA do usuário logado
 * (camada 5.1.a — dual-fetch).
 *
 * Fonte do paletteId por papel:
 *   - super-admin: me.paletteId (constante 'meada-default' — Opção A, super-admin não
 *     toca banco; ele não tem empresa). getMyCompany NÃO é chamado.
 *   - tenant-admin: a paleta da EMPRESA (getMyCompany.paletteId), não a do user. É a
 *     migração cravada como TODO(5.1) na 5.0: o tema do tenant é o tema da empresa dele.
 *
 * useQuery condicional (enabled): getMyCompany só dispara para tenant-admin. Sem isso, o
 * super-admin chamaria getMyCompany → .single() sob RLS volta vazio (super-admin não tem
 * linha em public.users → app.company_id() = NULL) → PostgrestError → query em erro
 * permanente. O enabled mantém a query desabilitada para super-admin (nunca roda).
 *
 * Paleta efetiva por estado (anti-flash em todos):
 *   - me ainda carregando                    → meada-default
 *   - super_admin                            → me.paletteId (meada-default constante)
 *   - tenant_admin, company ainda carregando → meada-default (loading state)
 *   - tenant_admin, company resolvida        → company.paletteId
 *
 * Cleanup: ao desmontar, remove as vars do :root para não vazar tema entre sessões/contas
 * num mesmo documento.
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })

  const isTenant = me?.role === 'tenant_admin'
  const { data: company } = useQuery({
    queryKey: ['my-company'],
    queryFn: getMyCompany,
    enabled: isTenant,
  })

  // Resolve a paleta efetiva conforme o papel e o estado de carregamento.
  let paletteId = DEFAULT_PALETTE_ID
  if (me) {
    if (isTenant) {
      // tenant: paleta da empresa quando resolver; meada-default enquanto carrega
      paletteId = company?.paletteId ?? DEFAULT_PALETTE_ID
    } else {
      // super-admin: paleta do me (constante meada-default — Opção A)
      paletteId = me.paletteId
    }
  }

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
