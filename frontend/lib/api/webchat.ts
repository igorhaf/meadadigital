/**
 * SDK mínimo do widget de chat web (camada 5.25 #73). O endpoint /api/chat/{slug} é PÚBLICO (sem
 * auth) — por isso NÃO usa o apiFetch (que injeta o token do Supabase): é um fetch cru. O widget
 * embutível de verdade é o `public/widget.js` (vanilla, dependency-free); este módulo existe só
 * para completude/tipagem caso a app Next precise falar com o endpoint público (ex.: preview do
 * widget no painel).
 */

/** Resposta do endpoint público de chat web. */
export type WebChatResponse = {
  reply: string
}

/**
 * Envia uma mensagem ao chat web público de uma empresa (por slug) e devolve a resposta da IA.
 *
 * @param apiBase  URL base do backend (ex.: process.env.NEXT_PUBLIC_API_URL)
 * @param slug     slug da empresa (companies.slug)
 * @param sessionId id de sessão do visitante (gerado no cliente)
 * @param message  texto do visitante
 */
export async function sendWebChat(
  apiBase: string,
  slug: string,
  sessionId: string,
  message: string,
): Promise<WebChatResponse> {
  const response = await fetch(`${apiBase}/api/chat/${slug}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, message }),
  })
  if (!response.ok) {
    throw new Error(`webchat failed: ${response.status}`)
  }
  return response.json() as Promise<WebChatResponse>
}
