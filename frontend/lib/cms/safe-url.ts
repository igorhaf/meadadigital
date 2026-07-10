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

const YT_ID = /^[A-Za-z0-9_-]{6,20}$/

/**
 * Converte um link de vídeo colado pelo tenant (bloco video) num src de embed SEGURO — mais
 * estrito que safeFrameSrc: só YouTube/Vimeo, e o src final é SEMPRE reconstruído no player
 * oficial (youtube-nocookie / player.vimeo) a partir do ID extraído, nunca a URL crua.
 * Aceita: youtube.com/watch?v=ID, youtu.be/ID, shorts/ID, embed/ID, vimeo.com/ID,
 * player.vimeo.com/video/ID. Qualquer outro host/forma → undefined (bloco não renderiza iframe).
 */
export function safeVideoEmbedSrc(raw: string | null | undefined): string | undefined {
  if (!raw) return undefined
  let u: URL
  try {
    u = new URL(raw.trim())
  } catch {
    return undefined
  }
  if (u.protocol !== 'https:') return undefined
  const host = u.hostname.replace(/^www\./, '')
  if (host === 'youtube.com' || host === 'm.youtube.com' || host === 'youtube-nocookie.com') {
    const id =
      u.pathname.startsWith('/embed/') || u.pathname.startsWith('/shorts/')
        ? u.pathname.split('/')[2]
        : u.searchParams.get('v')
    return id && YT_ID.test(id) ? `https://www.youtube-nocookie.com/embed/${id}` : undefined
  }
  if (host === 'youtu.be') {
    const id = u.pathname.slice(1).split('/')[0]
    return id && YT_ID.test(id) ? `https://www.youtube-nocookie.com/embed/${id}` : undefined
  }
  if (host === 'vimeo.com' || host === 'player.vimeo.com') {
    const m = u.pathname.match(/\/(\d{6,})(?:$|\/)/)
    return m ? `https://player.vimeo.com/video/${m[1]}` : undefined
  }
  return undefined
}
