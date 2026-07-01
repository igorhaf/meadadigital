package com.meada.webhook;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Autenticação de origem do webhook da Evolution API.
 *
 * <p>Protege APENAS {@code /webhooks/**}. Roda na cadeia de filtros do servlet,
 * ANTES de o Spring MVC desserializar/validar o corpo — portanto requisição sem
 * secret válido recebe 401 ANTES de qualquer parsing/validação. É autenticação
 * antes de processamento: não vaza schema (campos do 400) a origem não-autenticada,
 * não gasta CPU desserializando payload desconhecido, e blinda bugs futuros de
 * desserialização contra quem não tem o secret.
 *
 * <p><b>Fonte do secret (precedência):</b> header {@code apikey} (preferencial);
 * se ausente/vazio, query param {@code apikey} (fallback). Mesmo nome nos dois
 * canais. O fallback de query param existe porque a Evolution self-hosted não
 * garante suporte a header customizado em webhook outbound (issues #1933/#2276).
 * Ver RISKS.md — item "Secret de webhook em query param (?apikey=) pode vazar em
 * access-log de proxy".
 *
 * <p><b>Comparação constant-time</b> via {@link MessageDigest#isEqual}: evita
 * timing attack — String.equals faz short-circuit no 1º byte divergente, vazando
 * por latência quanto do prefixo está correto. (isEqual vaza o COMPRIMENTO do
 * secret ao comparar tamanhos diferentes, fraqueza conhecida e aceitável.)
 *
 * <p><b>Fail-fast:</b> o secret esperado vem de {@code @Value("${webhook.secret}")}
 * no construtor. Sem WEBHOOK_SECRET, o bean não é criado e a app NÃO sobe.
 *
 * <p><b>Pendência HMAC (pós-MVP):</b> secret compartilhado protege contra POST
 * forjado por quem não conhece o segredo, mas NÃO contra replay (reenviar um POST
 * legítimo capturado) nem contra interceptação se o TLS for downgradeado. HMAC do
 * corpo + timestamp resolve. Hardening registrado para depois do MVP.
 *
 * <p><b>Cobertura PARCIAL de replay já existente (auditoria de segurança):</b> o handler de
 * {@code messages.upsert} aplica um GUARD DE FRESCOR por {@code messageTimestamp}
 * ({@code webhook.message-max-age-seconds}, ver RISKS.md) — um {@code messages.upsert} antigo
 * reenviado é REJEITADO por idade. Isso mitiga o replay no evento de maior risco (resposta
 * automática a contato real). Outros eventos de webhook ainda não têm anti-replay; o HMAC+
 * timestamp acima continua sendo o hardening completo. Além disso, o webhook está OFF até religar
 * consciente (RISKS.md), o que zera a exposição ativa por ora.
 *
 * <p>@Order(1): primeiro filtro custom da aplicação — garante explicitamente que
 * a verificação de secret roda antes de qualquer filtro de app futuro (rate limit,
 * tracing, etc.). Filtros built-in do Spring Boot usam ordens menores e seguem
 * antes naturalmente.
 */
@Component
@Order(1)
public class WebhookSecretFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecretFilter.class);

    private static final String WEBHOOK_PATH_PREFIX = "/webhooks/";
    private static final String SECRET_PARAM = "apikey";   // mesmo nome p/ header e query param

    private final byte[] expectedSecretBytes;

    public WebhookSecretFilter(@Value("${webhook.secret}") String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // Rede de segurança: se o Spring resolver o placeholder para vazio em
            // vez de barrar, ainda derrubamos o startup com mensagem clara.
            throw new IllegalStateException("webhook.secret must be configured (env WEBHOOK_SECRET)");
        }
        this.expectedSecretBytes = webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Só filtra {@code /webhooks/**}. Demais rotas (futuro health, actuator, API
     * do painel com auth própria) passam sem este filtro.
     *
     * <p>Nota: getRequestURI() inclui o context path. Aqui o context é "/", então
     * o match é direto. Atrás de proxy com context path != "/", revisar.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String provided = request.getHeader(SECRET_PARAM);
        if (provided == null || provided.isBlank()) {
            provided = request.getParameter(SECRET_PARAM);   // fallback: query param
        }

        if (provided == null || provided.isBlank()) {
            reject(request, response, "missing_secret");
            return;
        }

        boolean valid = MessageDigest.isEqual(
            provided.getBytes(StandardCharsets.UTF_8), expectedSecretBytes);

        if (!valid) {
            reject(request, response, "invalid_secret");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 401 sem corpo (não distingue missing de invalid para o cliente — não dá
     * pista ao atacante). A distinção fica só no log interno, estruturado.
     */
    private void reject(HttpServletRequest request, HttpServletResponse response, String reason) {
        log.warn("webhook auth failed path={} remote_addr={} reason={}",
            request.getRequestURI(), request.getRemoteAddr(), reason);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
