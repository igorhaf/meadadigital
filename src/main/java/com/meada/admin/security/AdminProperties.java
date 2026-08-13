package com.meada.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Config do painel admin, ligada ao bloco {@code admin} do application.yml. Registrado
 * automaticamente via {@code @ConfigurationPropertiesScan} (no WhatsappApplication).
 *
 * <p>Mora em {@code admin.security} porque a allowlist é parte do mecanismo de
 * segurança/autorização (decisão C1: auth-related em security). Detalhamento das
 * decisões: ver DEVELOPMENT.md, seção "Camada 4.1 — decisões de auth/admin".
 *
 * <p><b>Não normaliza nada</b> — entrega os valores crus do YAML. A comparação
 * case-insensitive de email (allowlist) é regra de negócio do {@code JwtAuthenticationFilter},
 * não de config: o filtro normaliza com {@code toLowerCase()} ao comparar. Properties são
 * input; normalização é do consumidor.
 *
 * <p><b>Listas vazias são legítimas (sem {@code @NotEmpty}):</b> se
 * {@code superAdminEmails} vier vazia (default em dev/teste), o app sobe normalmente e
 * NENHUM email é super-admin — todos caem em tenant_admin (e quem não tem linha em
 * public.users recebe 403 user_not_provisioned). Esse é o comportamento desejado em
 * dev/teste; em prod, a allowlist precisa de ao menos um email para haver onboarding.
 * NOTA: se a key faltar de todo no YAML, o campo vem {@code null} (não lista vazia) — o
 * consumidor (filtro, CORS config) trata null defensivamente.
 *
 * @param superAdminEmails  emails tratados como super-admin pelo filtro JWT (sem SELECT
 *                          em public.users). CSV no YAML; comparação case-insensitive é
 *                          responsabilidade do filtro. Vazia/null = nenhum super-admin.
 * @param corsAllowedOrigins origens do frontend aceitas no CORS de /admin/** (ex.
 *                          http://localhost:3000 em dev).
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(List<String> superAdminEmails, List<String> corsAllowedOrigins) {
}
