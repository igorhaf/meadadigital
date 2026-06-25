package com.meada.whatsapp.profiles.casamento.proposals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <proposta_casamento>{...}</proposta_casamento>} da resposta da IA e ABRE a
 * proposta (camada 8.7). Espelho do PropostaEventoConfirmHandler, UM modo só: NÃO há sub-entidade a
 * cadastrar — o cliente (noivos) é o próprio contact da conversa (snapshots customer_name/phone).
 *
 * <p>NÃO usa tool calling / responseSchema. Cria a proposta em 'rascunho' (total 0, SEM itens — a
 * equipe monta o orçamento no painel). Qualquer falha → {@link Optional#empty()} + warn (a mensagem da
 * IA segue sem efeito colateral). planner_id inválido → ignorado (a proposta abre sem assessor).
 */
@Component
public class PropostaCasamentoConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PropostaCasamentoConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<proposta_casamento>\\s*(\\{.*?\\})\\s*</proposta_casamento>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final WeddingProposalService proposalService;

    public PropostaCasamentoConfirmHandler(ObjectMapper objectMapper, WeddingProposalService proposalService) {
        this.objectMapper = objectMapper;
        this.proposalService = proposalService;
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

    /**
     * Extrai a tag e abre a proposta em 'rascunho'. {@link Optional#empty()} quando: não há tag, JSON
     * inválido, briefing faltando, ou a abertura falha. O {@code contactId} (noivos) vem da conversa.
     * Um planner_id inexistente/inativo é tratado como ausência (abre sem assessor), não bloqueia.
     */
    public Optional<WeddingProposal> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
                                                    String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("casamento: tag <proposta_casamento> com JSON inválido p/ conversa {} ({}) — proposta não aberta",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String briefing = root.path("briefing").asText(null);
        if (briefing == null || briefing.isBlank()) {
            log.warn("casamento: tag <proposta_casamento> sem briefing p/ conversa {} — proposta não aberta", conversationId);
            return Optional.empty();
        }
        String weddingStyle = textOrNull(root.path("wedding_style").asText(null));
        String notes = textOrNull(root.path("notes").asText(null));
        UUID plannerId = parseUuid(root.path("planner_id").asText(null));
        Integer guestCount = root.hasNonNull("guest_count") && root.get("guest_count").isNumber()
            ? root.get("guest_count").asInt() : null;
        if (guestCount != null && guestCount < 0) {
            guestCount = null;
        }
        LocalDate weddingDate = parseDate(root.path("wedding_date").asText(null));

        try {
            WeddingProposal created = proposalService.open(companyId, contactId, null, plannerId, conversationId,
                weddingStyle, weddingDate, guestCount, briefing, notes);
            log.info("casamento: proposta {} aberta p/ conversa {} (cliente {})", created.id(), conversationId, contactId);
            return Optional.of(created);
        } catch (WeddingProposalService.PlannerNotFoundException | WeddingProposalService.InactivePlannerException e) {
            // planner_id inválido → abre sem assessor (não bloqueia a abertura).
            try {
                WeddingProposal created = proposalService.open(companyId, contactId, null, null, conversationId,
                    weddingStyle, weddingDate, guestCount, briefing, notes);
                log.info("casamento: proposta {} aberta SEM assessor (planner_id inválido) p/ conversa {}",
                    created.id(), conversationId);
                return Optional.of(created);
            } catch (RuntimeException e2) {
                log.warn("casamento: falha ao abrir proposta sem assessor p/ conversa {} ({}) — mensagem segue sem proposta",
                    conversationId, e2.getMessage());
                return Optional.empty();
            }
        } catch (RuntimeException e) {
            log.warn("casamento: falha ao abrir proposta p/ conversa {} ({}) — mensagem segue sem proposta",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static String textOrNull(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        return raw;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
