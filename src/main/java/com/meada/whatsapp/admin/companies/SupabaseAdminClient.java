package com.meada.whatsapp.admin.companies;

import com.meada.whatsapp.admin.security.SupabaseProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Cliente da Admin API do GoTrue (Supabase Auth), usado pelo "entrar como empresa" do
 * super-admin: gera um magic link (token de uso único) para um usuário, que a nova aba
 * troca por uma sessão Supabase real via {@code /auth/confirm} (verifyOtp).
 *
 * <p>Requer {@code supabase.url} + {@code supabase.service-role-key}. Se ausentes
 * ({@link #enabled()} == false), o controller responde 503 sem chamar nada — recurso
 * opcional, não fail-fast no boot.
 *
 * <p>service_role key é um SEGREDO de servidor; nunca vai ao browser. O que vai ao
 * browser é o {@code hashed_token} (uso único, curta validade).
 */
@Component
public class SupabaseAdminClient {

    private final String serviceRoleKey;
    private final RestClient http;   // null quando desabilitado

    public SupabaseAdminClient(SupabaseProperties props) {
        this.serviceRoleKey = props.serviceRoleKey();
        this.http = props.adminApiEnabled()
            ? RestClient.builder().baseUrl(props.url()).build()
            : null;
    }

    /** True se a Admin API está configurada (url + service_role key). */
    public boolean enabled() {
        return http != null;
    }

    /**
     * Gera um magic link para o email e devolve o {@code hashed_token} (o que a nova aba
     * usa em /auth/confirm). Lança se a Admin API não está habilitada ou a resposta não
     * traz o token.
     */
    @SuppressWarnings("unchecked")
    public String generateMagicLinkTokenHash(String email) {
        if (!enabled()) {
            throw new IllegalStateException("supabase admin api disabled");
        }
        Map<String, Object> resp = http.post()
            .uri("/auth/v1/admin/generate_link")
            .header("apikey", serviceRoleKey)
            .header("Authorization", "Bearer " + serviceRoleKey)
            .body(Map.of("type", "magiclink", "email", email))
            .retrieve()
            .body(Map.class);
        if (resp == null) {
            throw new IllegalStateException("generate_link returned empty body");
        }
        // hashed_token: nas versões recentes do GoTrue vem no TOPO da resposta; em versões
        // antigas, dentro de "properties". Tenta os dois (topo primeiro).
        Object top = resp.get("hashed_token");
        if (top instanceof String s && !s.isBlank()) {
            return s;
        }
        if (resp.get("properties") instanceof Map<?, ?> p
                && p.get("hashed_token") instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalStateException("generate_link response missing hashed_token");
    }
}
