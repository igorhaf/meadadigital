package com.meada.admin.security;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * Provê o {@link JWKSource} que o JwtAuthenticationFilter usa para verificar a assinatura
 * dos tokens Supabase (ES256, chaves assimétricas).
 *
 * <p>O Supabase meada-delta-01 migrou de HS256 (secret compartilhado) para JWT Signing
 * Keys assimétricas (ECC P-256 / ES256) em 2026-06-04. A chave pública é servida no
 * endpoint JWKS ({@code supabase.jwks-url}); o {@link RemoteJWKSet} busca, cacheia, e
 * re-busca automaticamente quando a {@code kid} muda (rotação futura) — o ganho real
 * sobre um secret estático.
 *
 * <p>O RemoteJWKSet é LAZY: não faz HTTP no construtor, só na 1ª verificação. Isso
 * permite que, nos testes, um {@code @Bean @Primary} (AdminTestJwksConfig) sobrescreva
 * este bean com um JWKSource local — o RemoteJWKSet de prod fica instanciado mas inerte
 * (nunca resolve a URL dummy de teste).
 */
@Configuration
public class JwksConfig {

    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${supabase.jwks-url}") String jwksUrl) throws MalformedURLException {
        // URI.create lança IllegalArgumentException (unchecked) se a sintaxe for inválida;
        // .toURL() lança MalformedURLException (checked) — único checked a declarar.
        URL url = URI.create(jwksUrl).toURL();
        return new RemoteJWKSet<>(url);
    }
}
