import { createServerClient } from '@supabase/ssr'
import { NextResponse, type NextRequest } from 'next/server'

import {
  subdomainFromHost,
  isMeadaHost,
  rawSubdomain,
  isNicheSubdomain,
  baseUrl,
  nicheLoginUrl,
  SUBDOMAIN_HEADER,
} from '@/lib/profiles/subdomain'
import { resolveCompany } from '@/lib/profiles/resolve-company'

/**
 * Middleware de sessão Supabase. ÚNICA responsabilidade: refrescar o token a cada
 * request (escrevendo os cookies atualizados na response). NÃO decide rota — a
 * barreira de autorização ("sem sessão → /login") vive em app/(protected)/layout.tsx.
 * Manter a separação: middleware = manutenção de sessão; layout = autorização.
 *
 * Sem fail-fast de envs aqui de propósito: o middleware roda em TODA request; um
 * throw aqui viraria 500 em toda página (inclusive /login). O fail-fast com mensagem
 * acionável mora no client.ts/server.ts (chamados explicitamente por página/layout);
 * e o createServerClient lança por conta própria se url/key forem undefined.
 *
 * Padrão oficial do @supabase/ssr para Next app router:
 *  1. cria uma NextResponse a partir da request;
 *  2. cria o serverClient com getAll/setAll que escrevem cookies NA request E NA
 *     response (mutar a mesma response, não criar outra — senão a sessão não renova);
 *  3. chama getUser() — que contata o Auth server e dispara o refresh do cookie.
 *     getSession() NÃO serve aqui (lê o cookie cru, falsificável — ver README da lib).
 *     Ignoramos o RESULTADO de getUser para roteamento; o efeito colateral (refresh)
 *     é o objetivo.
 */
export async function middleware(request: NextRequest) {
  const host = request.headers.get('host')
  const path = request.nextUrl.pathname

  // CMS por domínio custom (SM-N): se o host NÃO é um domínio do Meada, é um domínio próprio de
  // tenant apontado pro nosso servidor → reescreve internamente pro render público por domínio.
  // Só reescreve requisições de PÁGINA (não /api, /_next, /public, nem o próprio /p) — assets e
  // chamadas de API seguem normais. Raiz (/) → home; /{pageSlug} → página interna daquele tenant.
  if (
    !isMeadaHost(host) &&
    !path.startsWith('/api') &&
    !path.startsWith('/_next') &&
    !path.startsWith('/public') &&
    !path.startsWith('/p/')
  ) {
    const hostname = (host ?? '').split(':')[0]
    const url = request.nextUrl.clone()
    const sub = path.replace(/^\/+|\/+$/g, '') // tira barras das pontas
    url.pathname = sub ? `/p/by-domain/${hostname}/${sub}` : `/p/by-domain/${hostname}`
    return NextResponse.rewrite(url)
  }

  // {empresa}.meadadigital.com (roteamento de domínios): um subdomínio Meada que NÃO é nicho
  // conhecido é candidato a EMPRESA (o slug é o subdomínio). Resolve no backend (cache 60s):
  //  - não existe / suspensa → redirect pra raiz (meadadigital.com);
  //  - tem CMS publicado → render público por slug (/p/{slug});
  //  - sem CMS → redirect pro login do NICHO da empresa ({perfil}.meadadigital.com/login).
  // Nichos conhecidos (sushi, comida, ...) NÃO entram aqui — seguem o login do nicho abaixo.
  const candidate = rawSubdomain(host)
  if (
    isMeadaHost(host) &&
    candidate &&
    candidate !== 'www' &&
    !isNicheSubdomain(candidate) &&
    !path.startsWith('/api') &&
    !path.startsWith('/_next') &&
    !path.startsWith('/public') &&
    !path.startsWith('/p/') &&
    // Rotas de APP que precisam funcionar no subdomínio da empresa (ex.: o "Acessar admin"
    // do super-admin abre {empresa}.dominio/auth/confirm → /dashboard, logado como o tenant).
    // Sem excluir, o roteamento de empresa engoliria esses paths (→ 404 no /p/{slug}/...).
    !path.startsWith('/auth') &&
    !path.startsWith('/login') &&
    !path.startsWith('/dashboard') &&
    !path.startsWith('/admin')
  ) {
    const r = await resolveCompany(candidate)
    if (!r.exists) {
      return NextResponse.redirect(baseUrl(request.nextUrl, host))
    }
    if (r.hasCms) {
      const url = request.nextUrl.clone()
      const rest = path.replace(/^\/+|\/+$/g, '')
      url.pathname = rest ? `/p/${candidate}/${rest}` : `/p/${candidate}`
      return NextResponse.rewrite(url)
    }
    // empresa sem CMS → login do nicho dela
    return NextResponse.redirect(nicheLoginUrl(request.nextUrl, host, r.profileSubdomain ?? 'meada'))
  }

  // Subdomain detection (camada 7.0): resolve o perfil pelo host e injeta como header de
  // request, para que Server Components / layouts downstream possam ler o perfil sem re-parsear.
  // localhost / domínio-base → 'meada' (genérico/universal).
  const subdomain = subdomainFromHost(host)
  request.headers.set(SUBDOMAIN_HEADER, subdomain)

  let response = NextResponse.next({ request })

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll()
        },
        setAll(cookiesToSet) {
          // escreve na request (para handlers/layouts downstream verem nesta request)
          // e recria a response com a request mutada, depois grava na response (para
          // o browser nas próximas requests). O pattern duplo é o documentado.
          cookiesToSet.forEach(({ name, value }) => request.cookies.set(name, value))
          response = NextResponse.next({ request })
          cookiesToSet.forEach(({ name, value, options }) =>
            response.cookies.set(name, value, options)
          )
        },
      },
    }
  )

  // dispara o refresh do token (efeito colateral). Resultado ignorado para rota.
  await supabase.auth.getUser()

  return response
}

/**
 * Matcher: roda em todas as rotas EXCETO assets estáticos do Next e favicon —
 * senão o middleware recriaria sessão em cada request de imagem/css (custo + ruído).
 * /api/* É coberto de propósito: route handlers futuros que precisem de auth se
 * beneficiam do cookie de sessão atualizado.
 */
export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
}
