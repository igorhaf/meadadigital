package com.meada.whatsapp.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
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
import java.nio.charset.StandardCharsets;
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

    // Aceite de convite (camada 5.16 #6): exige JWT válido, mas NÃO linha em public.users
    // (o convidado acabou de criar conta no Auth; a linha nasce no accept). O filtro
    // autentica este path como INVITEE, pulando resolveUser. Path: POST
    // /api/invitations/{token}/accept — casado por prefixo + sufixo (token é livre).
    private static final String INVITE_ACCEPT_PREFIX = "/api/invitations/";
    private static final String INVITE_ACCEPT_SUFFIX = "/accept";

    // Endpoints dos perfis verticais (sushi 7.1, legal 7.2, restaurant 7.3): sob /api/sushi/**,
    // /api/legal/** e /api/restaurant/**, mas são TENANT-autenticados (precisam do AuthenticatedUser,
    // como /admin/**). O filtro também os autentica.
    private static final String SUSHI_PATH_PREFIX = "/api/sushi/";
    private static final String LEGAL_PATH_PREFIX = "/api/legal/";
    private static final String RESTAURANT_PATH_PREFIX = "/api/restaurant/";
    private static final String DENTAL_PATH_PREFIX = "/api/dental/";
    private static final String SALON_PATH_PREFIX = "/api/salon/";
    private static final String POUSADA_PATH_PREFIX = "/api/pousada/";
    private static final String ACADEMIA_PATH_PREFIX = "/api/academia/";
    private static final String PET_PATH_PREFIX = "/api/pet/";
    private static final String OFICINA_PATH_PREFIX = "/api/oficina/";
    private static final String NUTRI_PATH_PREFIX = "/api/nutri/";
    private static final String BARBEARIA_PATH_PREFIX = "/api/barbearia/";
    private static final String EVENTOS_PATH_PREFIX = "/api/eventos/";
    private static final String ESTETICA_PATH_PREFIX = "/api/estetica/";
    private static final String COMIDA_PATH_PREFIX = "/api/comida/";
    private static final String FLORICULTURA_PATH_PREFIX = "/api/floricultura/";
    private static final String PIZZARIA_PATH_PREFIX = "/api/pizzaria/";
    private static final String ADEGA_PATH_PREFIX = "/api/adega/";
    private static final String ESCOLA_PATH_PREFIX = "/api/escola/";
    private static final String ATELIE_PATH_PREFIX = "/api/atelie/";
    private static final String CASAMENTO_PATH_PREFIX = "/api/casamento/";
    private static final String CONCESSIONARIA_PATH_PREFIX = "/api/concessionaria/";
    private static final String LAVANDERIA_PATH_PREFIX = "/api/lavanderia/";
    private static final String DERMATOLOGIA_PATH_PREFIX = "/api/dermatologia/";
    private static final String FOTOGRAFIA_PATH_PREFIX = "/api/fotografia/";
    private static final String CURSOS_PATH_PREFIX = "/api/cursos/";
    private static final String LINGERIE_PATH_PREFIX = "/api/lingerie/";
    private static final String MODA_INFANTIL_PATH_PREFIX = "/api/moda-infantil/";
    private static final String LAS_PATH_PREFIX = "/api/las/";
    private static final String CMS_PATH_PREFIX = "/api/cms/";

    // Junta a company para checar suspensão da empresa no mesmo SELECT (camada 6.1/6.2).
    // u.suspended / u.deleted_at: suspensão e soft-delete do usuário. c.status: 'suspended'
    // bloqueia toda a empresa. last_login_at: lido para o throttle de 5min do update.
    private static final String SELECT_USER_DATA =
        "select u.company_id, u.palette_id, u.role, u.suspended, u.deleted_at, "
            + "u.last_login_at, c.status as company_status "
            + "from users u join companies c on c.id = u.company_id where u.id = ?";

    // Update de last_login_at com throttle: só grava se passou > 5min do último (evita um
    // write por request). WHERE com o predicado de frescor torna a operação barata e idempotente.
    private static final String UPDATE_LAST_LOGIN =
        "update users set last_login_at = now() where id = ? "
            + "and (last_login_at is null or last_login_at < now() - interval '5 minutes')";

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final Set<String> allowlistLower;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(AdminProperties adminProperties,
                                   JWKSource<SecurityContext> jwkSource,
                                   @Value("${supabase.jwt-secret:}") String jwtSecret,
                                   JdbcTemplate jdbcTemplate,
                                   ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        // Processor que verifica a assinatura E valida claims (exp/nbf) pelo
        // DefaultJWTClaimsVerifier padrão do nimbus — com tolerância a clock skew, que a
        // validação manual não tinha. exp expirado vira BadJWTException, mapeada para
        // token_expired em parseAndVerify.
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            // DEV LOCAL (STAGE=local + Supabase CLI): GoTrue local assina HS256 com secret
            // simétrico (sem JWKS/ES256). Verifica HS256 via ImmutableSecret JWKSource.
            // O jwkSource ES256 de prod fica injetado mas inerte. Ver application.yml.
            JWKSource<SecurityContext> hmacSource =
                new ImmutableSecret<>(jwtSecret.getBytes(StandardCharsets.UTF_8));
            processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.HS256, hmacSource));
            log.warn("JWT em modo HS256 (dev local) — NÃO usar em produção");
        } else {
            // PROD: ES256 selecionando a chave pública pela kid do token (via JWKSource/JWKS).
            processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, jwkSource));
        }
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
        if ("OPTIONS".equals(request.getMethod())) {
            return true;   // preflight CORS passa direto (ver javadoc acima)
        }
        // Filtra /admin/**, /api/sushi/** (tenant do perfil sushi — camada 7.1) E o aceite de
        // convite (/api/invitations/{token}/accept). Demais rotas (webhook, lookup público do
        // convite, health) passam sem filtro de auth.
        String uri = request.getRequestURI();
        return !uri.startsWith(ADMIN_PATH_PREFIX)
            && !uri.startsWith(SUSHI_PATH_PREFIX)
            && !uri.startsWith(LEGAL_PATH_PREFIX)
            && !uri.startsWith(RESTAURANT_PATH_PREFIX)
            && !uri.startsWith(DENTAL_PATH_PREFIX)
            && !uri.startsWith(SALON_PATH_PREFIX)
            && !uri.startsWith(POUSADA_PATH_PREFIX)
            && !uri.startsWith(ACADEMIA_PATH_PREFIX)
            && !uri.startsWith(PET_PATH_PREFIX)
            && !uri.startsWith(OFICINA_PATH_PREFIX)
            && !uri.startsWith(NUTRI_PATH_PREFIX)
            && !uri.startsWith(BARBEARIA_PATH_PREFIX)
            && !uri.startsWith(EVENTOS_PATH_PREFIX)
            && !uri.startsWith(ESTETICA_PATH_PREFIX)
            && !uri.startsWith(COMIDA_PATH_PREFIX)
            && !uri.startsWith(FLORICULTURA_PATH_PREFIX)
            && !uri.startsWith(PIZZARIA_PATH_PREFIX)
            && !uri.startsWith(ADEGA_PATH_PREFIX)
            && !uri.startsWith(ESCOLA_PATH_PREFIX)
            && !uri.startsWith(ATELIE_PATH_PREFIX)
            && !uri.startsWith(CASAMENTO_PATH_PREFIX)
            && !uri.startsWith(CONCESSIONARIA_PATH_PREFIX)
            && !uri.startsWith(LAVANDERIA_PATH_PREFIX)
            && !uri.startsWith(DERMATOLOGIA_PATH_PREFIX)
            && !uri.startsWith(FOTOGRAFIA_PATH_PREFIX)
            && !uri.startsWith(CURSOS_PATH_PREFIX)
            && !uri.startsWith(LINGERIE_PATH_PREFIX)
            && !uri.startsWith(MODA_INFANTIL_PATH_PREFIX)
            && !uri.startsWith(LAS_PATH_PREFIX)
            && !uri.startsWith(CMS_PATH_PREFIX)
            && !isInviteAccept(request);
    }

    /** POST /api/invitations/{token}/accept — o único path /api/ que este filtro autentica. */
    private boolean isInviteAccept(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "POST".equals(request.getMethod())
            && uri.startsWith(INVITE_ACCEPT_PREFIX)
            && uri.endsWith(INVITE_ACCEPT_SUFFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthenticatedUser user;
        try {
            String token = extractBearerToken(request);
            VerifiedClaims claims = parseAndVerify(token);
            // Aceite de convite: JWT válido basta; NÃO resolve em public.users (a linha
            // nasce no accept). INVITEE com companyId null. Demais paths (/admin/**)
            // resolvem a identidade completa (super-admin allowlist ou tenant em users).
            user = isInviteAccept(request)
                ? new AuthenticatedUser(claims.email(), claims.userId(),
                    AdminRole.INVITEE, null, "meada-default")
                : resolveUser(claims);
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
        UserData data;
        try {
            data = jdbcTemplate.queryForObject(
                SELECT_USER_DATA,
                (rs, rowNum) -> new UserData(
                    (UUID) rs.getObject("company_id"), rs.getString("palette_id"),
                    rs.getString("role"), rs.getBoolean("suspended"),
                    rs.getObject("deleted_at") != null, rs.getString("company_status")),
                claims.userId());
        } catch (EmptyResultDataAccessException e) {
            throw new AuthRejectException(403, "user_not_provisioned");
        }
        // Guards de suspensão (camada 6.1/6.2): 403 distinto (NÃO 401). Soft-delete também
        // bloqueia (a linha existe mas o usuário foi removido pelo super-admin).
        if (data.deletedUser()) {
            throw new AuthRejectException(403, "user_not_provisioned");
        }
        if (data.suspended()) {
            throw new AuthRejectException(403, "forbidden_user_suspended");
        }
        if ("suspended".equals(data.companyStatus())) {
            throw new AuthRejectException(403, "forbidden_company_suspended");
        }
        // last_login_at com throttle (>5min). Best-effort: falha aqui não derruba o login.
        try {
            jdbcTemplate.update(UPDATE_LAST_LOGIN, claims.userId());
        } catch (RuntimeException e) {
            log.warn("failed to update last_login_at for {}: {}", claims.userId(), e.getMessage());
        }
        return new AuthenticatedUser(
            claims.email(), claims.userId(), AdminRole.TENANT_ADMIN,
            data.companyId(), data.paletteId(), data.role());
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

    /** Tupla do SELECT em users+companies (camada 6) — detalhe interno do filtro. */
    private record UserData(UUID companyId, String paletteId, String role,
                            boolean suspended, boolean deletedUser, String companyStatus) {
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
