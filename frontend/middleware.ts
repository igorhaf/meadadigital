import { createServerClient } from '@supabase/ssr'
import { NextResponse, type NextRequest } from 'next/server'

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
