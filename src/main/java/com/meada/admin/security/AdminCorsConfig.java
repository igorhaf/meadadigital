package com.meada.admin.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Objects;

/**
 * CORS para os endpoints do painel admin ({@code /admin/**}). Permite que o frontend
 * (origens configuradas via {@code admin.cors-allowed-origins}) chame a API.
 *
 * <p>{@code WebMvcConfigurer.addCorsMappings} (não SecurityFilterChain — não usamos
 * Spring Security). Mora em {@code admin.security} porque CORS é parte da defesa
 * cross-origin do painel (decisão C1).
 *
 * <p><b>allowCredentials(false)</b> (decisão A1): o token vai no header
 * {@code Authorization: Bearer}, NÃO em cookie. Sem credentials, não precisamos refletir
 * a origem exata e evitamos esse acoplamento. Origens vêm do env (lista específica), não
 * wildcard — mas o wildcard seria permitido aqui justamente por credentials=false.
 *
 * <p>Null-guard nas origens: se a key {@code admin.cors-allowed-origins} faltar no YAML,
 * o campo do record vem null — tratamos como lista vazia (mesmo padrão do filtro). Lista
 * vazia = nenhuma origem cross-origin permitida (o frontend não conseguiria chamar; em
 * dev/prod a key sempre deve estar setada).
 */
@Configuration
public class AdminCorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public AdminCorsConfig(AdminProperties adminProperties) {
        this.allowedOrigins = Objects.requireNonNullElse(
            adminProperties.corsAllowedOrigins(), List.of());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/admin/**")
            .allowedOrigins(allowedOrigins.toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
    }
}
