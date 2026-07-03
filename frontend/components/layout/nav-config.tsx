import {
  Activity,
  AlertTriangle,
  Armchair,
  BarChart3,
  BedDouble,
  BookOpen,
  Briefcase,
  Plane,
  Building2,
  Calendar,
  Camera,
  CalendarCheck,
  Eye,
  Glasses,
  CreditCard,
  GraduationCap,
  CalendarClock,
  ClipboardList,
  Croissant,
  Clock,
  Dumbbell,
  Gem,
  Globe,
  HelpCircle,
  Home,
  LayoutDashboard,
  LayoutGrid,
  ListOrdered,
  Mail,
  Megaphone,
  MessageSquare,
  MessageSquareText,
  PartyPopper,
  Car,
  ClipboardCheck,
  FileText,
  Flower2,
  Package,
  Palette,
  PawPrint,
  Scale,
  Wrench,
  Scissors,
  ScrollText,
  Settings,
  Shirt,
  ShoppingBag,
  Award,
  BellRing,
  Ticket,
  Baby,
  Pizza,
  School,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  Syringe,
  Tag,
  Wand2,
  UtensilsCrossed,
  UserCog,
  Users,
  UserPlus,
  UsersRound,
  WashingMachine,
  Wine,
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
      { label: 'Nichos', href: '/dashboard/profile-features', icon: LayoutGrid },
      { label: 'Vitrine', href: '/dashboard/niches-showcase', icon: LayoutGrid },
      // Site do Meada (CMS-root, B1): o CMS é EMBUTIDO pro root — sempre visível, sem feature flag.
      // Aponta pra /dashboard/cms; o backend mira a company-âncora da plataforma p/ o super-admin.
      { label: 'Site', href: '/dashboard/cms', icon: Globe },
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
 *
 * <p>{@code features} (camada 9.0 — plumbing): mapa de feature flags resolvidas do nicho
 * ({key → enabled}, de /admin/me.features). Threadado aqui agora pra que a SM-M só precise
 * adicionar UM item de nav atrás de {@code features?.cms === true} — ex.:
 * {@code if (features?.cms) groups.push({ heading: 'Site', items: [{ label: 'Página', href: '/dashboard/cms', icon: Globe }] })}.
 * Esta SM-L NÃO adiciona item de CMS — só passa o parâmetro pronto.
 */
export function getNavForProfile(
  profileId: string | null | undefined,
  features?: Record<string, boolean>,
): NavGroup[] {
  // Perfil vertical (camada 7.1/7.2): grupo próprio no topo. Demais perfis seguem o nav padrão.
  const base = profileGroups(profileId)
  // CMS (camada 9.x / SM-M): o grupo "Site" só aparece se a feature 'cms' está ligada pro nicho
  // (resolvido em /admin/me.features). superAdmin não tem features → não vê. Vem por último.
  if (features?.cms === true) {
    return [...base, CMS_GROUP]
  }
  return base
}

/** Grupos base por perfil (sem o gate de feature flags). */
function profileGroups(profileId: string | null | undefined): NavGroup[] {
  if (profileId === 'sushi') return [SUSHI_GROUP, ...NAV_GROUPS]
  if (profileId === 'legal') return [LEGAL_GROUP, ...NAV_GROUPS]
  if (profileId === 'restaurant') return [RESTAURANT_GROUP, ...NAV_GROUPS]
  if (profileId === 'dental') return [DENTAL_GROUP, ...NAV_GROUPS]
  if (profileId === 'salon') return [SALON_GROUP, ...NAV_GROUPS]
  if (profileId === 'pousada') return [POUSADA_GROUP, ...NAV_GROUPS]
  if (profileId === 'academia') return [ACADEMIA_GROUP, ...NAV_GROUPS]
  if (profileId === 'pet') return [PET_GROUP, ...NAV_GROUPS]
  if (profileId === 'oficina') return [OFICINA_GROUP, ...NAV_GROUPS]
  if (profileId === 'nutri') return [NUTRI_GROUP, ...NAV_GROUPS]
  if (profileId === 'barbearia') return [BARBEARIA_GROUP, ...NAV_GROUPS]
  if (profileId === 'eventos') return [EVENTOS_GROUP, ...NAV_GROUPS]
  if (profileId === 'estetica') return [ESTETICA_GROUP, ...NAV_GROUPS]
  if (profileId === 'comida') return [COMIDA_GROUP, ...NAV_GROUPS]
  if (profileId === 'floricultura') return [FLORICULTURA_GROUP, ...NAV_GROUPS]
  if (profileId === 'pizzaria') return [PIZZARIA_GROUP, ...NAV_GROUPS]
  if (profileId === 'adega') return [ADEGA_GROUP, ...NAV_GROUPS]
  if (profileId === 'escola') return [ESCOLA_GROUP, ...NAV_GROUPS]
  if (profileId === 'atelie') return [ATELIE_GROUP, ...NAV_GROUPS]
  if (profileId === 'casamento') return [CASAMENTO_GROUP, ...NAV_GROUPS]
  if (profileId === 'concessionaria') return [CONCESSIONARIA_GROUP, ...NAV_GROUPS]
  if (profileId === 'lavanderia') return [LAVANDERIA_GROUP, ...NAV_GROUPS]
  if (profileId === 'dermatologia') return [DERMATOLOGIA_GROUP, ...NAV_GROUPS]
  if (profileId === 'fotografia') return [FOTOGRAFIA_GROUP, ...NAV_GROUPS]
  if (profileId === 'cursos') return [CURSOS_GROUP, ...NAV_GROUPS]
  if (profileId === 'lingerie') return [LINGERIE_GROUP, ...NAV_GROUPS]
  if (profileId === 'moda_infantil') return [MODA_INFANTIL_GROUP, ...NAV_GROUPS]
  if (profileId === 'las') return [LAS_GROUP, ...NAV_GROUPS]
  if (profileId === 'padaria') return [PADARIA_GROUP, ...NAV_GROUPS]
  if (profileId === 'otica') return [OTICA_GROUP, ...NAV_GROUPS]
  if (profileId === 'papelaria') return [PAPELARIA_GROUP, ...NAV_GROUPS]
  if (profileId === 'viagens') return [VIAGENS_GROUP, ...NAV_GROUPS]
  if (profileId === 'suplementos') return [SUPLEMENTOS_GROUP, ...NAV_GROUPS]
  return NAV_GROUPS
}

/** Grupo "Site" (CMS) — só injetado quando features.cms está on (SM-M). */
const CMS_GROUP: NavGroup = {
  heading: 'Site',
  items: [{ label: 'Página', href: '/dashboard/cms', icon: Globe }],
}

/** Grupo de navegação exclusivo do perfil sushi (camada 7.1). */
const SUSHI_GROUP: NavGroup = {
  heading: 'Restaurante',
  items: [
    { label: 'Cardápio', href: '/dashboard/sushi-menu', icon: UtensilsCrossed },
    { label: 'Categorias', href: '/dashboard/sushi-categories', icon: Tag },
    { label: 'Pedidos', href: '/dashboard/sushi-orders', icon: ClipboardList },
    { label: 'Status & Notificações', href: '/dashboard/sushi-statuses', icon: BellRing },
    { label: 'Cupons', href: '/dashboard/sushi-coupons', icon: Ticket },
    { label: 'Fidelidade', href: '/dashboard/sushi-loyalty', icon: Award },
    { label: 'Configurações', href: '/dashboard/sushi-settings', icon: Settings },
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

/** Grupo de navegação exclusivo do perfil academia (camada 7.7 + piloto Onda 2 do backlog). */
const ACADEMIA_GROUP: NavGroup = {
  heading: 'Academia',
  items: [
    { label: 'Planos', href: '/dashboard/academia-plans', icon: Dumbbell },
    { label: 'Aulas', href: '/dashboard/academia-classes', icon: CalendarClock },
    { label: 'Matrículas', href: '/dashboard/academia-memberships', icon: ClipboardList },
    { label: 'Check-ins', href: '/dashboard/academia-checkins', icon: ClipboardCheck },
    { label: 'Fila de espera', href: '/dashboard/academia-waitlist', icon: ListOrdered },
    { label: 'Avulsos', href: '/dashboard/academia-day-passes', icon: Ticket },
    { label: 'Cupons', href: '/dashboard/academia-coupons', icon: Tag },
    { label: 'Fidelidade', href: '/dashboard/academia-loyalty', icon: Award },
    { label: 'Indicações', href: '/dashboard/academia-referrals', icon: UserPlus },
    { label: 'Relatórios', href: '/dashboard/academia-reports', icon: BarChart3 },
    { label: 'Configurações', href: '/dashboard/academia-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil pet (camada 7.8). "Pet Shop". Rotas /dashboard/pet-*
 * distintas (professionals/services do salon usam /dashboard/professionals e /dashboard/salon-*). */
const PET_GROUP: NavGroup = {
  heading: 'Pet Shop',
  items: [
    { label: 'Profissionais', href: '/dashboard/pet-professionals', icon: Stethoscope },
    { label: 'Serviços', href: '/dashboard/pet-services', icon: Wand2 },
    { label: 'Animais', href: '/dashboard/pet-animals', icon: PawPrint },
    { label: 'Agenda', href: '/dashboard/pet-appointments', icon: CalendarClock },
    { label: 'Configurações', href: '/dashboard/pet-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil oficina (camada 7.9). "Oficina". Rotas /dashboard/oficina-*. */
const OFICINA_GROUP: NavGroup = {
  heading: 'Oficina',
  items: [
    { label: 'Mecânicos', href: '/dashboard/oficina-mechanics', icon: Wrench },
    { label: 'Veículos', href: '/dashboard/oficina-vehicles', icon: Car },
    { label: 'Ordens', href: '/dashboard/oficina-orders', icon: ClipboardCheck },
    { label: 'Configurações', href: '/dashboard/oficina-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil nutri (camada 8.0). "Nutri". Rotas /dashboard/nutri-*. */
const NUTRI_GROUP: NavGroup = {
  heading: 'Nutri',
  items: [
    { label: 'Profissionais', href: '/dashboard/nutri-professionals', icon: Stethoscope },
    { label: 'Pacientes', href: '/dashboard/nutri-patients', icon: Users },
    { label: 'Planos', href: '/dashboard/nutri-plans', icon: FileText },
    { label: 'Agenda', href: '/dashboard/nutri-appointments', icon: CalendarClock },
    { label: 'Configurações', href: '/dashboard/nutri-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil barbearia (camada 8.1). "Barbearia". Rotas
 * /dashboard/barber-*. A Fila é a tela da escapada (walk-in com posição derivada). */
const BARBEARIA_GROUP: NavGroup = {
  heading: 'Barbearia',
  items: [
    { label: 'Barbeiros', href: '/dashboard/barber-barbers', icon: Scissors },
    { label: 'Serviços', href: '/dashboard/barber-services', icon: Wand2 },
    { label: 'Agenda', href: '/dashboard/barber-appointments', icon: CalendarClock },
    { label: 'Fila', href: '/dashboard/barber-queue', icon: ListOrdered },
    { label: 'Cupons', href: '/dashboard/barber-coupons', icon: Ticket },
    { label: 'Fidelidade', href: '/dashboard/barber-loyalty', icon: Award },
    { label: 'Relatórios', href: '/dashboard/barber-reports', icon: BarChart3 },
    { label: 'Configurações', href: '/dashboard/barber-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil eventos (camada 8.2). "Eventos". Rotas
 * /dashboard/eventos-*. Propostas tem os DOIS editores inline (orçamento + cronograma). */
const EVENTOS_GROUP: NavGroup = {
  heading: 'Eventos',
  items: [
    { label: 'Cerimonialistas', href: '/dashboard/eventos-planners', icon: PartyPopper },
    { label: 'Propostas', href: '/dashboard/eventos-proposals', icon: ClipboardList },
    { label: 'Configurações', href: '/dashboard/eventos-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil estetica (camada 8.3). "Estética". Rotas
 * /dashboard/estetica-*. Pacotes é a tela da escapada (saldo de sessões que decrementa). */
const ESTETICA_GROUP: NavGroup = {
  heading: 'Estética',
  items: [
    { label: 'Profissionais', href: '/dashboard/estetica-professionals', icon: Stethoscope },
    { label: 'Procedimentos', href: '/dashboard/estetica-procedures', icon: Sparkles },
    { label: 'Pacotes', href: '/dashboard/estetica-packages', icon: Gem },
    { label: 'Agenda', href: '/dashboard/estetica-appointments', icon: CalendarClock },
    { label: 'Configurações', href: '/dashboard/estetica-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil comida (delivery iFood-style). "Comida". Rotas
 * /dashboard/comida-* (todos os nichos seguem o padrão {nicho}-*, inclusive o sushi). */
const COMIDA_GROUP: NavGroup = {
  heading: 'Comida',
  items: [
    { label: 'Cardápio', href: '/dashboard/comida-menu', icon: UtensilsCrossed },
    { label: 'Pedidos', href: '/dashboard/comida-orders', icon: ClipboardList },
    { label: 'Cupons', href: '/dashboard/comida-coupons', icon: Ticket },
    { label: 'Fidelidade', href: '/dashboard/comida-loyalty', icon: Award },
    { label: 'Zonas de entrega', href: '/dashboard/comida-zones', icon: Package },
    { label: 'Relatórios', href: '/dashboard/comida-reports', icon: BarChart3 },
    { label: 'Configurações', href: '/dashboard/comida-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil floricultura (loja de flores, camada 8.5). "Floricultura". */
const FLORICULTURA_GROUP: NavGroup = {
  heading: 'Floricultura',
  items: [
    { label: 'Catálogo', href: '/dashboard/floricultura-catalog', icon: Flower2 },
    { label: 'Pedidos', href: '/dashboard/floricultura-orders', icon: ClipboardList },
    { label: 'Configurações', href: '/dashboard/floricultura-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil pizzaria (pizzaria delivery, camada 8.6). "Pizzaria".
 * Rotas /dashboard/pizzaria-* — Cardápio inclui a gestão de sabores p/ pizza meio-a-meio. */
const PIZZARIA_GROUP: NavGroup = {
  heading: 'Pizzaria',
  items: [
    { label: 'Cardápio', href: '/dashboard/pizzaria-menu', icon: Pizza },
    { label: 'Pedidos', href: '/dashboard/pizzaria-orders', icon: ClipboardList },
    { label: 'Configurações', href: '/dashboard/pizzaria-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil adega (delivery de bebidas, camada 8.9). "Adega".
 * Rotas /dashboard/adega-* — Pedidos mostra o selo +18 (age_confirmed) pra compliance. */
const ADEGA_GROUP: NavGroup = {
  heading: 'Adega',
  items: [
    { label: 'Catálogo', href: '/dashboard/adega-menu', icon: Wine },
    { label: 'Pedidos', href: '/dashboard/adega-orders', icon: ClipboardList },
    { label: 'Cupons', href: '/dashboard/adega-coupons', icon: Ticket },
    { label: 'Fidelidade', href: '/dashboard/adega-loyalty', icon: Award },
    { label: 'Configurações', href: '/dashboard/adega-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil escola (educação infantil, camada 8.19). "Escola".
 * Rotas /dashboard/escola-* — Alunos são sub-entidade do responsável; Visitas é a agenda leve. */
const ESCOLA_GROUP: NavGroup = {
  heading: 'Escola',
  items: [
    { label: 'Turmas', href: '/dashboard/escola-classes', icon: School },
    { label: 'Alunos', href: '/dashboard/escola-students', icon: Baby },
    { label: 'Matrículas', href: '/dashboard/escola-enrollments', icon: ClipboardList },
    { label: 'Visitas', href: '/dashboard/escola-visits', icon: CalendarCheck },
    { label: 'Configurações', href: '/dashboard/escola-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil atelie (costura sob medida/arte/design, camada 8.14). "Ateliê".
 * Rotas /dashboard/atelie-* — Propostas tem os 2 editores (orçamento + provas/ajustes) + cupom/sinal/
 * medidas; Materiais é o catálogo do autofill; Relatórios é o faturamento (onda 2 do backlog). */
const ATELIE_GROUP: NavGroup = {
  heading: 'Ateliê',
  items: [
    { label: 'Artesãos', href: '/dashboard/atelie-artisans', icon: Scissors },
    { label: 'Propostas', href: '/dashboard/atelie-proposals', icon: FileText },
    { label: 'Materiais', href: '/dashboard/atelie-catalog', icon: Package },
    { label: 'Cupons', href: '/dashboard/atelie-coupons', icon: Ticket },
    { label: 'Relatórios', href: '/dashboard/atelie-reports', icon: BarChart3 },
    { label: 'Configurações', href: '/dashboard/atelie-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil casamento (assessoria de casamento, camada 8.7). "Casamento".
 * Rotas /dashboard/casamento-* — Propostas tem os 3 editores (orçamento + cronograma + checklist). */
const CASAMENTO_GROUP: NavGroup = {
  heading: 'Casamento',
  items: [
    { label: 'Assessores', href: '/dashboard/casamento-planners', icon: Gem },
    { label: 'Propostas', href: '/dashboard/casamento-proposals', icon: FileText },
    { label: 'Catálogo', href: '/dashboard/casamento-catalog', icon: Package },
    { label: 'Cupons', href: '/dashboard/casamento-coupons', icon: Ticket },
    { label: 'Relatórios', href: '/dashboard/casamento-reports', icon: BarChart3 },
    { label: 'Configurações', href: '/dashboard/casamento-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil concessionaria (loja de carros, camada 8.17). "Concessionária".
 * Rotas /dashboard/concessionaria-* — híbrido triplo: estoque + test-drive + lead. */
const CONCESSIONARIA_GROUP: NavGroup = {
  heading: 'Concessionária',
  items: [
    { label: 'Estoque', href: '/dashboard/concessionaria-vehicles', icon: Car },
    { label: 'Vendedores', href: '/dashboard/concessionaria-salespeople', icon: Users },
    { label: 'Test-drives', href: '/dashboard/concessionaria-testdrives', icon: CalendarCheck },
    { label: 'Leads', href: '/dashboard/concessionaria-leads', icon: Megaphone },
    { label: 'Configurações', href: '/dashboard/concessionaria-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil lavanderia (lavagem com coleta+entrega, camada 8.10). */
const LAVANDERIA_GROUP: NavGroup = {
  heading: 'Lavanderia',
  items: [
    { label: 'Serviços', href: '/dashboard/lavanderia-services', icon: WashingMachine },
    { label: 'Pedidos', href: '/dashboard/lavanderia-orders', icon: ClipboardList },
    { label: 'Configurações', href: '/dashboard/lavanderia-settings', icon: Settings },
  ],
}

/** Grupo de navegação exclusivo do perfil dermatologia (clínica dermatológica, camada 8.11). */
const DERMATOLOGIA_GROUP: NavGroup = {
  heading: 'Dermatologia',
  items: [
    { label: 'Dermatologistas', href: '/dashboard/dermatologia-professionals', icon: Stethoscope },
    { label: 'Pacientes', href: '/dashboard/dermatologia-patients', icon: Users },
    { label: 'Tipos de Atendimento', href: '/dashboard/dermatologia-procedures', icon: Syringe },
    { label: 'Agenda', href: '/dashboard/dermatologia-appointments', icon: CalendarCheck },
    { label: 'Configurações', href: '/dashboard/dermatologia-settings', icon: Settings },
  ],
}

const FOTOGRAFIA_GROUP: NavGroup = {
  heading: 'Fotografia',
  items: [
    { label: 'Fotógrafos', href: '/dashboard/fotografia-professionals', icon: Users },
    { label: 'Pacotes', href: '/dashboard/fotografia-packages', icon: Package },
    { label: 'Agenda', href: '/dashboard/fotografia-appointments', icon: Camera },
    { label: 'Configurações', href: '/dashboard/fotografia-settings', icon: Settings },
  ],
}

const CURSOS_GROUP: NavGroup = {
  heading: 'Cursos',
  items: [
    { label: 'Cursos', href: '/dashboard/cursos-courses', icon: BookOpen },
    { label: 'Matrículas', href: '/dashboard/cursos-enrollments', icon: GraduationCap },
    { label: 'Pagamentos', href: '/dashboard/cursos-payments', icon: CreditCard },
    { label: 'Configurações', href: '/dashboard/cursos-settings', icon: Settings },
  ],
}

const LINGERIE_GROUP: NavGroup = {
  heading: 'Lingerie',
  items: [
    { label: 'Catálogo', href: '/dashboard/lingerie-catalog', icon: Shirt },
    { label: 'Pedidos', href: '/dashboard/lingerie-orders', icon: ShoppingBag },
    { label: 'Configurações', href: '/dashboard/lingerie-settings', icon: Settings },
  ],
}

const MODA_INFANTIL_GROUP: NavGroup = {
  heading: 'Moda Infantil',
  items: [
    { label: 'Catálogo', href: '/dashboard/moda-infantil-catalog', icon: Baby },
    { label: 'Pedidos', href: '/dashboard/moda-infantil-orders', icon: ShoppingBag },
    { label: 'Configurações', href: '/dashboard/moda-infantil-settings', icon: Settings },
  ],
}

const LAS_GROUP: NavGroup = {
  heading: 'Lãs',
  items: [
    { label: 'Catálogo', href: '/dashboard/las-catalog', icon: Scissors },
    { label: 'Pedidos', href: '/dashboard/las-orders', icon: ShoppingBag },
    { label: 'Configurações', href: '/dashboard/las-settings', icon: Settings },
  ],
}

const PADARIA_GROUP: NavGroup = {
  heading: 'Padaria',
  items: [
    { label: 'Cardápio', href: '/dashboard/padaria-menu', icon: Croissant },
    { label: 'Pedidos', href: '/dashboard/padaria-orders', icon: ClipboardList },
    { label: 'Configurações', href: '/dashboard/padaria-settings', icon: Settings },
  ],
}

const OTICA_GROUP: NavGroup = {
  heading: 'Ótica',
  items: [
    { label: 'Optometristas', href: '/dashboard/otica-professionals', icon: Stethoscope },
    { label: 'Exames', href: '/dashboard/otica-exams', icon: Eye },
    { label: 'Catálogo', href: '/dashboard/otica-catalog', icon: Glasses },
    { label: 'Pedidos', href: '/dashboard/otica-orders', icon: ClipboardList },
    { label: 'Configurações', href: '/dashboard/otica-settings', icon: Settings },
  ],
}

const PAPELARIA_GROUP: NavGroup = {
  heading: 'Papelaria',
  items: [
    { label: 'Catálogo', href: '/dashboard/papelaria-catalog', icon: Mail },
    { label: 'Pedidos', href: '/dashboard/papelaria-orders', icon: ClipboardList },
    { label: 'Configurações', href: '/dashboard/papelaria-settings', icon: Settings },
  ],
}

const VIAGENS_GROUP: NavGroup = {
  heading: 'Viagens',
  items: [
    { label: 'Consultores', href: '/dashboard/viagens-consultants', icon: Users },
    { label: 'Propostas', href: '/dashboard/viagens-proposals', icon: Plane },
    { label: 'Configurações', href: '/dashboard/viagens-settings', icon: Settings },
  ],
}

const SUPLEMENTOS_GROUP: NavGroup = {
  heading: 'Suplementos',
  items: [
    { label: 'Produtos', href: '/dashboard/suplementos-products', icon: Dumbbell },
    { label: 'Pedidos', href: '/dashboard/suplementos-orders', icon: ClipboardList },
    { label: 'Configurações', href: '/dashboard/suplementos-settings', icon: Settings },
  ],
}
