package com.meada.whatsapp.profiles.atelie.proposals;

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
 * Extrai a tag {@code <proposta_atelie>{...}</proposta_atelie>} da resposta da IA e ABRE a proposta
 * (camada 8.14). Espelho do PropostaEventoConfirmHandler, com UM modo só: NÃO há sub-entidade a
 * cadastrar — o cliente é o próprio contact da conversa (snapshots customer_name/phone).
 *
 * <p>NÃO usa tool calling / responseSchema. Cria a proposta em 'rascunho' (total 0, SEM itens, SEM
 * provas — a equipe monta o orçamento e as provas no painel). {@code project_type} ausente/inválido
 * → 'costura'. {@code artisan_id} inválido → ignora o artesão mas ainda abre. Qualquer falha →
 * {@link Optional#empty()} + warn (a mensagem da IA segue sem efeito colateral).
 */
@Component
public class PropostaAtelieConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PropostaAtelieConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<proposta_atelie>\\s*(\\{.*?\\})\\s*</proposta_atelie>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final AtelieProposalService proposalService;

    public PropostaAtelieConfirmHandler(ObjectMapper objectMapper, AtelieProposalService proposalService) {
        this.objectMapper = objectMapper;
        this.proposalService = proposalService;
    }

    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripOrderTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e abre a proposta em 'rascunho'. {@link Optional#empty()} quando: não há tag, JSON
     * inválido, briefing faltando, ou a abertura falha. O {@code contactId} (cliente) vem da conversa.
     */
    public Optional<AtelieProposal> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("atelie: tag <proposta_atelie> com JSON inválido p/ conversa {} ({}) — proposta não aberta",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String briefing = root.path("briefing").asText(null);
        if (briefing == null || briefing.isBlank()) {
            log.warn("atelie: tag <proposta_atelie> sem briefing p/ conversa {} — proposta não aberta", conversationId);
            return Optional.empty();
        }
        String projectType = textOrNull(root.path("project_type").asText(null));
        String occasion = textOrNull(root.path("occasion").asText(null));
        String notes = textOrNull(root.path("notes").asText(null));
        UUID artisanId = parseUuid(root.path("artisan_id").asText(null));
        LocalDate estimatedDate = parseDate(root.path("estimated_date").asText(null));

        try {
            AtelieProposal created = proposalService.open(companyId, contactId, null, artisanId, conversationId,
                projectType, occasion, estimatedDate, briefing, notes);
            log.info("atelie: proposta {} aberta p/ conversa {} (cliente {})", created.id(), conversationId, contactId);
            return Optional.of(created);
        } catch (AtelieProposalService.ArtisanNotFoundException | AtelieProposalService.InactiveArtisanException e) {
            // artesão inválido/inativo: reabre SEM o artesão (a proposta ainda nasce).
            try {
                AtelieProposal created = proposalService.open(companyId, contactId, null, null, conversationId,
                    projectType, occasion, estimatedDate, briefing, notes);
                log.info("atelie: proposta {} aberta SEM artesão (id inválido/inativo) p/ conversa {}", created.id(), conversationId);
                return Optional.of(created);
            } catch (RuntimeException e2) {
                log.warn("atelie: falha ao abrir proposta sem artesão p/ conversa {} ({}) — mensagem segue sem proposta",
                    conversationId, e2.getMessage());
                return Optional.empty();
            }
        } catch (RuntimeException e) {
            log.warn("atelie: falha ao abrir proposta p/ conversa {} ({}) — mensagem segue sem proposta",
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
