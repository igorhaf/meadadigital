import { BookOpen, Clock, HelpCircle, Home, Package, Sparkles } from 'lucide-react'
import type { ComponentType } from 'react'

/** Item de navegação do sidebar. */
export type NavItem = {
  label: string
  href: string
  icon: ComponentType<{ className?: string }>
}

/** Grupo de navegação (seção do sidebar). `superAdminOnly` esconde para tenant. */
export type NavGroup = {
  heading: string
  items: NavItem[]
  superAdminOnly?: boolean
}

/**
 * Configuração de navegação do painel (camada UI) — fonte única consumida pelo sidebar
 * desktop e pelo drawer mobile. Grupos cravados pelo arquiteto. O grupo ADMIN só aparece
 * para super_admin; os demais, só para tenant_admin (o sidebar filtra por papel).
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    heading: 'Atendimento',
    items: [{ label: 'Início', href: '/dashboard', icon: Home }],
  },
  {
    heading: 'Configuração IA',
    items: [
      { label: 'IA', href: '/dashboard/ai-settings', icon: Sparkles },
      { label: 'FAQs', href: '/dashboard/faqs', icon: HelpCircle },
      { label: 'Serviços', href: '/dashboard/services', icon: Package },
      { label: 'Conhecimento', href: '/dashboard/knowledge', icon: BookOpen },
      { label: 'Horários', href: '/dashboard/business-hours', icon: Clock },
    ],
  },
]

/**
 * Navegação por perfil. Por enquanto TODOS os tenants usam os mesmos grupos (NAV_GROUPS)
 * — o seam existe agora para que a mudança futura seja localizada: a tela consome
 * getNavForProfile em vez de NAV_GROUPS diretamente; quando um perfil vertical ganhar
 * itens próprios, só esta função muda. O título do produto NÃO vem daqui — vem do
 * GET /admin/me (productName), renderizado pelo SidebarBrand.
 *
 * `features`: mapa de flags resolvidas por nicho (plumbing — nenhum consumidor ainda).
 */
export function getNavForProfile(
  profileId: string | null | undefined,
  features?: Record<string, boolean>,
): NavGroup[] {
  void profileId
  void features
  return NAV_GROUPS
}
