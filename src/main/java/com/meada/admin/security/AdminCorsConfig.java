package com.meada.admin.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;
import java.util.Objects;

/**
 * CORS para os endpoints do painel admin ({@code /admin/**}) e as rotas PÚBLICAS de browser
 * ({@code /api/**}: access-logs, chat widget, invitations). Permite que o frontend (origens
 * configuradas via {@code admin.cors-allowed-origins}) chame a API cross-origin.
 *
 * <p><b>Por que um CorsFilter e NÃO o WebMvcConfigurer.addCorsMappings</b> (mudança da fase
 * 0.5 — dev multi-domínio): o {@link JwtAuthenticationFilter} (@Order 2) e o
 * WebhookSecretFilter (@Order 1) respondem 401/403 DIRETO no filtro, sem passar pelo
 * DispatcherServlet — então o CORS do MVC não decora essas respostas de erro. Um 401
 * cross-origin sem {@code Access-Control-Allow-Origin} é BLOQUEADO pela leitura no browser
 * (Failed to fetch), quebrando o login quando o frontend (processo.meadadigital.local) e a
 * API (api.meadadigital.local) são origens distintas. O {@link CorsFilter}, registrado com
 * {@link Ordered#HIGHEST_PRECEDENCE} (antes dos filtros de auth), decora TODA resposta —
 * inclusive os 401/403 dos filtros — e trata o preflight OPTIONS cedo.
 *
 * <p><b>allowCredentials(false)</b> (decisão A1): o token vai no header
 * {@code Authorization: Bearer}, NÃO em cookie. Origens vêm do env (lista específica).
 *
 * <p>Null-guard: se {@code admin.cors-allowed-origins} faltar no YAML, o campo vem null →
 * lista vazia (nenhuma origem cross-origin permitida; em dev/prod a key sempre deve estar setada).
 *
 * <p><b>Hardening de origem (auditoria de segurança):</b> o curinga {@code *.meadadigital.local}
 * é aceitável em DEV (o painel roda em N subdomínios na porta 80) com {@code allowCredentials=false}
 * — o JWT vive em cookie httpOnly (via @supabase/ssr), inalcançável por JS cross-origin, então um
 * subdomínio de tenant não consegue ler resposta de {@code /admin/**} com credencial. AINDA ASSIM,
 * em PROD a recomendação é listar origens EXPLÍCITAS do painel (root + nichos), evitando o curinga
 * global ({@code *}) — esse, sim, é rejeitado aqui (sanitizeOrigins) por ser largo demais.
 */
@Configuration
public class AdminCorsConfig {

    private final List<String> allowedOrigins;

    public AdminCorsConfig(AdminProperties adminProperties) {
        this.allowedOrigins = sanitizeOrigins(Objects.requireNonNullElse(
            adminProperties.corsAllowedOrigins(), List.of()));
    }

    /**
     * Remove curingas GLOBAIS perigosos ({@code *}, {@code http://*}, {@code https://*}) — um
     * "qualquer origem" anula o propósito da allowlist. Curinga de SUBDOMÍNIO
     * ({@code http://*.dominio}) é mantido (escopo a um domínio que controlamos). Lista vazia após
     * a limpeza = nenhuma origem cross-origin (fail-closed).
     */
    private static List<String> sanitizeOrigins(List<String> origins) {
        return origins.stream()
            .map(String::trim)
            .filter(o -> !o.isEmpty())
            .filter(o -> !o.equals("*") && !o.equals("http://*") && !o.equals("https://*"))
            .toList();
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // setAllowedOriginPatterns (não setAllowedOrigins): aceita curinga de subdomínio
        // (ex.: http://*.meadadigital.local) além de origens exatas. Válido porque
        // allowCredentials=false. Necessário desde que o front roda em N subdomínios na porta 80.
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/admin/**", config);
        source.registerCorsConfiguration("/api/**", config);
        // /public/** é chamado CLIENT-SIDE pelo browser em alguns casos (ex.: o bloco niches_grid
        // do CMS faz fetch de /public/niches a partir de meadadigital.local) → precisa de CORS.
        source.registerCorsConfiguration("/public/**", config);

        FilterRegistrationBean<CorsFilter> bean =
            new FilterRegistrationBean<>(new CorsFilter(source));
        // Antes dos filtros de auth (WebhookSecretFilter @Order 1, JwtAuthenticationFilter
        // @Order 2) — assim as respostas de erro deles também saem com headers CORS.
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
