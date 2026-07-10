package com.meada.profiles.modainfantil.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <aviso_estoque_moda>{"variant_id":"..."}</aviso_estoque_moda>} e registra o
 * interesse do CONTATO da conversa na variante esgotada (onda 1 — avise-me quando voltar).
 * Best-effort: JSON/variante inválida → warn e a mensagem segue. O OutboundService remove a tag.
 */
@Component
public class AvisoEstoqueModaHandler {

    private static final Logger log = LoggerFactory.getLogger(AvisoEstoqueModaHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<aviso_estoque_moda>\\s*(\\{.*?\\})\\s*</aviso_estoque_moda>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final com.meada.profiles.modainfantil.alerts.ModaInfantilStockAlertService alertService;

    public AvisoEstoqueModaHandler(ObjectMapper objectMapper,
                                   com.meada.profiles.modainfantil.alerts.ModaInfantilStockAlertService alertService) {
        this.objectMapper = objectMapper;
        this.alertService = alertService;
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

    public void parseAndRegister(UUID companyId, UUID conversationId, UUID contactId, String aiResponseText) {
        if (aiResponseText == null || contactId == null) {
            return;
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(m.group(1));
            UUID variantId = UUID.fromString(root.path("variant_id").asText());
            boolean ok = alertService.register(companyId, contactId, variantId);
            log.info("moda-alert: aviso {} p/ variante {} (conversa {})",
                ok ? "registrado" : "ignorado", variantId, conversationId);
        } catch (Exception e) {
            log.warn("moda-alert: tag <aviso_estoque_moda> inválida p/ conversa {} ({})",
                conversationId, e.getMessage());
        }
    }
}
