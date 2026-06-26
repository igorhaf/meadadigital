package com.meada.whatsapp.profiles.dermatologia;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointment;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentRepository;
import com.meada.whatsapp.profiles.dermatologia.config.DermatologiaConfig;
import com.meada.whatsapp.profiles.dermatologia.config.DermatologiaConfigRepository;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatient;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatientRepository;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureType;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureTypeRepository;
import com.meada.whatsapp.profiles.dermatologia.professionals.DermatologiaProfessional;
import com.meada.whatsapp.profiles.dermatologia.professionals.DermatologiaProfessionalRepository;
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
 * Cache do bloco de contexto dinâmico injetado no prompt do DermatologiaBot (camada 8.11). TTL 30s.
 * Keyed por {@code (companyId, contactId)}. Conteúdo:
 * <ul>
 *   <li>dermatologistas ativos (id + nome) — pra IA referenciar professional_id na consulta;
 *   <li>tipos de atendimento ativos (id + nome + duração; INDICA se TEM preparo, sem despejar o
 *       texto) — pra IA referenciar procedure_type_id;
 *   <li>pacientes do contato (id + nome + última consulta) — modo patient_id da tag de agendamento;
 *   <li>slots livres por profissional (próximos 14 dias).
 * </ul>
 * + instruções/trava clínica e as 2 tags ({@code <consulta_derma>} 2 modos e {@code <entrega_preparo>}).
 *
 * <p>IMPORTANTE (segurança): NÃO injeta o TEXTO de preparo no contexto — só a indicação de que o tipo
 * tem preparo. O texto só é lido no momento da entrega, pelo EntregaPreparoHandler.
 */
@Component
public class DermatologiaContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 14;
    private static final int SLOT_GRANULARITY_MIN = 30;
    private static final int MAX_SLOTS_PER_PROF_DAY = 6;
    private static final int MAX_PATIENTS = 10;

    private final DermatologiaProfessionalRepository professionalRepository;
    private final DermatologiaProcedureTypeRepository procedureTypeRepository;
    private final DermatologiaPatientRepository patientRepository;
    private final DermatologiaAppointmentRepository appointmentRepository;
    private final DermatologiaConfigRepository configRepository;
    private final Cache<String, String> cache;

    public DermatologiaContextCache(DermatologiaProfessionalRepository professionalRepository,
                                    DermatologiaProcedureTypeRepository procedureTypeRepository,
                                    DermatologiaPatientRepository patientRepository,
                                    DermatologiaAppointmentRepository appointmentRepository,
                                    DermatologiaConfigRepository configRepository) {
        this.professionalRepository = professionalRepository;
        this.procedureTypeRepository = procedureTypeRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de prof/tipo/paciente/consulta/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        DermatologiaConfig config = configRepository.findByCompany(companyId);
        List<DermatologiaProfessional> pros = professionalRepository.listByCompany(companyId, true);
        List<DermatologiaProcedureType> types = procedureTypeRepository.listByCompany(companyId, true);

        StringBuilder sb = new StringBuilder();

        // --- DERMATOLOGISTAS ---
        if (pros.isEmpty()) {
            sb.append("DERMATOLOGISTAS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("DERMATOLOGISTAS (use o professional_id EXATO):\n");
            for (DermatologiaProfessional p : pros) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name());
                if (p.specialty() != null && !p.specialty().isBlank()) {
                    sb.append(" (").append(p.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- TIPOS DE ATENDIMENTO ---
        if (types.isEmpty()) {
            sb.append("TIPOS DE ATENDIMENTO: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("TIPOS DE ATENDIMENTO (use o procedure_type_id EXATO; a duração é fixa por tipo):\n");
            for (DermatologiaProcedureType t : types) {
                sb.append("- ").append(t.id()).append(" · ").append(t.name())
                    .append(" (").append(t.durationMinutes()).append(" min)");
                boolean hasPrep = t.prepInstructions() != null && !t.prepInstructions().isBlank();
                sb.append(hasPrep ? " — TEM nota de preparo (entregável)" : " — sem preparo");
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- PACIENTES DO CONTATO ---
        if (contactId != null) {
            List<DermatologiaPatient> patients = patientRepository.listByContact(companyId, contactId, true);
            if (!patients.isEmpty()) {
                sb.append("PACIENTES DESTE CONTATO (use o patient_id EXATO; ofereça os já cadastrados):\n");
                int count = 0;
                for (DermatologiaPatient pt : patients) {
                    if (count++ >= MAX_PATIENTS) break;
                    sb.append("- ").append(pt.id()).append(" · ").append(pt.name());
                    List<DermatologiaAppointment> last = appointmentRepository.listByPatient(companyId, pt.id(), 1);
                    if (!last.isEmpty()) {
                        ZonedDateTime z = last.get(0).startAt().atZone(TENANT_ZONE);
                        sb.append("; última consulta ").append(DATE_FMT.format(z));
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("CONTATO SEM PACIENTES cadastrados: peça o nome (e, se quiser, a data de nascimento) "
                    + "para cadastrar o paciente junto com a consulta.\n\n");
            }
        } else {
            sb.append("CONTATO NÃO IDENTIFICADO pelo telefone. Peça os dados para cadastrar o paciente.\n\n");
        }

        // --- SLOTS LIVRES POR PROFISSIONAL ---
        sb.append(buildSlotsSegment(companyId, config, pros));

        // --- INSTRUÇÕES + TRAVA CLÍNICA + TAGS ---
        sb.append("INSTRUÇÕES E LIMITES (LEIA COM ATENÇÃO):\n")
            .append("Você AGENDA consultas e ENTREGA a nota de preparo que o médico já gravou — nada além disso. "
                + "NUNCA diagnostique, NUNCA avalie lesão/mancha/pinta/foto, NUNCA recomende tratamento, medicação ou "
                + "procedimento, NUNCA opine 'é grave/é câncer/é normal'. Para QUALQUER dúvida clínica, oriente a agendar "
                + "uma consulta com o dermatologista.\n")
            .append("Para AGENDAR, termine com UMA das tags (linha própria, sem markdown):\n")
            .append("Paciente JÁ cadastrado:\n")
            .append("<consulta_derma>{\"professional_id\":\"UUID\",\"procedure_type_id\":\"UUID\","
                + "\"patient_id\":\"UUID\",\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\",\"notes\":\"...\"}</consulta_derma>\n")
            .append("Paciente NOVO (cadastra junto):\n")
            .append("<consulta_derma>{\"professional_id\":\"UUID\",\"procedure_type_id\":\"UUID\","
                + "\"new_patient\":{\"name\":\"...\",\"birth_date\":\"YYYY-MM-DD\"},\"date\":\"YYYY-MM-DD\","
                + "\"start_time\":\"HH:MM\"}</consulta_derma>\n")
            .append("Quando o paciente PEDIR as orientações de preparo de uma consulta dele que TENHA preparo, termine com:\n")
            .append("<entrega_preparo>{\"appointment_id\":\"UUID\"}</entrega_preparo>\n")
            .append("O texto de preparo é enviado EXATAMENTE como o médico gravou — você não o reescreve nem comenta. "
                + "Se a consulta não tem preparo, diga que não há orientação específica. Use ids EXATOS.\n\n");

        return sb.toString();
    }

    private String buildSlotsSegment(UUID companyId, DermatologiaConfig config, List<DermatologiaProfessional> pros) {
        StringBuilder sb = new StringBuilder("HORÁRIOS LIVRES (próximos ").append(CONTEXT_DAYS).append(" dias, por profissional):\n");
        if (pros.isEmpty()) {
            sb.append("(sem profissionais ativos.)\n\n");
            return sb.toString();
        }
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);

        for (DermatologiaProfessional p : pros) {
            List<DermatologiaAppointment> active = appointmentRepository.listActiveByProfessional(companyId, p.id(), now, until);
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
