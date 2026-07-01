/**
 * Sanitização de URL para conteúdo do CMS editável pelo tenant.
 *
 * Os campos de URL (buttonHref, imageUrl, embedUrl) são editados pelo tenant no painel e
 * renderizam NO SITE PÚBLICO (/p/{slug}). React escapa TEXTO, mas NÃO escapa atributos de URL —
 * um `javascript:` ou `data:text/html,...` colado vira XSS armazenado (executa ao clicar/carregar).
 *
 * `safeUrl` admite só esquemas inertes (http/https/mailto/tel) e paths relativos/âncora; qualquer
 * outra coisa (javascript:, data:, vbscript:, file:, etc.) → undefined, e o chamador NÃO renderiza
 * o link/imagem. `safeFrameSrc` é mais estrito (só https) pro <iframe> do bloco map.
 */

const ALLOWED_SCHEMES = new Set(['http:', 'https:', 'mailto:', 'tel:'])

/** Sanitiza href/src genérico. Retorna a URL se segura, ou undefined se deve ser descartada. */
export function safeUrl(raw: string | null | undefined): string | undefined {
  if (!raw) return undefined
  const v = raw.trim()
  if (v === '') return undefined
  // Path relativo ou âncora do próprio site: sempre seguro. '//' (protocol-relative) NÃO é relativo.
  if ((v.startsWith('/') && !v.startsWith('//')) || v.startsWith('#')) {
    return v
  }
  // Esquema absoluto: validar contra a allowlist. URL inválida (sem esquema) → tratar como inseguro.
  try {
    const scheme = new URL(v).protocol
    return ALLOWED_SCHEMES.has(scheme) ? v : undefined
  } catch {
    return undefined
  }
}

/** Sanitiza src de <iframe> (bloco map): só https absoluto. Qualquer outra coisa → undefined. */
export function safeFrameSrc(raw: string | null | undefined): string | undefined {
  if (!raw) return undefined
  const v = raw.trim()
  try {
    return new URL(v).protocol === 'https:' ? v : undefined
  } catch {
    return undefined
  }
}
