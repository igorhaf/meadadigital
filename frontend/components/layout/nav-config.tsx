import {
  Activity,
  AlertTriangle,
  Armchair,
  BarChart3,
  BedDouble,
  BookOpen,
  Briefcase,
  Building2,
  Calendar,
  CalendarCheck,
  CalendarClock,
  ClipboardList,
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
  Scale,
  Scissors,
  ScrollText,
  Settings,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  Tag,
  Wand2,
  UtensilsCrossed,
  UserCog,
  Users,
  UserPlus,
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
 * (NAV_GROUPS) — esta SM é só fundação; as features específicas de cada perfil (e seus itens
 * de menu próprios) entram em SM-B/C/D, que estenderão esta função com branches por profileId.
 *
 * <p>O seam existe agora para que a mudança futura seja localizada: a tela consome
 * getNavForProfile em vez de NAV_GROUPS diretamente, e quando um perfil ganhar itens próprios,
 * só esta função muda. O título do produto NÃO vem daqui — vem do GET /admin/me (productName),
 * renderizado pelo SidebarBrand.
 */
export function getNavForProfile(profileId: string | null | undefined): NavGroup[] {
  // Perfil vertical (camada 7.1/7.2): grupo próprio no topo. Demais perfis seguem o nav padrão.
  if (profileId === 'sushi') {
    return [SUSHI_GROUP, ...NAV_GROUPS]
  }
  if (profileId === 'legal') {
    return [LEGAL_GROUP, ...NAV_GROUPS]
  }
  if (profileId === 'restaurant') {
    return [RESTAURANT_GROUP, ...NAV_GROUPS]
  }
  if (profileId === 'dental') {
    return [DENTAL_GROUP, ...NAV_GROUPS]
  }
  if (profileId === 'salon') {
    return [SALON_GROUP, ...NAV_GROUPS]
  }
  if (profileId === 'pousada') {
    return [POUSADA_GROUP, ...NAV_GROUPS]
  }
  return NAV_GROUPS
}

/** Grupo de navegação exclusivo do perfil sushi (camada 7.1). */
const SUSHI_GROUP: NavGroup = {
  heading: 'Restaurante',
  items: [
    { label: 'Cardápio', href: '/dashboard/menu', icon: UtensilsCrossed },
    { label: 'Pedidos', href: '/dashboard/orders', icon: ClipboardList },
  ],
}

/** Grupo de navegação exclusivo do perfil legal (camada 7.2). */
const LEGAL_GROUP: NavGroup = {
  heading: 'Escritório',
  items: [
    { label: 'Clientes', href: '/dashboard/clients', icon: Briefcase },
    { label: 'Processos', href: '/dashboard/cases', icon: Scale },
  ],
}

/** Grupo de navegação exclusivo do perfil restaurant (camada 7.3). "Reservas" (não "Restaurante",
 * pra não colidir com o grupo do sushi). */
const RESTAURANT_GROUP: NavGroup = {
  heading: 'Reservas',
  items: [
    { label: 'Mesas', href: '/dashboard/tables', icon: Armchair },
    { label: 'Reservas', href: '/dashboard/reservations', icon: CalendarClock },
    { label: 'Configurações', href: '/dashboard/restaurant-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil dental (camada 7.4). */
const DENTAL_GROUP: NavGroup = {
  heading: 'Consultório',
  items: [
    { label: 'Pacientes', href: '/dashboard/patients', icon: Stethoscope },
    { label: 'Agenda', href: '/dashboard/appointments', icon: CalendarClock },
    { label: 'Configurações', href: '/dashboard/dental-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil salon (camada 7.5). */
const SALON_GROUP: NavGroup = {
  heading: 'Salão',
  items: [
    { label: 'Profissionais', href: '/dashboard/professionals', icon: Scissors },
    { label: 'Serviços', href: '/dashboard/salon-services', icon: Wand2 },
    { label: 'Agenda', href: '/dashboard/salon-appointments', icon: CalendarClock },
    { label: 'Configurações', href: '/dashboard/salon-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil pousada (camada 7.6). */
const POUSADA_GROUP: NavGroup = {
  heading: 'Pousada',
  items: [
    { label: 'Quartos', href: '/dashboard/rooms', icon: BedDouble },
    { label: 'Reservas', href: '/dashboard/pousada-reservations', icon: CalendarClock },
    { label: 'Configurações', href: '/dashboard/pousada-settings', icon: Settings },
  ],
}
