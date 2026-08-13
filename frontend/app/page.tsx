import { redirect } from 'next/navigation'

/**
 * Raiz do app: o produto É o painel nesta fase — manda direto pro dashboard.
 * Sem sessão, o layout protegido devolve o usuário ao /login.
 */
export default function RootPage() {
  redirect('/dashboard')
}
