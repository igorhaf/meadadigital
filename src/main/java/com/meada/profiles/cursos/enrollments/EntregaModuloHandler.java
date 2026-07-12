package com.meada.profiles.cursos.enrollments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.cursos.modules.CursosModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <entrega_modulo>{...}</entrega_modulo>} da resposta da IA e ENTREGA o PRÓXIMO
 * módulo da matrícula (camada 8.20 / perfil cursos, ESCAPADA 2) — padrão de ENTREGA READ-ONLY (clone
 * do EntregaMaterialHandler do fotografia / EntregaPlanoHandler do nutri).
 *
 * <p>Diferente dos confirm handlers que CRIAM ou MUTAM: aqui a IA NUNCA gera o conteúdo. O texto
 * entregue é o {@code content} do PRÓXIMO módulo não-concluído da matrícula (1º por position que NÃO
 * está em cursos_enrollment_progress), enviado VERBATIM ao aluno — sem reescrita, sem geração. Depois
 * de entregar, REGISTRA o progresso desse módulo (a próxima chamada entrega o módulo seguinte).
 *
 * <p>BARREIRA DE SEGURANÇA: o módulo só é entregue se o {@code contactId} da matrícula coincidir com o
 * contato DA PRÓPRIA CONVERSA. Isso impede que a IA, induzida por um enrollment_id de outra pessoa,
 * vaze o material para o contato errado. Trilha concluída (sem próximo) / contato diferente /
 * matrícula inexistente → {@code false}. Qualquer falha → {@code false} + warn.
 */
@Component
public class EntregaModuloHandler {

    private static final Logger log = LoggerFactory.getLogger(EntregaModuloHandler.class);

    private static final Pattern TAG = Pattern.compile("<entrega_modulo>\\s*(\\{.*?\\})\\s*</entrega_modulo>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final CursosEnrollmentRepository enrollmentRepository;
    private final CursosEnrollmentNotifier notifier;

    public EntregaModuloHandler(ObjectMapper objectMapper, CursosEnrollmentRepository enrollmentRepository,
                                CursosEnrollmentNotifier notifier) {
        this.objectMapper = objectMapper;
        this.enrollmentRepository = enrollmentRepository;
        this.notifier = notifier;
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
     * Extrai a tag e entrega o content do próximo módulo. Devolve {@code true} se entregou. {@code
     * false} quando: não há tag, JSON inválido, enrollment_id faltando/inválido, matrícula inexistente,
     * matrícula de OUTRO contato (barreira), trilha concluída (sem próximo módulo), ou o envio falha. O
     * texto é o {@code content} VERBATIM — nunca passa por geração da IA. Em sucesso, registra o
     * progresso do módulo entregue.
     */
    public boolean parseAndDeliver(UUID companyId, UUID conversationId, UUID contactId, String aiResponseText) {
        if (aiResponseText == null) {
            return false;
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return false;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("cursos: tag <entrega_modulo> com JSON inválido p/ conversa {} ({}) — não entregue",
                conversationId, e.getMessage());
            return false;
        }

        String rawEnrollment = root.path("enrollment_id").asText(null);
        if (rawEnrollment == null || rawEnrollment.isBlank()) {
            log.warn("cursos: tag <entrega_modulo> sem enrollment_id p/ conversa {} — não entregue", conversationId);
            return false;
        }
        UUID enrollmentId;
        try {
            enrollmentId = UUID.fromString(rawEnrollment);
        } catch (RuntimeException e) {
            log.warn("cursos: <entrega_modulo> com enrollment_id inválido p/ conversa {} — não entregue", conversationId);
            return false;
        }

        // Best-effort DE VERDADE: lookups/envio dentro do catch-all — uma RuntimeException
        // transitória (banco, rede) NÃO pode derrubar o envio da resposta da IA ao cliente.
        try {
            Optional<CursosEnrollment> enrollment = enrollmentRepository.findById(companyId, enrollmentId);
            if (enrollment.isEmpty()) {
                log.warn("cursos: <entrega_modulo> referencia matrícula inexistente {} p/ conversa {} — não entregue",
                    enrollmentId, conversationId);
                return false;
            }

            // BARREIRA DE SEGURANÇA: o módulo só sai para o contato dono da matrícula.
            if (!Objects.equals(enrollment.get().contactId(), contactId)) {
                log.warn("cursos: <entrega_modulo> de matrícula de outro contato (matrícula {} contato {} ≠ conversa {}) — bloqueado",
                    enrollmentId, enrollment.get().contactId(), contactId);
                return false;
            }

            Optional<CursosModule> next = enrollmentRepository.findNextModule(enrollmentId);
            if (next.isEmpty()) {
                log.info("cursos: <entrega_modulo> matrícula {} sem próximo módulo (trilha concluída) p/ conversa {} — nada a entregar",
                    enrollmentId, conversationId);
                return false;
            }

            String content = next.get().content();
            if (content == null || content.isBlank()) {
                log.warn("cursos: <entrega_modulo> próximo módulo {} sem content p/ conversa {} — não entregue",
                    next.get().id(), conversationId);
                return false;
            }

            if (notifier.sendText(companyId, conversationId, content)) {
                // recordProgress falhar APÓS o envio não pode estourar: módulo já entregue —
                // o progresso fica pendente com warn e a resposta ao cliente segue.
                try {
                    enrollmentRepository.recordProgress(enrollmentId, next.get().id());
                } catch (RuntimeException e) {
                    log.warn("cursos: módulo {} entregue mas recordProgress falhou p/ matrícula {} ({}) — progresso pendente",
                        next.get().id(), enrollmentId, e.getMessage());
                }
                log.info("cursos: módulo {} (matrícula {}) entregue VERBATIM + progresso registrado p/ conversa {}",
                    next.get().id(), enrollmentId, conversationId);
                return true;
            }
            log.warn("cursos: <entrega_modulo> falhou ao enviar o módulo {} p/ conversa {} (matrícula {}) — não entregue",
                next.get().id(), conversationId, enrollmentId);
            return false;
        } catch (RuntimeException e) {
            log.warn("cursos: <entrega_modulo> falhou inesperadamente p/ conversa {} ({}) — não entregue (best-effort)",
                conversationId, e.getMessage());
            return false;
        }
    }
}
