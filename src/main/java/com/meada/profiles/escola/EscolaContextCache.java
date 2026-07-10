package com.meada.profiles.escola;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.escola.classes.EscolaClass;
import com.meada.profiles.escola.classes.EscolaClassRepository;
import com.meada.profiles.escola.config.EscolaConfig;
import com.meada.profiles.escola.config.EscolaConfigRepository;
import com.meada.profiles.escola.enrollments.EscolaEnrollment;
import com.meada.profiles.escola.enrollments.EscolaEnrollmentRepository;
import com.meada.profiles.escola.students.EscolaStudent;
import com.meada.profiles.escola.students.EscolaStudentRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do EscolaBot (camada 8.19).
 *
 * <p>TTL 60s (turmas/vagas mudam lento). Keyed por {@code (companyId, contactId)}. Conteúdo: turmas
 * ativas COM vagas restantes (capacity - matrículas ativas/suspensas) + série + turno + mensalidade;
 * os alunos do responsável (filhos), com a turma atual de cada um; horário de funcionamento; e as
 * INSTRUÇÕES das 2 tags (matrícula com 2 modos + visita) com a TRAVA. Os services chamam
 * {@link #invalidate} ao mutar.
 */
@Component
public class EscolaContextCache {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final EscolaConfigRepository configRepository;
    private final EscolaClassRepository classRepository;
    private final EscolaStudentRepository studentRepository;
    private final EscolaEnrollmentRepository enrollmentRepository;
    private final Cache<String, String> cache;

    public EscolaContextCache(EscolaConfigRepository configRepository,
                              EscolaClassRepository classRepository,
                              EscolaStudentRepository studentRepository,
                              EscolaEnrollmentRepository enrollmentRepository) {
        this.configRepository = configRepository;
        this.classRepository = classRepository;
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de config/turma/aluno/matrícula/visita). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        EscolaConfig config = configRepository.findByCompany(companyId);
        List<EscolaClass> classes = classRepository.listByCompany(companyId, true, null);

        StringBuilder sb = new StringBuilder();

        // --- HORÁRIO DE FUNCIONAMENTO ---
        sb.append("HORÁRIO DE FUNCIONAMENTO: ").append(TIME_FMT.format(config.opensAt()))
            .append(" às ").append(TIME_FMT.format(config.closesAt())).append(".\n\n");

        // --- TURMAS (com vagas restantes) ---
        if (classes.isEmpty()) {
            sb.append("TURMAS: (nenhuma turma ativa no momento.)\n\n");
        } else {
            sb.append("TURMAS DISPONÍVEIS (use o class_id EXATO; só ofereça turmas com vaga):\n");
            for (EscolaClass c : classes) {
                int remaining = Math.max(0, c.capacity() - classRepository.countActiveEnrollments(c.id()));
                sb.append("- ").append(c.id()).append(" · \"").append(c.name()).append("\": ")
                    .append(c.grade()).append(", turno ").append(shiftLabel(c.shift()))
                    .append(", R$ ").append(formatBrl(c.monthlyCents())).append("/mês, ")
                    .append(remaining).append("/").append(c.capacity()).append(" vagas");
                if (c.year() != null) {
                    sb.append(" (").append(c.year()).append(")");
                }
                if (c.description() != null && !c.description().isBlank()) {
                    sb.append(" — ").append(c.description().strip());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- ALUNOS DO RESPONSÁVEL (filhos) ---
        if (contactId != null) {
            List<EscolaStudent> students = studentRepository.listByContact(companyId, contactId, true);
            if (!students.isEmpty()) {
                // turma atual de cada filho (matrícula ativa).
                Map<UUID, String> currentClassByStudent = new HashMap<>();
                for (EscolaEnrollment e : enrollmentRepository.listActiveByContact(companyId, contactId)) {
                    currentClassByStudent.putIfAbsent(e.studentId(), e.className());
                }
                sb.append("ALUNOS DESTE RESPONSÁVEL (use o student_id EXATO p/ matricular um já cadastrado):\n");
                for (EscolaStudent s : students) {
                    sb.append("- ").append(s.id()).append(" · ").append(s.name());
                    String currentClass = currentClassByStudent.get(s.id());
                    if (currentClass != null) {
                        sb.append(" (já matriculado na turma ").append(currentClass).append(")");
                    } else if (s.intendedGrade() != null && !s.intendedGrade().isBlank()) {
                        sb.append(" (série pretendida: ").append(s.intendedGrade().strip()).append(")");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("ALUNOS DESTE RESPONSÁVEL: (nenhum aluno cadastrado ainda — se ele quiser matricular, "
                    + "use o modo new_student na tag.)\n\n");
            }
        }

        // --- INSTRUÇÕES + TRAVA ---
        sb.append("INSTRUÇÕES (TRAVA — siga à risca):\n")
            .append("LISTA DE ESPERA: se a família quiser uma turma SEM vaga, ofereça a lista de "
                + "espera — emita a tag <matricula_escola> normalmente com o class_id da turma cheia "
                + "(o sistema registra o interesse na fila e a secretaria avisa quando abrir vaga). "
                + "Deixe claro que é lista de espera, SEM promessa de vaga nem de prazo.\n")
            .append("Você é o atendente da escola. NUNCA prometa uma vaga que não esteja confirmada na lista "
                + "(só ofereça turma com vaga > 0 — exceto pra registrar LISTA DE ESPERA, acima). "
                + "NUNCA defina, invente ou negocie mensalidade, desconto ou "
                + "bolsa — informe APENAS o valor que consta na turma; questões de valor/desconto são com a "
                + "secretaria. NUNCA dê parecer pedagógico, opinião sobre desenvolvimento da criança ou "
                + "recomendação educacional. NUNCA invente turma, série, turno ou nome de professor. Confirme a "
                + "turma + o aluno ANTES de emitir a tag.\n\n")
            .append("MATRÍCULA — quando o responsável confirmar, sua ÚLTIMA mensagem deve TERMINAR com a tag "
                + "(em uma linha própria, sem markdown). DOIS MODOS de aluno:\n")
            .append("  • aluno já cadastrado: <matricula_escola>{\"class_id\":\"UUID\",\"student_id\":\"UUID\","
                + "\"notes\":\"...\"}</matricula_escola>\n")
            .append("  • aluno novo (cadastra e matricula): <matricula_escola>{\"class_id\":\"UUID\","
                + "\"new_student\":{\"name\":\"...\",\"birth_date\":\"YYYY-MM-DD\",\"intended_grade\":\"...\"},"
                + "\"notes\":\"...\"}</matricula_escola>\n\n")
            .append("VISITA — quando o responsável quiser conhecer a escola, agende uma visita (dia + período "
                + "manha|tarde). Confirme data e período ANTES de emitir a tag:\n")
            .append("  <visita_escola>{\"visit_date\":\"YYYY-MM-DD\",\"period\":\"manha|tarde\","
                + "\"num_people\":N,\"student_id\":\"UUID-ou-omitir\",\"notes\":\"...\"}</visita_escola>\n")
            .append("Use ids EXATOS das listas. Só emita a tag na confirmação final.\n\n");

        return sb.toString();
    }

    private static String shiftLabel(String shift) {
        return switch (shift) {
            case "manha" -> "manhã";
            case "tarde" -> "tarde";
            case "integral" -> "integral";
            default -> shift;
        };
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
