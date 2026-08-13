import {
  Activity,
  AlertTriangle,
  BarChart3,
  BookOpen,
  Building2,
  Calendar,
  CalendarCheck,
  Clock,
  HelpCircle,
  Home,
  LayoutDashboard,
  Mail,
  Megaphone,
  MessageSquare,
  MessageSquareText,
  Package,
  Palette,
  ScrollText,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Tag,
  UserCog,
  UserPlus,
  Users,
  UsersRound,
  Workflow,
} from 'lucide-react'
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
    items: [
      { label: 'Início', href: '/dashboard', icon: Home },
      { label: 'Conversas', href: '/dashboard/conversations', icon: MessageSquare },
      { label: 'Contatos', href: '/dashboard/contacts', icon: Users },
      { label: 'Agenda', href: '/dashboard/calendar', icon: Calendar },
    ],
  },
  {
    heading: 'Configuração IA',
    items: [
      { label: 'WhatsApp', href: '/dashboard/whatsapp', icon: Smartphone },
      { label: 'IA', href: '/dashboard/ai-settings', icon: Sparkles },
      { label: 'FAQs', href: '/dashboard/faqs', icon: HelpCircle },
      { label: 'Serviços', href: '/dashboard/services', icon: Package },
      { label: 'Conhecimento', href: '/dashboard/knowledge', icon: BookOpen },
      { label: 'Horários', href: '/dashboard/business-hours', icon: Clock },
      { label: 'Disponibilidade', href: '/dashboard/availability', icon: CalendarCheck },
      { label: 'Tags', href: '/dashboard/tags', icon: Tag },
      { label: 'Saved Replies', href: '/dashboard/saved-replies', icon: MessageSquareText },
    ],
  },
  {
    heading: 'Empresa',
    items: [
      { label: 'Equipe', href: '/dashboard/team', icon: UserPlus },
      { label: 'Times', href: '/dashboard/teams', icon: UsersRound },
      { label: 'Métricas', href: '/dashboard/metrics', icon: BarChart3 },
      { label: 'Auditoria', href: '/dashboard/audit', icon: ScrollText },
      { label: 'Segurança', href: '/dashboard/security', icon: ShieldCheck },
    ],
  },
  // ---- Grupos do super-admin (camada 6.0). Todos superAdminOnly. As rotas /metrics,
  //      /audit, /security já existem (tenant) e ganharam render condicional por papel;
  //      as demais são placeholders criados na 6.0. ----
  {
    heading: 'Visão geral',
    superAdminOnly: true,
    items: [{ label: 'Início', href: '/dashboard', icon: LayoutDashboard }],
  },
  {
    heading: 'Empresas',
    superAdminOnly: true,
    items: [{ label: 'Empresas', href: '/dashboard/companies', icon: Building2 }],
  },
  {
    heading: 'Usuários',
    superAdminOnly: true,
    items: [
      { label: 'Usuários', href: '/dashboard/users', icon: Users },
      { label: 'Convites', href: '/dashboard/invitations', icon: Mail },
    ],
  },
  {
    heading: 'Operação',
    superAdminOnly: true,
    items: [
      { label: 'Saúde', href: '/dashboard/health', icon: Activity },
      { label: 'Jobs', href: '/dashboard/jobs', icon: Workflow },
      { label: 'Erros', href: '/dashboard/errors', icon: AlertTriangle },
    ],
  },
  {
    heading: 'Compliance',
    superAdminOnly: true,
    items: [
      { label: 'Auditoria', href: '/dashboard/audit', icon: ScrollText },
      { label: 'Segurança', href: '/dashboard/security', icon: ShieldCheck },
      { label: 'Ações Admin', href: '/dashboard/admin-actions', icon: UserCog },
    ],
  },
  {
    heading: 'Plataforma',
    superAdminOnly: true,
    items: [
      { label: 'Métricas', href: '/dashboard/metrics', icon: BarChart3 },
      { label: 'Anúncios', href: '/dashboard/announcements', icon: Megaphone },
      { label: 'Planos', href: '/dashboard/plans', icon: Package },
      { label: 'Paletas', href: '/dashboard/palettes', icon: Palette },
    ],
  },
]

/**
 * Navegação por perfil (camada 7.0). Por enquanto TODOS os perfis usam os mesmos grupos
 * (NAV_GROUPS) — esta função é o seam: quando um perfil vertical ganhar itens próprios,
 * só ela muda (a tela consome getNavForProfile em vez de NAV_GROUPS diretamente). O título
 * do produto NÃO vem daqui — vem do GET /admin/me (productName), no SidebarBrand.
 *
 * {@code features}: mapa de feature flags resolvidas do nicho ({key → enabled}) — plumbing
 * para gatear itens de nav por flag quando a camada de features nascer.
 */
export function getNavForProfile(
  profileId: string | null | undefined,
  features?: Record<string, boolean>,
): NavGroup[] {
  void features
  return profileGroups(profileId)
}

/** Grupos base por perfil (sem o gate de feature flags). */
function profileGroups(profileId: string | null | undefined): NavGroup[] {
  void profileId
  return NAV_GROUPS
}
