package com.meada.whatsapp.profiles.escola.visits;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.messaging.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <visita_escola>{...}</visita_escola>} da resposta da IA e agenda a visita
 * (camada 8.19, ESCAPADA 2). Resolve o responsável (contato) da conversa; visitorName/phone são
 * snapshots do contato. Valida data futura + período no service. Qualquer falha →
 * {@link Optional#empty()} + warn. Tag namespace exclusivo {@code <visita_escola>}.
 */
@Component
public class VisitaEscolaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(VisitaEscolaConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<visita_escola>\\s*(\\{.*?\\})\\s*</visita_escola>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final EscolaVisitService visitService;

    public VisitaEscolaConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                      EscolaVisitService visitService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.visitService = visitService;
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
     * Extrai a tag e agenda a visita. {@link Optional#empty()} quando: não há tag, JSON inválido,
     * campos faltando, data/período inválido, ou a criação falha. O {@code contactId} (responsável)
     * vem da conversa.
     */
    public Optional<EscolaVisit> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("escola: tag <visita_escola> com JSON inválido p/ conversa {} ({}) — visita não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawDate = root.path("visit_date").asText(null);
        String period = root.path("period").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawDate == null || rawDate.isBlank() || period == null || period.isBlank()) {
            log.warn("escola: tag <visita_escola> com campos faltando p/ conversa {} — visita não criada", conversationId);
            return Optional.empty();
        }

        LocalDate visitDate;
        try {
            visitDate = LocalDate.parse(rawDate);
        } catch (RuntimeException e) {
            log.warn("escola: tag <visita_escola> com visit_date inválida p/ conversa {} — visita não criada", conversationId);
            return Optional.empty();
        }

        Integer numPeople = root.has("num_people") && root.path("num_people").isInt()
            ? root.path("num_people").asInt() : null;

        UUID studentId = null;
        String rawStudent = root.path("student_id").asText(null);
        if (rawStudent != null && !rawStudent.isBlank()) {
            try {
                studentId = UUID.fromString(rawStudent);
            } catch (RuntimeException e) {
                log.warn("escola: tag <visita_escola> com student_id inválido p/ conversa {} — visita não criada", conversationId);
                return Optional.empty();
            }
        }

        String visitorName = contactRepository.findNameByConversationId(conversationId)
            .filter(n -> n != null && !n.isBlank())
            .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Visitante"));
        String visitorPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            EscolaVisit created = visitService.create(companyId, conversationId, contactId, studentId,
                visitorName, visitorPhone, visitDate, period, numPeople, notes);
            log.info("escola: visita {} agendada p/ conversa {} ({} {})", created.id(), conversationId, visitDate, period);
            return Optional.of(created);
        } catch (EscolaVisitService.PastDateException e) {
            log.warn("escola: <visita_escola> com data no passado p/ conversa {} — visita não criada", conversationId);
            return Optional.empty();
        } catch (EscolaVisitService.InvalidPeriodException e) {
            log.warn("escola: <visita_escola> com período inválido p/ conversa {} — visita não criada", conversationId);
            return Optional.empty();
        } catch (EscolaVisitService.StudentNotFoundException e) {
            log.warn("escola: <visita_escola> com student_id inexistente p/ conversa {} — visita não criada", conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("escola: falha ao agendar visita p/ conversa {} ({}) — mensagem segue sem visita",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
