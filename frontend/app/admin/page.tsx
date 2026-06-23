import { redirect } from 'next/navigation'

/**
 * {empresa}.meadadigital.com/admin → login do tenant.
 * O /admin é só um atalho memorável para a área administrativa: redireciona pro /login no
 * MESMO host (o subdomínio da empresa é preservado), de modo que o login já vem no contexto
 * daquela empresa/nicho. Quem já tem sessão, ao chegar no /login, segue pro dashboard.
 */
export default function AdminRedirect() {
  redirect('/login')
}
