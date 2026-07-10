package com.meada.profiles.concessionaria.tradein;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <troca_carro>{"brand":"...","model":"...","year":N,"km":N,"condition":"...",
 * "asking_cents":N,"interest_vehicle_id":"UUID|null"}</troca_carro>} e ABRE a proposta de
 * trade-in (onda Concessionária 2, backlog #5). A IA só COLETA — quem avalia é a equipe no
 * painel (trava de precificação intacta). Best-effort.
 */
@Component
public class TrocaCarroHandler {

    private static final Logger log = LoggerFactory.getLogger(TrocaCarroHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<troca_carro>\\s*(\\{.*?\\})\\s*</troca_carro>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final TradeInService tradeInService;
    private final JdbcTemplate jdbcTemplate;

    public TrocaCarroHandler(ObjectMapper objectMapper, TradeInService tradeInService,
                             JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.tradeInService = tradeInService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    public void parseAndOpen(UUID companyId, UUID conversationId, UUID contactId, String aiResponseText) {
        if (aiResponseText == null) {
            return;
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(m.group(1));
            String brand = root.path("brand").asText(null);
            String model = root.path("model").asText(null);
            if (brand == null || brand.isBlank() || model == null || model.isBlank()) {
                log.warn("concessionaria: <troca_carro> sem marca/modelo p/ conversa {} — não aberta", conversationId);
                return;
            }
            UUID interest = null;
            String rawInterest = root.path("interest_vehicle_id").asText(null);
            if (rawInterest != null && !rawInterest.isBlank() && !"null".equalsIgnoreCase(rawInterest)) {
                try {
                    interest = UUID.fromString(rawInterest);
                } catch (IllegalArgumentException ignored) {
                    // interesse inválido não derruba a coleta.
                }
            }
            String customerName = contactId == null ? "(cliente)" : jdbcTemplate.query(
                    "select name from contacts where company_id = ? and id = ?",
                    (rs, rn) -> rs.getString("name"), companyId, contactId)
                .stream().findFirst().orElse("(cliente)");
            UUID id = tradeInService.open(companyId, contactId, conversationId, customerName, interest,
                brand.strip(), model.strip(),
                root.path("year").isNumber() ? root.path("year").asInt() : null,
                root.path("km").isNumber() ? root.path("km").asInt() : null,
                root.path("condition").asText(null),
                root.path("asking_cents").isNumber() ? root.path("asking_cents").asInt() : null);
            log.info("concessionaria: trade-in {} aberto p/ conversa {} ({} {})",
                id, conversationId, brand, model);
        } catch (Exception e) {
            log.warn("concessionaria: tag <troca_carro> inválida p/ conversa {} ({})",
                conversationId, e.getMessage());
        }
    }
}
