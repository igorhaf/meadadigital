package com.meada.whatsapp.profiles.viagens.proposals;

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
 * Extrai a tag {@code <proposta_viagem>{...}</proposta_viagem>} da resposta da IA e ABRE a proposta
 * (camada 8.18 / perfil viagens). Espelho EXATO do PropostaEventoConfirmHandler (chassi eventos 8.2):
 * UM modo só — NÃO há sub-entidade de cliente; o cliente é o próprio contact da conversa (snapshots
 * customer_name/phone).
 *
 * <p>NÃO usa tool calling / responseSchema. Cria a proposta em 'rascunho' (total 0, SEM itens — a
 * equipe monta a cotação no painel; espelho da proposta aberta sem itens do eventos). consultant_id
 * inválido → ignora o consultor mas abre; datas inválidas → ignora a data mas abre. Qualquer falha
 * dura → {@link Optional#empty()} + warn (a mensagem da IA segue sem efeito colateral).
 */
@Component
public class PropostaViagemConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PropostaViagemConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<proposta_viagem>\\s*(\\{.*?\\})\\s*</proposta_viagem>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final TravelProposalService proposalService;

    public PropostaViagemConfirmHandler(ObjectMapper objectMapper, TravelProposalService proposalService) {
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
     * inválido, briefing faltando, ou a abertura falha. O {@code contactId} (cliente) vem da conversa.
     */
    public Optional<TravelProposal> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("viagens: tag <proposta_viagem> com JSON inválido p/ conversa {} ({}) — proposta não aberta",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String briefing = root.path("briefing").asText(null);
        if (briefing == null || briefing.isBlank()) {
            log.warn("viagens: tag <proposta_viagem> sem briefing p/ conversa {} — proposta não aberta", conversationId);
            return Optional.empty();
        }
        String destination = textOrNull(root.path("destination").asText(null));
        String travelStyle = textOrNull(root.path("travel_style").asText(null));
        String notes = textOrNull(root.path("notes").asText(null));
        UUID consultantId = parseUuid(root.path("consultant_id").asText(null));
        Integer numTravelers = root.hasNonNull("num_travelers") && root.get("num_travelers").isNumber()
            ? root.get("num_travelers").asInt() : null;
        if (numTravelers != null && numTravelers < 1) {
            numTravelers = null;
        }
        LocalDate startDate = parseDate(root.path("start_date").asText(null));
        LocalDate endDate = parseDate(root.path("end_date").asText(null));

        try {
            TravelProposal created = proposalService.open(companyId, contactId, null, consultantId, conversationId,
                destination, startDate, endDate, numTravelers, travelStyle, briefing, notes);
            log.info("viagens: proposta {} aberta p/ conversa {} (cliente {})", created.id(), conversationId, contactId);
            return Optional.of(created);
        } catch (TravelProposalService.ConsultantNotFoundException
                | TravelProposalService.InactiveConsultantException e) {
            // consultant_id inválido/inativo → reabre SEM consultor (não é razão pra perder a proposta).
            log.warn("viagens: <proposta_viagem> com consultor inválido/inativo p/ conversa {} — abrindo sem consultor",
                conversationId);
            try {
                TravelProposal created = proposalService.open(companyId, contactId, null, null, conversationId,
                    destination, startDate, endDate, numTravelers, travelStyle, briefing, notes);
                return Optional.of(created);
            } catch (RuntimeException e2) {
                log.warn("viagens: falha ao reabrir proposta sem consultor p/ conversa {} ({}) — mensagem segue sem proposta",
                    conversationId, e2.getMessage());
                return Optional.empty();
            }
        } catch (RuntimeException e) {
            log.warn("viagens: falha ao abrir proposta p/ conversa {} ({}) — mensagem segue sem proposta",
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
