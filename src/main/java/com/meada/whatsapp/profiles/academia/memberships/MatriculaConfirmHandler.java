package com.meada.whatsapp.profiles.academia.memberships;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.messaging.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <matricula>{...}</matricula>} da resposta da IA e cria a matrícula (camada
 * 7.7). Espelho dos confirm handlers anteriores.
 *
 * <p>Parse: plan_id, class_ids (array de UUID), student_name (opc), notes (opc). Resolve o contato
 * da conversa; student_name vem do JSON ou do contact. O service valida plano/aulas/vaga/anti-dupla;
 * qualquer falha → {@link Optional#empty()} + warn. Tag namespace exclusivo {@code <matricula>}.
 */
@Component
public class MatriculaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(MatriculaConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<matricula>\\s*(\\{.*?\\})\\s*</matricula>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final AcademiaMembershipService membershipService;

    public MatriculaConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                   AcademiaMembershipService membershipService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.membershipService = membershipService;
    }

    public boolean hasMatriculaTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripMatriculaTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    public Optional<AcademiaMembership> parseAndCreate(UUID companyId, UUID conversationId,
                                                       UUID contactId, String aiResponseText) {
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
            log.warn("academia: tag <matricula> com JSON inválido p/ conversa {} ({}) — matrícula não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawPlan = root.path("plan_id").asText(null);
        JsonNode classIdsNode = root.path("class_ids");
        String studentNameJson = root.path("student_name").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawPlan == null || !classIdsNode.isArray() || classIdsNode.isEmpty()) {
            log.warn("academia: tag <matricula> com campos faltando p/ conversa {} — matrícula não criada",
                conversationId);
            return Optional.empty();
        }

        UUID planId;
        List<UUID> classIds = new ArrayList<>();
        try {
            planId = UUID.fromString(rawPlan);
            for (JsonNode n : classIdsNode) {
                classIds.add(UUID.fromString(n.asText()));
            }
        } catch (RuntimeException e) {
            log.warn("academia: tag <matricula> com ids inválidos p/ conversa {} ({}) — matrícula não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String studentName = studentNameJson != null && !studentNameJson.isBlank()
            ? studentNameJson.strip()
            : contactRepository.findNameByConversationId(conversationId)
                .filter(n -> n != null && !n.isBlank())
                .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Aluno"));
        String studentPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            AcademiaMembership created = membershipService.create(companyId, planId, classIds, contactId,
                conversationId, studentName, studentPhone, notes);
            log.info("academia: matrícula {} criada p/ conversa {} (plano {}, {} aulas)",
                created.id(), conversationId, planId, classIds.size());
            return Optional.of(created);
        } catch (AcademiaMembershipService.ClassFullException e) {
            log.warn("academia: <matricula> com aula lotada ({}) p/ conversa {} — não criada", e.className(), conversationId);
            return Optional.empty();
        } catch (AcademiaMembershipService.AlreadyActiveException e) {
            log.warn("academia: <matricula> p/ contato que já tem matrícula ativa (conversa {}) — não criada", conversationId);
            return Optional.empty();
        } catch (AcademiaMembershipService.PlanNotFoundException
                 | AcademiaMembershipService.PlanInactiveException
                 | AcademiaMembershipService.ClassNotFoundException
                 | AcademiaMembershipService.ClassInactiveException
                 | AcademiaMembershipService.NoClassesException e) {
            log.warn("academia: <matricula> com plano/aula inválido ou inativo p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("academia: falha ao criar matrícula p/ conversa {} ({}) — mensagem segue sem matrícula",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
