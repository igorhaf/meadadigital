package com.meada.whatsapp.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config do bloco {@code supabase} do application.yml. Registrado via
 * {@code @ConfigurationPropertiesScan} (WhatsappApplication).
 *
 * <p>{@code url} + {@code serviceRoleKey} alimentam a Admin API do GoTrue (generate_link)
 * usada pelo "entrar como empresa" do super-admin. Vazios = recurso desabilitado (o
 * {@link com.meada.whatsapp.admin.companies.CompanyAdminController} responde 503). NÃO
 * fail-fast: o app sobe sem eles (recurso opcional).
 *
 * <p>{@code jwksUrl}/{@code jwtSecret} também moram no bloco supabase; são consumidos pelo
 * JwksConfig/JwtAuthenticationFilter via @Value — aqui só centralizam a documentação.
 */
@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(String url, String serviceRoleKey,
                                 String jwksUrl, String jwtSecret) {

    /** True se a Admin API do GoTrue está configurada (url + service_role_key presentes). */
    public boolean adminApiEnabled() {
        return url != null && !url.isBlank()
            && serviceRoleKey != null && !serviceRoleKey.isBlank();
    }
}
