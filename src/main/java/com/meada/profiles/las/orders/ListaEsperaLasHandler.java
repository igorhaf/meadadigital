package com.meada.profiles.las.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <lista_espera_las>{"variant_id":"...","any_lot":true,"qty":8}</lista_espera_las>}
 * e registra o interesse do CONTATO da conversa (onda Lãs 1, backlog #1 — lista de espera de dye
 * lot). {@code any_lot} true = qualquer lote da cor serve; {@code qty} é opcional (informativo).
 * Best-effort: JSON/variante inválida → warn e a mensagem segue. O OutboundService remove a tag.
 */
@Component
public class ListaEsperaLasHandler {

    private static final Logger log = LoggerFactory.getLogger(ListaEsperaLasHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<lista_espera_las>\\s*(\\{.*?\\})\\s*</lista_espera_las>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final com.meada.profiles.las.waitlist.LasWaitlistService waitlistService;

    public ListaEsperaLasHandler(ObjectMapper objectMapper,
                                 com.meada.profiles.las.waitlist.LasWaitlistService waitlistService) {
        this.objectMapper = objectMapper;
        this.waitlistService = waitlistService;
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
            boolean anyLot = root.path("any_lot").asBoolean(false);
            int qty = root.path("qty").asInt(0);
            boolean ok = waitlistService.register(companyId, contactId, variantId, anyLot,
                qty > 0 ? qty : null);
            log.info("las-waitlist: interesse {} p/ variante {} any_lot={} (conversa {})",
                ok ? "registrado" : "ignorado", variantId, anyLot, conversationId);
        } catch (Exception e) {
            log.warn("las-waitlist: tag <lista_espera_las> inválida p/ conversa {} ({})",
                conversationId, e.getMessage());
        }
    }
}
