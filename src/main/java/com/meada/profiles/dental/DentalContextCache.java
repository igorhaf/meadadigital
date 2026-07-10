package com.meada.profiles.dental;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.dental.appointments.DentalAppointment;
import com.meada.profiles.dental.appointments.DentalAppointmentRepository;
import com.meada.profiles.dental.config.DentalClinicConfig;
import com.meada.profiles.dental.config.DentalClinicConfigRepository;
import com.meada.profiles.dental.patients.DentalPatient;
import com.meada.profiles.dental.patients.DentalPatientRepository;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do DentalBot (camada 7.4).
 *
 * <p>A persona base do dental vem da SM-A (texto fixo em {@link com.meada.profiles.ProfilePromptContext}
 * — tom acolhedor, NUNCA diagnóstico). Esta classe acrescenta o contexto DINÂMICO: dados do
 * paciente identificado pelo telefone (nome + próximas consultas) + slots livres dos próximos 14
 * dias + instruções de agendamento (decisão 5).
 *
 * <p>TTL 30s — entre o restaurant (15s, agenda muito dinâmica) e o sushi/legal (60s). As consultas
 * mudam menos rápido que uma mesa de restaurante, mais rápido que um processo. Keyed por
 * {@code (companyId, contactId)}. Os services de paciente/consulta/config chamam {@link #invalidate}
 * (por company) ao mutar.
 */
@Component
public class DentalContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 14;
    private static final int MAX_SLOTS_PER_DAY = 8;   // limita a lista no prompt (resumida).

    private final DentalPatientRepository patientRepository;
    private final DentalAppointmentRepository appointmentRepository;
    private final DentalClinicConfigRepository configRepository;
    private final Cache<String, String> cache;

    public DentalContextCache(DentalPatientRepository patientRepository,
                              DentalAppointmentRepository appointmentRepository,
                              DentalClinicConfigRepository configRepository) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(1000)
            .build();
    }

    /** Bloco de contexto dinâmico p/ a conversa (resolve o paciente pelo contato). null-safe. */
    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de paciente/consulta/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        // O(n) sobre o cache — aceitável (TTL 30s, poucas entradas vivas). Limpa todas as keys
        // (todos os contatos) daquele company, pois a mutação pode afetar slots livres de qualquer um.
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        DentalClinicConfig config = configRepository.findByCompany(companyId);
        StringBuilder sb = new StringBuilder();

        // --- DADOS DO PACIENTE (se identificado pelo telefone) ---
        Optional<DentalPatient> patient = patientRepository.findByContactId(companyId, contactId);
        if (patient.isPresent()) {
            DentalPatient p = patient.get();
            sb.append("DADOS DO PACIENTE:\n");
            sb.append("Nome: ").append(p.name()).append("\n");
            List<DentalAppointment> upcoming =
                appointmentRepository.listByPatient(companyId, p.id(), true);
            if (upcoming.isEmpty()) {
                sb.append("Próximas consultas: nenhuma agendada.\n");
            } else {
                sb.append("Próximas consultas:\n");
                for (DentalAppointment a : upcoming) {
                    ZonedDateTime z = a.startAt().atZone(TENANT_ZONE);
                    sb.append("- ").append(DATE_FMT.format(z)).append(" ").append(TIME_FMT.format(z))
                        .append(": ").append(a.type())
                        .append(" (status: ").append(statusLabel(a.status())).append(")\n");
                }
            }
            sb.append("\n");
        } else {
            sb.append("PACIENTE NÃO IDENTIFICADO pelo telefone. Peça o nome completo p/ localizar o "
                + "cadastro ou registrar um novo, antes de agendar.\n\n");
        }

        // --- SLOTS LIVRES (próximos 14 dias, resumido) ---
        sb.append(buildSlotsSegment(companyId, config));

        // --- INSTRUÇÕES (decisão 5) ---
        sb.append("INSTRUÇÕES DE AGENDAMENTO:\n")
            .append("Quando o paciente PEDIR agendamento, confirme dia/hora/tipo. NUNCA recomende "
                + "procedimento — pergunte o que o paciente precisa e registre como \"type\" (texto "
                + "livre, ex.: \"Limpeza\", \"Avaliação\") sem julgar. Para QUALQUER dúvida clínica "
                + "(dor, sintoma, recomendação), responda: \"Para isso, vou pedir que o dentista "
                + "avalie. Posso agendar uma consulta?\". Se o paciente PEDIR para desmarcar/cancelar, "
                + "NÃO emita tag — diga \"vou avisar o dentista, ele entra em contato pra confirmar o "
                + "cancelamento\". Quando o paciente CONFIRMAR (dia/hora/tipo definidos), sua ÚLTIMA "
                + "mensagem deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<consulta>{\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\",\"type\":\"...\","
                + "\"notes\":\"...\"}</consulta>\n")
            .append("Só emita a tag se o paciente já estiver identificado e tudo confirmado.\n")
            .append("Quando o paciente RESPONDER SIM a um lembrete de consulta, termine com a tag "
                + "(linha própria, sem markdown): <confirmacao_consulta>{\"appointment_id\":"
                + "\"UUID_DA_CONSULTA\"}</confirmacao_consulta> — use o id EXATO das consultas dele "
                + "acima. Se ele pedir pra DESMARCAR/REMARCAR, mantenha a regra: o cancelamento é "
                + "feito pelo consultório (ofereça os horários livres pra REMARCAR, sem cancelar).\n\n");

        return sb.toString();
    }

    /**
     * Lista resumida de horários livres nos próximos {@value #CONTEXT_DAYS} dias. Para cada dia, gera
     * os slots de {@code duration_minutes} entre opens_at e closes_at e remove os ocupados por
     * consultas ativas (agendada/confirmada). Limita a {@value #MAX_SLOTS_PER_DAY} por dia no prompt.
     */
    private String buildSlotsSegment(UUID companyId, DentalClinicConfig config) {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        List<DentalAppointment> active = appointmentRepository.listActiveInRange(companyId, now, until);

        StringBuilder sb = new StringBuilder("HORÁRIOS LIVRES (próximos ")
            .append(CONTEXT_DAYS).append(" dias, resumido):\n");
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);
        boolean anyDay = false;
        for (int d = 0; d < CONTEXT_DAYS; d++) {
            LocalDate day = startDay.plusDays(d).toLocalDate();
            List<String> free = new ArrayList<>();
            LocalTime t = config.opensAt();
            while (!t.plusMinutes(config.durationMinutes()).isAfter(config.closesAt())
                    && free.size() < MAX_SLOTS_PER_DAY) {
                ZonedDateTime slotStart = day.atTime(t).atZone(TENANT_ZONE);
                ZonedDateTime slotEnd = slotStart.plusMinutes(config.durationMinutes());
                boolean inFuture = slotStart.toInstant().isAfter(now);
                boolean occupied = active.stream().anyMatch(a ->
                    !(a.endAt().compareTo(slotStart.toInstant()) <= 0
                        || a.startAt().compareTo(slotEnd.toInstant()) >= 0));
                if (inFuture && !occupied) {
                    free.add(TIME_FMT.format(t));
                }
                t = t.plusMinutes(config.durationMinutes());
            }
            if (!free.isEmpty()) {
                anyDay = true;
                sb.append("- ").append(DATE_FMT.format(day.atStartOfDay(TENANT_ZONE)))
                    .append(": ").append(String.join(", ", free)).append("\n");
            }
        }
        if (!anyDay) {
            sb.append("(nenhum horário livre nos próximos dias — informe o paciente e ofereça "
                + "anotar o contato p/ avisar.)\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String statusLabel(String id) {
        return AppointmentStatus.fromId(id).map(AppointmentStatus::label).orElse(id);
    }
}
