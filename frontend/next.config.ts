import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',
  // Dev por subdomínio: o Next 16 bloqueia recursos de dev (HMR/JS) de origens não confiáveis.
  // Sem isto, a hidratação não completa e o form de login cai no submit NATIVO (GET com a senha
  // na URL — sintoma observado), e o branding client-side (me.productName) não carrega → parece
  // o "front do Meada root". Cobre o domínio-base + TODOS os subdomínios (nichos E tenants, ex.:
  // comida-modelo.meadadigital.local no "Acessar admin") via wildcard — assim tenant novo não
  // exige editar esta lista.
  allowedDevOrigins: [
    'meadadigital.local',
    '*.meadadigital.local',
  ],
  // Compat: o sushi foi alinhado ao padrão {nicho}-* (sushi-menu/sushi-orders). Redireciona as
  // rotas genéricas antigas (bookmark/histórico) pras novas. 308 permanente.
  async redirects() {
    return [
      { source: '/dashboard/menu', destination: '/dashboard/sushi-menu', permanent: true },
      { source: '/dashboard/orders', destination: '/dashboard/sushi-orders', permanent: true },
    ]
  },
  // Anti-clickjacking no SITE PÚBLICO do CMS (/p/**): o conteúdo é editável pelo tenant e
  // renderizado sem auth — sem isto, um terceiro poderia embutir a página num iframe invisível
  // e induzir cliques (clickjacking). X-Frame-Options (legado) + CSP frame-ancestors 'none'
  // (moderno) negam o embed. Aplicado só a /p/** (o painel /dashboard tem seu próprio contexto).
  async headers() {
    return [
      {
        source: '/p/:path*',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'Content-Security-Policy', value: "frame-ancestors 'none'" },
        ],
      },
    ]
  },
};

export default nextConfig;
