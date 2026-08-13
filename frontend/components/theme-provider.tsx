'use client'

import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'

import { getMe } from '@/lib/api/me'
import { getMyCompany } from '@/lib/supabase/companies'
import { DEFAULT_PALETTE_ID, getPalette } from '@/lib/themes/palettes'

/**
 * Injeta as CSS vars do tema no :root conforme a paleta EFETIVA do usuário logado
 * (camada 5.1.a — dual-fetch; camada 5.1.b — sobrescrita de tokens shadcn de marca).
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
 * Aplicação (5.1.b — Caminho 2): além das --palette-* (consumidas por componentes que
 * usam arbitrary values, ex.: PaletteSelect), o provider SOBRESCREVE os tokens shadcn de
 * MARCA no :root, apontando-os para as --palette-*. Isso faz todo bg-primary /
 * text-primary-foreground / ring-ring (botões primários, bolhas outbound, foco visível)
 * herdar a paleta SEM tocar nenhuma das 9 telas — um ponto de controle único.
 *   - --primary            → --palette-primary
 *   - --primary-foreground → --palette-text-on-primary
 *   - --ring               → --palette-primary
 * NÃO sobrescreve --accent/--destructive/--secondary/--muted/--border nem demais neutros:
 * accent é hover de superfície neutra, destructive é vermelho semântico de erro, e os
 * outros são estruturais — colori-los pintaria lugares que não são identidade de marca.
 * Badges de status (success/danger/warning) seguem shadcn: semântica universal (um tenant
 * com paleta vermelha não quer badge "ativo" vermelho, que confundiria com erro).
 *
 * Cleanup: ao desmontar, remove TODAS as vars do :root (inclui os tokens shadcn
 * sobrescritos, que voltam ao valor declarado em globals.css — o cinza padrão). Itera
 * Object.keys(vars), então as 3 novas entradas são removidas sem código extra. Evita
 * vazar tema entre sessões/contas num mesmo documento.
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
      // CSS vars próprias da paleta (consumidas por arbitrary values, ex.: PaletteSelect).
      '--palette-primary': palette.primary,
      '--palette-primary-hover': palette.primaryHover,
      '--palette-accent': palette.accent,
      '--palette-surface': palette.surface,
      '--palette-text-on-primary': palette.textOnPrimary,
      // Tokens shadcn de MARCA sobrescritos (5.1.b): botões primários, bolhas outbound,
      // foco visível herdam a paleta sem tocar nenhuma tela. Neutros e semânticos ficam.
      '--primary': 'var(--palette-primary)',
      '--primary-foreground': 'var(--palette-text-on-primary)',
      '--ring': 'var(--palette-primary)',
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
