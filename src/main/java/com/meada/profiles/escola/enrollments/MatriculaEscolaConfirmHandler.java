package com.meada.profiles.escola.enrollments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.escola.students.EscolaStudent;
import com.meada.profiles.escola.students.EscolaStudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <matricula_escola>{...}</matricula_escola>} da resposta da IA e cria a
 * matrícula (camada 8.19). Espelho do MatriculaConfirmHandler da academia, com a NOVIDADE dos 2
 * MODOS de aluno (espelho do new_animal do pet):
 *
 * <ul>
 *   <li><b>student_id</b> existente: matricula direto um aluno já cadastrado do responsável.</li>
 *   <li><b>new_student</b> {name, birth_date?, intended_grade?}: cadastra o aluno (sub-entidade do
 *       contato/responsável da conversa) e, em seguida, matricula — tudo no mesmo turno.</li>
 * </ul>
 *
 * <p>Resolve o responsável (contato) da conversa. O service valida turma/aluno/anti-dupla/vaga;
 * qualquer falha → {@link Optional#empty()} + warn. Tag namespace exclusivo {@code <matricula_escola>}.
 */
@Component
public class MatriculaEscolaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(MatriculaEscolaConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<matricula_escola>\\s*(\\{.*?\\})\\s*</matricula_escola>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final EscolaStudentService studentService;
    private final EscolaEnrollmentService enrollmentService;
    private final com.meada.profiles.escola.waitlist.EscolaWaitlistService waitlistService;

    public MatriculaEscolaConfirmHandler(ObjectMapper objectMapper, EscolaStudentService studentService,
                                         EscolaEnrollmentService enrollmentService,
                                        com.meada.profiles.escola.waitlist.EscolaWaitlistService waitlistService) {
        this.objectMapper = objectMapper;
        this.studentService = studentService;
        this.enrollmentService = enrollmentService;
        this.waitlistService = waitlistService;
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
     * Extrai a tag e cria a matrícula. Resolve o aluno por um dos 2 modos. {@link Optional#empty()}
     * quando: não há tag, JSON inválido, campos faltando, aluno/cadastro inválido, ou a criação da
     * matrícula falha (turma inválida/inativa, anti-dupla, sem vaga). O {@code contactId}
     * (responsável) vem da conversa.
     */
    public Optional<EscolaEnrollment> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("escola: tag <matricula_escola> com JSON inválido p/ conversa {} ({}) — matrícula não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawClass = root.path("class_id").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawClass == null || rawClass.isBlank()) {
            log.warn("escola: tag <matricula_escola> sem class_id p/ conversa {} — matrícula não criada", conversationId);
            return Optional.empty();
        }

        UUID classId;
        try {
            classId = UUID.fromString(rawClass);
        } catch (RuntimeException e) {
            log.warn("escola: tag <matricula_escola> com class_id inválido p/ conversa {} — matrícula não criada",
                conversationId);
            return Optional.empty();
        }

        // Resolve o aluno: modo student_id (existente) OU new_student (cadastra e matricula).
        UUID studentId;
        try {
            studentId = resolveStudent(companyId, contactId, conversationId, root);
        } catch (ResolveStudentException e) {
            return Optional.empty();
        }
        if (studentId == null) {
            return Optional.empty();
        }

        try {
            EscolaEnrollment created = enrollmentService.create(companyId, classId, studentId, contactId,
                conversationId, notes);
            log.info("escola: matrícula {} criada p/ conversa {} (turma {}, aluno {})",
                created.id(), conversationId, classId, studentId);
            return Optional.of(created);
        } catch (EscolaEnrollmentService.ClassFullException e) {
            // Onda 1 (backlog #1): turma cheia ENFILEIRA na lista de espera em vez de descartar o
            // lead (aviso de vaga é ação HUMANA no painel — a IA nunca promete vaga).
            boolean queued = waitlistService.enqueue(companyId, e.classId(), contactId, studentId);
            log.warn("escola: <matricula_escola> com turma lotada ({}) p/ conversa {} — {}",
                e.className(), conversationId, queued ? "lead na lista de espera" : "não criada");
            return Optional.empty();
        } catch (EscolaEnrollmentService.AlreadyActiveException e) {
            log.warn("escola: <matricula_escola> p/ aluno já matriculado nessa turma (conversa {}) — não criada",
                conversationId);
            return Optional.empty();
        } catch (EscolaEnrollmentService.ClassNotFoundException
                 | EscolaEnrollmentService.ClassInactiveException
                 | EscolaEnrollmentService.StudentNotFoundException
                 | EscolaEnrollmentService.StudentInactiveException e) {
            log.warn("escola: <matricula_escola> com turma/aluno inválido ou inativo p/ conversa {} — não criada",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("escola: falha ao criar matrícula p/ conversa {} ({}) — mensagem segue sem matrícula",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static class ResolveStudentException extends RuntimeException {}

    /**
     * Modo student_id: valida UUID e usa direto (a criação revalida que é do tenant). Modo
     * new_student: cadastra o aluno como sub-entidade do responsável (contato da conversa) e retorna
     * o id criado. Sem contato resolvido → não dá pra cadastrar. Dados inválidos → empty.
     */
    private UUID resolveStudent(UUID companyId, UUID contactId, UUID conversationId, JsonNode root) {
        String rawStudent = root.path("student_id").asText(null);
        if (rawStudent != null && !rawStudent.isBlank()) {
            try {
                return UUID.fromString(rawStudent);
            } catch (RuntimeException e) {
                log.warn("escola: <matricula_escola> com student_id inválido p/ conversa {} — não criada", conversationId);
                throw new ResolveStudentException();
            }
        }

        JsonNode newStudent = root.path("new_student");
        if (newStudent.isMissingNode() || !newStudent.isObject()) {
            log.warn("escola: <matricula_escola> sem student_id nem new_student p/ conversa {} — não criada", conversationId);
            throw new ResolveStudentException();
        }
        if (contactId == null) {
            log.warn("escola: <matricula_escola> new_student sem responsável resolvido p/ conversa {} — não criada", conversationId);
            throw new ResolveStudentException();
        }
        String name = newStudent.path("name").asText(null);
        if (name == null || name.isBlank()) {
            log.warn("escola: <matricula_escola> new_student com nome faltando p/ conversa {} — não criada", conversationId);
            throw new ResolveStudentException();
        }
        LocalDate birthDate;
        try {
            String rawBirth = newStudent.path("birth_date").asText(null);
            birthDate = rawBirth == null || rawBirth.isBlank() ? null : LocalDate.parse(rawBirth);
        } catch (RuntimeException e) {
            log.warn("escola: <matricula_escola> new_student com birth_date inválida p/ conversa {} — não criada", conversationId);
            throw new ResolveStudentException();
        }
        String intendedGrade = newStudent.path("intended_grade").asText(null);
        try {
            // userId null: cadastro disparado pela IA (sem ator humano).
            EscolaStudent created = studentService.create(companyId, null, contactId, name, birthDate,
                intendedGrade, null);
            log.info("escola: aluno {} cadastrado pela IA p/ conversa {} (responsável {})",
                created.id(), conversationId, contactId);
            return created.id();
        } catch (EscolaStudentService.ContactNotFoundException e) {
            log.warn("escola: <matricula_escola> new_student com responsável inexistente p/ conversa {} — não criada", conversationId);
            throw new ResolveStudentException();
        } catch (RuntimeException e) {
            log.warn("escola: falha ao cadastrar new_student p/ conversa {} ({}) — não criada", conversationId, e.getMessage());
            throw new ResolveStudentException();
        }
    }
}
