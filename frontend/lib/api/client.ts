import { currentSubdomain } from '@/lib/profiles/subdomain'
import { createClient } from '@/lib/supabase/client'

const API_BASE = process.env.NEXT_PUBLIC_API_URL

/**
 * Erro de chamada ao backend admin. Carrega o status HTTP e o {@code reason} do corpo
 * JSON ({error, reason}) — o shape que o filtro JWT e os controllers do admin produzem.
 * O caller usa {@code status}/{@code reason} para tratar inline (ex. 403 → "acesso restrito").
 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly reason: string,
    // Corpo de erro completo (parseado), quando disponível. Alguns endpoints anexam detalhes
    // além do reason (ex.: 409 conflict_slot do perfil restaurant carrega o conflito). null se
    // o corpo não for JSON. Callers que não precisam ignoram.
    public readonly body: unknown = null,
  ) {
    super(`API ${status}: ${reason}`)
    this.name = 'ApiError'
  }
}

/**
 * Wrapper de fetch para o backend admin (Spring /admin/**). Concerns transversais:
 *  - injeta Authorization: Bearer <token> (token da sessão Supabase, lido a cada chamada);
 *  - 401 → sessão morta: signOut + redirect /login (não adianta tratar inline);
 *  - 403 e demais erros → lança ApiError(status, reason) para o caller tratar.
 *
 * O token vem de supabase.auth.getSession() (client-side) — sem provider próprio, o
 * @supabase/ssr já gerencia o state da sessão. getSession é tratado defensivamente:
 * se falhar (storage corrompido, race), segue sem token → backend dá 401 → branch abaixo.
 */
export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  if (!API_BASE) {
    throw new Error('NEXT_PUBLIC_API_URL não configurada (ver .env.local / .env.example).')
  }

  const supabase = createClient()
  const { data, error } = await supabase.auth.getSession()
  if (error) {
    console.error('getSession failed:', error.message)
  }
  const token = data?.session?.access_token

  const headers: Record<string, string> = {
    ...(options?.headers as Record<string, string> | undefined),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    // Subdomínio/perfil atual (camada 7.0): informa ao backend de qual "produto" a request
    // vem. Lido do hostname do browser (mesma regra do middleware). 'meada' em SSR.
    'X-Meada-Subdomain': currentSubdomain(),
  }
  // Content-Type só quando há body (POST/PUT/PATCH): evita preflight CORS desnecessário
  // e não faz sentido semântico num GET.
  if (options?.body) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })

  if (response.status === 401) {
    // Sessão morta/expirada: sai e manda para o login. Não há tratamento inline útil.
    await supabase.auth.signOut()
    window.location.href = '/login'
    // Lança para interromper o fluxo do caller (o redirect já está em curso).
    const { reason } = await readError(response)
    throw new ApiError(401, reason)
  }

  if (!response.ok) {
    const { reason, body } = await readError(response)
    throw new ApiError(response.status, reason, body)
  }

  // 204 No Content (ex.: DELETE, PATCH de toggle) não tem corpo — chamar response.json()
  // aqui lança "Unexpected end of JSON input". Callers desses endpoints tipam Promise<void>,
  // então devolvemos undefined. Cobre também qualquer resposta sem corpo (Content-Length 0).
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T
  }

  return response.json() as Promise<T>
}

/**
 * Extrai o {@code reason} do corpo de erro. Dois shapes possíveis no backend admin:
 *  - {error, reason} (filtro JWT + controllers): retorna o reason direto.
 *  - ValidationErrorResponse (400 do @Valid via GlobalExceptionHandler): NÃO tem reason
 *    → discriminado por status 400, vira 'validation_error' (sinal genérico; o zod
 *    client-side já é a 1ª barreira, este 400 é defensivo).
 * Tolerante a corpo não-JSON.
 */
async function readError(response: Response): Promise<{ reason: string; body: unknown }> {
  try {
    const body = await response.json()
    if (typeof body?.reason === 'string') {
      return { reason: body.reason, body }
    }
    if (response.status === 400) {
      return { reason: 'validation_error', body }
    }
    return { reason: 'unknown', body }
  } catch {
    return { reason: response.status === 400 ? 'validation_error' : 'unknown', body: null }
  }
}
