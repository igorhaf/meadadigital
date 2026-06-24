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
};

export default nextConfig;
