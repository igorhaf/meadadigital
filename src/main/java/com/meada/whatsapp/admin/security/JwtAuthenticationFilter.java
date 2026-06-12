package com.meada.whatsapp.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Autentica requests a {@code /admin/**} validando o JWT do Supabase (ES256, chaves
 * assimétricas via JWKS — ver {@link JwksConfig}) e resolvendo a identidade de forma
 * EAGER (decisão B2): produz um {@link AuthenticatedUser} completo que os controllers
 * leem via {@code @RequestAttribute("authenticatedUser")}.
 *
 * <p>A verificação é feita por um {@link DefaultJWTProcessor} com
 * {@link JWSVerificationKeySelector}(ES256) sobre o {@link JWKSource} injetado: o
 * processor seleciona a chave pública pela {@code kid} do token e verifica a assinatura.
 * Suporta rotação automática de keys (o RemoteJWKSet re-busca quando a kid muda).
 *
 * <p>@Order(2): roda depois do WebhookSecretFilter (@Order(1)); cada filtro só atua no
 * seu prefixo de path (shouldNotFilter). Espelha o padrão do WebhookSecretFilter.
 *
 * <p>Divisão 401 vs 403 (ver DEVELOPMENT.md seção 4.1): 401 = autenticação (quem é?);
 * 403 user_not_provisioned = token válido mas tenant-admin sem linha em public.users
 * (o filtro não consegue construir o AuthenticatedUser). O 403 forbidden_not_super_admin
 * é de AUTORIZAÇÃO e mora no controller, não aqui.
 *
 * <p>Controle de fluxo via {@link AuthRejectException} interna: cada validação lança;
 * doFilterInternal captura num ponto e escreve a resposta de erro (reject). Mantém o
 * happy path linear.
 */
@Component
@Order(2)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String ADMIN_PATH_PREFIX = "/admin/";
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTH_USER_ATTRIBUTE = "authenticatedUser";

    private static final String SELECT_USER_DATA =
        "select company_id, palette_id from users where id = ?";

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final Set<String> allowlistLower;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(AdminProperties adminProperties,
                                   JWKSource<SecurityContext> jwkSource,
                                   JdbcTemplate jdbcTemplate,
                                   ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        // Processor que verifica a assinatura ES256 selecionando a chave pública pela kid
        // do token (via JWKSource) E valida claims (exp/nbf) pelo DefaultJWTClaimsVerifier
        // padrão do nimbus — com tolerância a clock skew, que a validação manual não tinha.
        // exp expirado vira BadJWTException, mapeada para token_expired em parseAndVerify.
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, jwkSource));
        this.jwtProcessor = processor;
        // allowlist normalizada (lowercase) uma vez no boot; null-safe se a key faltar no YAML.
        this.allowlistLower = Objects.requireNonNullElse(
                adminProperties.superAdminEmails(), List.<String>of())
            .stream()
            .map(s -> s.toLowerCase())
            .collect(Collectors.toSet());
    }

    /** Só filtra /admin/**. Demais rotas (webhook, futuro health) passam direto.
     * OPTIONS (preflight CORS do browser) também passa direto — preflight não tem
     * credenciais e deve ser respondido pelo handler de CORS do Spring (AdminCorsConfig),
     * não pelo filtro de auth. Sem essa exclusão, o filtro responderia 401 e mataria o
     * preflight antes do CORS, quebrando todo request cross-origin do browser. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod())
            || !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthenticatedUser user;
        try {
            String token = extractBearerToken(request);
            VerifiedClaims claims = parseAndVerify(token);
            user = resolveUser(claims);
        } catch (AuthRejectException e) {
            reject(request, response, e.status, e.reason);
            return;
        }
        request.setAttribute(AUTH_USER_ATTRIBUTE, user);
        filterChain.doFilter(request, response);
    }

    /**
     * Token do header Authorization. Ausente → 401 missing; presente mas sem "Bearer "
     * → 401 malformed.
     *
     * <p>Comparação de "Bearer " é CASE-SENSITIVE intencionalmente. A RFC 6750 permite
     * case-insensitive, mas nosso ecossistema é fechado (o apiFetch do frontend sempre
     * manda "Bearer " exato); qualquer caller que mande "bearer "/"BEARER " está fora do
     * padrão e merece o 401 — permissividade só mascararia cliente mal configurado.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            throw new AuthRejectException(401, "missing_auth_header");
        }
        if (!header.startsWith(BEARER_PREFIX)) {
            throw new AuthRejectException(401, "malformed_auth_header");
        }
        return header.substring(BEARER_PREFIX.length());
    }

    /** Parseia, verifica assinatura ES256 (via JWKS) + exp/nbf, e extrai email+userId. */
    private VerifiedClaims parseAndVerify(String token) {
        // process() faz, num passo: parse + seleção da chave pública pela kid +
        // verificação ES256 + validação de claims (exp/nbf) pelo DefaultJWTClaimsVerifier
        // padrão do nimbus (que inclui tolerância a clock skew). Mapeamento das exceções
        // (ordem importa: subclasse BadJWTException ANTES de BadJOSEException):
        //   ParseException    → malformed_token (não é um JWT parseável)
        //   BadJWTException   → token_expired (claims-level: exp/nbf)
        //   BadJOSEException  → invalid_signature (BadJWSException: assinatura/kid)
        //   JOSEException     → invalid_signature (erro genérico de crypto)
        JWTClaimsSet claims;
        try {
            claims = jwtProcessor.process(token, null);
        } catch (ParseException e) {
            throw new AuthRejectException(401, "malformed_token");
        } catch (BadJWTException e) {
            // O DefaultJWTClaimsVerifier por padrão só valida exp e nbf — em MVP mapeamos
            // genericamente para token_expired. Se um dia configurarmos requiredClaims,
            // este catch precisa diferenciar (via e.getMessage() ou inspeção).
            throw new AuthRejectException(401, "token_expired");
        } catch (BadJOSEException e) {
            throw new AuthRejectException(401, "invalid_signature");
        } catch (JOSEException e) {
            throw new AuthRejectException(401, "invalid_signature");
        }

        String email;
        try {
            email = claims.getStringClaim("email");
        } catch (ParseException e) {
            throw new AuthRejectException(401, "invalid_claims");
        }
        if (email == null || email.isBlank()) {
            throw new AuthRejectException(401, "invalid_claims");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AuthRejectException(401, "invalid_claims");
        }

        UUID userId;
        try {
            userId = UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new AuthRejectException(401, "invalid_claims");
        }

        return new VerifiedClaims(email, userId);
    }

    /**
     * Resolve a identidade (eager). Allowlist (lowercase) checada ANTES do banco:
     * super-admin pula o SELECT (otimização B2) e recebe paletteId "meada-default"
     * constante — ele não tem linha em public.users de onde ler tema (decisão Opção A
     * da camada 5.0). Tenant-admin sem linha em public.users → 403 user_not_provisioned;
     * com linha, lê company_id E palette_id na MESMA query (palette_id é NOT NULL
     * DEFAULT 'meada-default' no banco, nunca null).
     */
    private AuthenticatedUser resolveUser(VerifiedClaims claims) {
        if (allowlistLower.contains(claims.email().toLowerCase())) {
            return new AuthenticatedUser(
                claims.email(), claims.userId(), AdminRole.SUPER_ADMIN, null, "meada-default");
        }
        try {
            UserData data = jdbcTemplate.queryForObject(
                SELECT_USER_DATA,
                (rs, rowNum) -> new UserData(
                    (UUID) rs.getObject("company_id"), rs.getString("palette_id")),
                claims.userId());
            return new AuthenticatedUser(
                claims.email(), claims.userId(), AdminRole.TENANT_ADMIN,
                data.companyId(), data.paletteId());
        } catch (EmptyResultDataAccessException e) {
            throw new AuthRejectException(403, "user_not_provisioned");
        }
    }

    /** Escreve a resposta de erro direto (o filtro não passa pelo GlobalExceptionHandler). */
    private void reject(HttpServletRequest request, HttpServletResponse response,
                        int status, String reason) throws IOException {
        log.warn("admin auth rejected status={} reason={} path={}",
            status, reason, request.getRequestURI());
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String errorText = status == 403 ? "Forbidden" : "Unauthorized";
        objectMapper.writeValue(
            response.getWriter(), Map.of("error", errorText, "reason", reason));
    }

    /** Claims já validadas e tipadas — detalhe interno do filtro. */
    private record VerifiedClaims(String email, UUID userId) {
    }

    /** Tupla (company_id, palette_id) do SELECT em public.users — detalhe interno. */
    private record UserData(UUID companyId, String paletteId) {
    }

    /** Sinaliza rejeição com status HTTP + reason; capturada em doFilterInternal. */
    private static final class AuthRejectException extends RuntimeException {
        final int status;
        final String reason;

        AuthRejectException(int status, String reason) {
            super(reason);
            this.status = status;
            this.reason = reason;
        }
    }
}
