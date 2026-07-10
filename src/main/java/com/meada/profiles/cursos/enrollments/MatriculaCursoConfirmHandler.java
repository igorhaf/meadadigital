package com.meada.profiles.cursos.enrollments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.messaging.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <matricula_curso>{...}</matricula_curso>} da resposta da IA e cria a matrícula
 * (camada 8.20 / perfil cursos). Clone do MatriculaConfirmHandler do academia (camada 7.7), em modo
 * ÚNICO: a matrícula é num só curso (sem class_ids). Namespace exclusivo {@code <matricula_curso>}.
 *
 * <p>Parse: course_id, student_name (opc), notes (opc). Resolve o contato da conversa; student_name
 * vem do JSON ou do contact. O service valida curso/anti-dupla; qualquer falha → {@link
 * Optional#empty()} + warn. Qualquer preço emitido pela IA é DESCARTADO (o snapshot vem do catálogo).
 */
@Component
public class MatriculaCursoConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(MatriculaCursoConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<matricula_curso>\\s*(\\{.*?\\})\\s*</matricula_curso>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final CursosEnrollmentService enrollmentService;

    public MatriculaCursoConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                        CursosEnrollmentService enrollmentService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.enrollmentService = enrollmentService;
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

    public Optional<CursosEnrollment> parseAndCreate(UUID companyId, UUID conversationId,
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
            log.warn("cursos: tag <matricula_curso> com JSON inválido p/ conversa {} ({}) — matrícula não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawCourse = root.path("course_id").asText(null);
        String studentNameJson = root.path("student_name").asText(null);
        String notes = root.path("notes").asText(null);
        String cupom = root.path("cupom").asText(null);
        if (rawCourse == null || rawCourse.isBlank()) {
            log.warn("cursos: tag <matricula_curso> sem course_id p/ conversa {} — matrícula não criada", conversationId);
            return Optional.empty();
        }

        UUID courseId;
        try {
            courseId = UUID.fromString(rawCourse);
        } catch (RuntimeException e) {
            log.warn("cursos: tag <matricula_curso> com course_id inválido p/ conversa {} — matrícula não criada",
                conversationId);
            return Optional.empty();
        }

        String studentName = studentNameJson != null && !studentNameJson.isBlank()
            ? studentNameJson.strip()
            : contactRepository.findNameByConversationId(conversationId)
                .filter(n -> n != null && !n.isBlank())
                .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Aluno"));
        String studentPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            CursosEnrollment created = enrollmentService.create(companyId, courseId, contactId,
                conversationId, studentName, studentPhone,
                cupom == null || cupom.isBlank() ? null : cupom.strip(), notes);
            log.info("cursos: matrícula {} criada p/ conversa {} (curso {})",
                created.id(), conversationId, courseId);
            return Optional.of(created);
        } catch (AlreadyEnrolledException e) {
            log.warn("cursos: <matricula_curso> p/ contato que já está matriculado no curso (conversa {}) — não criada",
                conversationId);
            return Optional.empty();
        } catch (CursosEnrollmentService.CourseNotFoundException
                 | CursosEnrollmentService.CourseInactiveException e) {
            log.warn("cursos: <matricula_curso> com curso inválido ou inativo p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("cursos: falha ao criar matrícula p/ conversa {} ({}) — mensagem segue sem matrícula",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
