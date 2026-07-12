package com.meada.profiles.nutri.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.nutri.patients.NutriPatient;
import com.meada.profiles.nutri.plans.NutriPlan;
import com.meada.profiles.nutri.plans.NutriPlanService;
import com.meada.profiles.nutri.patients.NutriPatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <entrega_plano>{...}</entrega_plano>} da resposta da IA e ENTREGA o plano
 * alimentar ativo do paciente (camada 8.0) — padrão NOVO de ENTREGA READ-ONLY.
 *
 * <p>Diferente dos confirm handlers que CRIAM ou MUTAM: aqui a IA NUNCA gera o conteúdo. O body
 * entregue é o TEXTO EXATO do profissional, gravado no painel ({@link NutriPlanService} é o único
 * caminho de escrita), enviado VERBATIM ao paciente — sem reescrita, sem geração pela IA. A tag só
 * referencia qual paciente; o backend resolve o plano ativo e dispara o envio.
 *
 * <p>BARREIRA DE SEGURANÇA: o plano só é entregue se o {@code contactId} do paciente coincidir com o
 * contato DA PRÓPRIA CONVERSA. Isso impede que a IA, induzida por um patient_id de outra pessoa,
 * vaze o plano alimentar de um paciente para o contato errado. Sem plano ativo → {@link
 * Optional#empty()} (a IA foi instruída a oferecer agendamento). Qualquer falha → empty + warn.
 */
@Component
public class EntregaPlanoHandler {

    private static final Logger log = LoggerFactory.getLogger(EntregaPlanoHandler.class);

    private static final Pattern TAG = Pattern.compile("<entrega_plano>\\s*(\\{.*?\\})\\s*</entrega_plano>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final NutriPatientService patientService;
    private final NutriPlanService planService;
    private final NutriAppointmentNotifier notifier;

    public EntregaPlanoHandler(ObjectMapper objectMapper, NutriPatientService patientService,
                               NutriPlanService planService, NutriAppointmentNotifier notifier) {
        this.objectMapper = objectMapper;
        this.patientService = patientService;
        this.planService = planService;
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
     * Extrai a tag e entrega o plano ativo do paciente. Devolve o body entregue em caso de sucesso.
     * {@link Optional#empty()} quando: não há tag, JSON inválido, patient_id faltando/inválido, paciente
     * inexistente, paciente de OUTRO contato (barreira de segurança), sem plano ativo, ou o envio falha.
     * O body é o texto VERBATIM do profissional — nunca passa por geração da IA.
     */
    public Optional<String> parseAndDeliver(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("nutri: tag <entrega_plano> com JSON inválido p/ conversa {} ({}) — não entregue",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawPatient = root.path("patient_id").asText(null);
        if (rawPatient == null || rawPatient.isBlank()) {
            log.warn("nutri: tag <entrega_plano> sem patient_id p/ conversa {} — não entregue", conversationId);
            return Optional.empty();
        }
        UUID patientId;
        try {
            patientId = UUID.fromString(rawPatient);
        } catch (RuntimeException e) {
            log.warn("nutri: <entrega_plano> com patient_id inválido p/ conversa {} — não entregue", conversationId);
            return Optional.empty();
        }

        // Best-effort DE VERDADE: lookups/envio dentro do catch-all — uma RuntimeException
        // transitória (banco, rede) NÃO pode derrubar o envio da resposta da IA ao cliente.
        try {
            Optional<NutriPatient> patient = patientService.get(companyId, patientId);
            if (patient.isEmpty()) {
                log.warn("nutri: <entrega_plano> referencia paciente inexistente {} p/ conversa {} — não entregue",
                    patientId, conversationId);
                return Optional.empty();
            }

            // BARREIRA DE SEGURANÇA: o plano só sai para o contato dono do paciente.
            if (!java.util.Objects.equals(patient.get().contactId(), contactId)) {
                log.warn("nutri: <entrega_plano> de paciente de outro contato (paciente {} contato {} ≠ conversa {}) — bloqueado, não entregue",
                    patientId, patient.get().contactId(), contactId);
                return Optional.empty();
            }

            Optional<NutriPlan> plan = planService.getActiveByPatient(companyId, patientId);
            if (plan.isEmpty()) {
                log.warn("nutri: <entrega_plano> sem plano ativo p/ paciente {} (conversa {}) — não entregue",
                    patientId, conversationId);
                return Optional.empty();
            }

            String body = plan.get().body();
            if (notifier.sendText(companyId, conversationId, body)) {
                log.info("nutri: plano {} entregue VERBATIM p/ conversa {} (paciente {})", plan.get().id(), conversationId, patientId);
                return Optional.of(body);
            }
            log.warn("nutri: <entrega_plano> falhou ao enviar o plano p/ conversa {} (paciente {}) — não entregue",
                conversationId, patientId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("nutri: <entrega_plano> falhou inesperadamente p/ conversa {} ({}) — não entregue (best-effort)",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
