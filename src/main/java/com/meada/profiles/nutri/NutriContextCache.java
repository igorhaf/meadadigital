package com.meada.profiles.nutri;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.nutri.appointments.NutriAppointment;
import com.meada.profiles.nutri.appointments.NutriAppointmentRepository;
import com.meada.profiles.nutri.config.NutriConfig;
import com.meada.profiles.nutri.config.NutriConfigRepository;
import com.meada.profiles.nutri.patients.NutriPatient;
import com.meada.profiles.nutri.patients.NutriPatientRepository;
import com.meada.profiles.nutri.plans.NutriPlanRepository;
import com.meada.profiles.nutri.professionals.NutriProfessional;
import com.meada.profiles.nutri.professionals.NutriProfessionalRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do NutriBot (camada 8.0). TTL 20s. Keyed
 * por {@code (companyId, contactId)}. Conteúdo:
 * <ul>
 *   <li>profissionais ativos (id + nome) — pra IA referenciar professional_id na consulta;
 *   <li>pacientes do contato (id + objetivo + SE tem plano ativo + última consulta) — modo
 *       patient_id da tag de agendamento e indicação de quem tem plano pra entregar;
 *   <li>slots livres por profissional (próximos 7 dias).
 * </ul>
 * + instruções/trava clínica e as 2 tags ({@code <consulta_nutri>} 2 modos e {@code <entrega_plano>}).
 *
 * <p>IMPORTANTE (segurança): NÃO injeta o BODY do plano no contexto — só a indicação de que existe um
 * plano ativo. O body só é lido no momento da entrega, pelo EntregaPlanoHandler.
 */
@Component
public class NutriContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 7;
    private static final int SLOT_GRANULARITY_MIN = 30;
    private static final int MAX_SLOTS_PER_PROF_DAY = 6;
    private static final int MAX_PATIENTS = 10;

    private final NutriProfessionalRepository professionalRepository;
    private final NutriPatientRepository patientRepository;
    private final NutriPlanRepository planRepository;
    private final NutriAppointmentRepository appointmentRepository;
    private final NutriConfigRepository configRepository;
    private final Cache<String, String> cache;

    public NutriContextCache(NutriProfessionalRepository professionalRepository,
                             NutriPatientRepository patientRepository,
                             NutriPlanRepository planRepository,
                             NutriAppointmentRepository appointmentRepository,
                             NutriConfigRepository configRepository) {
        this.professionalRepository = professionalRepository;
        this.patientRepository = patientRepository;
        this.planRepository = planRepository;
        this.appointmentRepository = appointmentRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(20))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de prof/paciente/plano/consulta/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        NutriConfig config = configRepository.findByCompany(companyId);
        List<NutriProfessional> pros = professionalRepository.listByCompany(companyId, true);

        StringBuilder sb = new StringBuilder();

        // --- PROFISSIONAIS ---
        if (pros.isEmpty()) {
            sb.append("NUTRICIONISTAS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("NUTRICIONISTAS (use o professional_id EXATO):\n");
            for (NutriProfessional p : pros) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name());
                if (p.specialty() != null && !p.specialty().isBlank()) {
                    sb.append(" (").append(p.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- PACIENTES DO CONTATO ---
        if (contactId != null) {
            List<NutriPatient> patients = patientRepository.listByContact(companyId, contactId, true);
            if (!patients.isEmpty()) {
                sb.append("PACIENTES DESTE CONTATO (use o patient_id EXATO; ofereça os já cadastrados):\n");
                int count = 0;
                for (NutriPatient pt : patients) {
                    if (count++ >= MAX_PATIENTS) break;
                    sb.append("- ").append(pt.id()).append(" · ").append(pt.name());
                    if (pt.goal() != null && !pt.goal().isBlank()) {
                        sb.append(" (objetivo: ").append(pt.goal()).append(")");
                    }
                    boolean hasPlan = planRepository.findActiveByPatient(companyId, pt.id()).isPresent();
                    sb.append(hasPlan ? " — TEM plano ativo disponível para entrega" : " — sem plano ativo");
                    List<NutriAppointment> last = appointmentRepository.listByPatient(companyId, pt.id(), 1);
                    if (!last.isEmpty()) {
                        ZonedDateTime z = last.get(0).startAt().atZone(TENANT_ZONE);
                        sb.append("; última consulta ").append(DATE_FMT.format(z));
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("CONTATO SEM PACIENTES cadastrados: peça nome e objetivo (texto livre, ex.: "
                    + "emagrecimento/ganho de massa/manutenção) para cadastrar o paciente junto com a consulta.\n\n");
            }

            // --- CONSULTAS FUTURAS (onda 1, backlog #1 — a IA captura confirmação/cancelamento) ---
            List<NutriAppointment> upcoming =
                appointmentRepository.listUpcomingByContact(companyId, contactId, 5);
            if (!upcoming.isEmpty()) {
                sb.append("CONSULTAS FUTURAS DESTE CONTATO (use o id EXATO na tag de confirmação):\n");
                for (NutriAppointment a : upcoming) {
                    ZonedDateTime z = a.startAt().atZone(TENANT_ZONE);
                    sb.append("- ").append(a.id()).append(" · ").append(a.patientName())
                        .append(": ").append(DATE_FMT.format(z))
                        .append(" com ").append(a.professionalName())
                        .append(" · status ").append(a.status()).append("\n");
                }
                sb.append("Quando o contato CONFIRMAR uma consulta futura (ex.: responder SIM ao lembrete) "
                    + "ou pedir para DESMARCAR, sua ÚLTIMA mensagem deve TERMINAR com a tag (linha própria):\n"
                    + "<confirmacao_nutri>{\"appointment_id\":\"UUID\",\"decisao\":\"confirmado|cancelado\"}"
                    + "</confirmacao_nutri>\n"
                    + "Você só REFLETE a decisão do contato — NUNCA confirme ou cancele sem ele pedir, "
                    + "e continue SEM falar de dieta/plano (trava clínica).\n\n");
            }
        } else {
            sb.append("CONTATO NÃO IDENTIFICADO pelo telefone. Peça os dados para cadastrar o paciente.\n\n");
        }

        // --- SLOTS LIVRES POR PROFISSIONAL ---
        sb.append(buildSlotsSegment(companyId, config, pros));

        // --- INSTRUÇÕES + TRAVA CLÍNICA + TAGS ---
        sb.append("INSTRUÇÕES E LIMITES (LEIA COM ATENÇÃO):\n")
            .append("Você AGENDA consultas e ENTREGA o plano alimentar que o nutricionista já gravou — nada além "
                + "disso. NUNCA crie, calcule, monte, adapte, resuma ou ajuste plano alimentar. NUNCA dê caloria, "
                + "macro, porção ou qualquer número nutricional. NUNCA responda 'posso comer X?', 'quantas calorias "
                + "tem Y?' ou 'isso engorda?'. NUNCA opine sobre patologia, suplementação, emagrecimento, ganho de "
                + "massa ou restrição. Para QUALQUER pergunta nutricional, oriente a agendar consulta/retorno.\n")
            .append("GUARDA: se o paciente sinalizar restrição alimentar intensa, compulsão, purga, contagem "
                + "obsessiva, peso-meta extremo ou sofrimento com comida/corpo, NÃO dê números, NÃO valide a conduta, "
                + "acolha sem reforçar e encaminhe ao nutricionista (e, se houver sinal de risco, sugira buscar apoio "
                + "profissional de saúde). NUNCA forneça técnica de restrição/compensação.\n")
            .append("Para AGENDAR, termine com UMA das tags (linha própria, sem markdown):\n")
            .append("Paciente JÁ cadastrado:\n")
            .append("<consulta_nutri>{\"professional_id\":\"UUID\",\"patient_id\":\"UUID\","
                + "\"appointment_type\":\"primeira|retorno|avaliacao\",\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\","
                + "\"notes\":\"...\"}</consulta_nutri>\n")
            .append("Paciente NOVO (cadastra junto):\n")
            .append("<consulta_nutri>{\"professional_id\":\"UUID\",\"new_patient\":{\"name\":\"...\",\"goal\":\"...\"},"
                + "\"appointment_type\":\"primeira\",\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\"}</consulta_nutri>\n")
            .append("Quando o paciente PEDIR o plano dele e houver plano ativo, termine com:\n")
            .append("<entrega_plano>{\"patient_id\":\"UUID\"}</entrega_plano>\n")
            .append("O texto do plano é enviado EXATAMENTE como o nutricionista gravou — você não o reescreve nem "
                + "comenta. Se o paciente não tem plano ativo, ofereça agendar uma consulta. Use ids EXATOS.\n\n");

        return sb.toString();
    }

    private String buildSlotsSegment(UUID companyId, NutriConfig config, List<NutriProfessional> pros) {
        StringBuilder sb = new StringBuilder("HORÁRIOS LIVRES (próximos ").append(CONTEXT_DAYS).append(" dias, por profissional):\n");
        if (pros.isEmpty()) {
            sb.append("(sem profissionais ativos.)\n\n");
            return sb.toString();
        }
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);

        for (NutriProfessional p : pros) {
            List<NutriAppointment> active = appointmentRepository.listActiveByProfessional(companyId, p.id(), now, until);
            List<String> dayChunks = new ArrayList<>();
            for (int d = 0; d < CONTEXT_DAYS; d++) {
                LocalDate day = startDay.plusDays(d).toLocalDate();
                List<String> free = new ArrayList<>();
                LocalTime t = config.opensAt();
                while (t.plusMinutes(SLOT_GRANULARITY_MIN).compareTo(config.closesAt()) <= 0
                        && free.size() < MAX_SLOTS_PER_PROF_DAY) {
                    ZonedDateTime slotStart = day.atTime(t).atZone(TENANT_ZONE);
                    ZonedDateTime slotEnd = slotStart.plusMinutes(SLOT_GRANULARITY_MIN);
                    boolean inFuture = slotStart.toInstant().isAfter(now);
                    boolean occupied = active.stream().anyMatch(a ->
                        !(a.endAt().compareTo(slotStart.toInstant()) <= 0 || a.startAt().compareTo(slotEnd.toInstant()) >= 0));
                    if (inFuture && !occupied) {
                        free.add(TIME_FMT.format(t));
                    }
                    t = t.plusMinutes(SLOT_GRANULARITY_MIN);
                }
                if (!free.isEmpty()) {
                    dayChunks.add(DATE_FMT.format(day.atStartOfDay(TENANT_ZONE)) + " " + String.join(", ", free));
                }
            }
            if (!dayChunks.isEmpty()) {
                sb.append("- ").append(p.name()).append(": ").append(String.join(" | ", dayChunks)).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }
}
