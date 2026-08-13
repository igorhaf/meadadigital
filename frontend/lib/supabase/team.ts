import { createClient } from './client'

/**
 * Membro (usuário) da empresa do tenant (camada 5.16 #6) — lido de public.users via SDK +
 * RLS. O RLS de users isola por company_id, então o admin só vê os colegas da própria
 * empresa. role é owner|admin|agent (papel dentro do tenant).
 */
export type TeamMember = {
  id: string
  email: string
  role: string
  createdAt: string
}

/**
 * Lista os usuários da empresa do tenant logado (SDK + RLS). Ordenado por created_at asc
 * (o primeiro admin/owner primeiro). Usado na tela /dashboard/team, seção "Membros atuais".
 */
export async function getMyTeamMembers(): Promise<TeamMember[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('users')
    .select('id, email, role, created_at')
    .order('created_at', { ascending: true })

  if (error) {
    throw error
  }

  return (data ?? []).map((u) => ({
    id: u.id,
    email: u.email,
    role: u.role,
    createdAt: u.created_at,
  }))
}
