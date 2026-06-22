package com.meada.whatsapp.profiles.estetica.packages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.messaging.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <compra_pacote>{...}</compra_pacote>} e registra a INTENÇÃO de compra de um
 * pacote (camada 8.3). O pacote nasce em 'pendente' (a clínica confirma o pagamento depois). O PREÇO
 * vem do CATÁLOGO (procedimento), NÃO da tag — a IA não inventa valor. NÃO usa tool calling. Qualquer
 * falha → {@link Optional#empty()} + warn.
 */
@Component
public class CompraPacoteConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(CompraPacoteConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<compra_pacote>\\s*(\\{.*?\\})\\s*</compra_pacote>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final AestheticPackageService packageService;

    public CompraPacoteConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                      AestheticPackageService packageService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.packageService = packageService;
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

    public Optional<AestheticPackage> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("estetica: tag <compra_pacote> com JSON inválido p/ conversa {} ({}) — não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawProc = root.path("procedure_id").asText(null);
        String notes = root.path("notes").asText(null);
        int totalSessions = root.hasNonNull("total_sessions") && root.get("total_sessions").isNumber()
            ? root.get("total_sessions").asInt() : 0;
        if (rawProc == null || totalSessions <= 0) {
            log.warn("estetica: tag <compra_pacote> com procedure_id/total_sessions faltando p/ conversa {} — não criado",
                conversationId);
            return Optional.empty();
        }

        UUID procedureId;
        try {
            procedureId = UUID.fromString(rawProc);
        } catch (RuntimeException e) {
            log.warn("estetica: <compra_pacote> com procedure_id inválido p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        }

        String customerName = contactRepository.findNameByConversationId(conversationId)
            .filter(n -> n != null && !n.isBlank())
            .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Cliente"));
        String customerPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            // o preço vem do catálogo (o service snapshota o unit_price do procedimento) — NÃO da tag.
            AestheticPackage pkg = packageService.create(companyId, contactId, customerName, customerPhone,
                procedureId, conversationId, totalSessions, notes);
            log.info("estetica: pacote {} criado (pendente) p/ conversa {} (proc {}, {} sessões)",
                pkg.id(), conversationId, procedureId, totalSessions);
            return Optional.of(pkg);
        } catch (AestheticPackageService.ProcedureNotFoundException e) {
            log.warn("estetica: <compra_pacote> com procedimento inexistente p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("estetica: falha ao criar pacote p/ conversa {} ({}) — mensagem segue sem pacote",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
