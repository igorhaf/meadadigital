import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',
  // Dev atrás do Caddy por subdomínio (fase 0.5): o Next 16 bloqueia recursos de dev (HMR)
  // de origens que não sejam localhost. Sem isto, a hidratação não completa e o form de login
  // cai no submit NATIVO (GET com a senha na URL — exatamente o sintoma observado). Lista os
  // hosts servidos pelo Caddy para o dev runtime confiar neles.
  allowedDevOrigins: [
    'meadadigital.local',
    'meada.meadadigital.local',
    'juridico.meadadigital.local',
    'dental.meadadigital.local',
    'sushi.meadadigital.local',
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
