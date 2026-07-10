package com.meada.profiles.estetica;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.estetica.appointments.AestheticAppointment;
import com.meada.profiles.estetica.appointments.AestheticAppointmentRepository;
import com.meada.profiles.estetica.config.AestheticConfig;
import com.meada.profiles.estetica.config.AestheticConfigRepository;
import com.meada.profiles.estetica.packages.AestheticPackage;
import com.meada.profiles.estetica.packages.AestheticPackageRepository;
import com.meada.profiles.estetica.procedures.AestheticProcedure;
import com.meada.profiles.estetica.procedures.AestheticProcedureRepository;
import com.meada.profiles.estetica.professionals.AestheticProfessional;
import com.meada.profiles.estetica.professionals.AestheticProfessionalRepository;
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
 * Cache do bloco de contexto dinâmico injetado no prompt do EsteticaBot (camada 8.3). TTL 20s
 * (espelho salon). Keyed por {@code (companyId, contactId)}. Conteúdo:
 * <ul>
 *   <li>procedimentos ativos (com preço de sessão) — pra IA referenciar procedure_id;
 *   <li>profissionais ativos — pra IA referenciar professional_id;
 *   <li>PACOTES ATIVOS do cliente com saldo (pra IA agendar consumindo o package_id certo);
 *   <li>slots livres por profissional (próximos 7 dias);
 *   <li>instruções + as 2 tags (&lt;agendamento_estetica&gt; e &lt;compra_pacote&gt;) + trava estética.
 * </ul>
 */
@Component
public class EsteticaContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 7;
    private static final int SLOT_GRANULARITY_MIN = 30;
    private static final int MAX_SLOTS_PER_PROF_DAY = 6;
    private static final int HISTORY_LIMIT = 5;

    private final AestheticProfessionalRepository professionalRepository;
    private final AestheticProcedureRepository procedureRepository;
    private final AestheticPackageRepository packageRepository;
    private final AestheticAppointmentRepository appointmentRepository;
    private final AestheticConfigRepository configRepository;
    private final Cache<String, String> cache;

    public EsteticaContextCache(AestheticProfessionalRepository professionalRepository,
                                AestheticProcedureRepository procedureRepository,
                                AestheticPackageRepository packageRepository,
                                AestheticAppointmentRepository appointmentRepository,
                                AestheticConfigRepository configRepository) {
        this.professionalRepository = professionalRepository;
        this.procedureRepository = procedureRepository;
        this.packageRepository = packageRepository;
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

    /** Invalida todas as entradas de uma empresa (mutação de qualquer entidade de estética). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String brl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        AestheticConfig config = configRepository.findByCompany(companyId);
        List<AestheticProcedure> procedures = procedureRepository.listByCompany(companyId, true);
        List<AestheticProfessional> pros = professionalRepository.listByCompany(companyId, true);

        StringBuilder sb = new StringBuilder();

        // --- PROCEDIMENTOS ---
        if (procedures.isEmpty()) {
            sb.append("PROCEDIMENTOS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("PROCEDIMENTOS (use o procedure_id EXATO; o preço é por SESSÃO):\n");
            for (AestheticProcedure p : procedures) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name())
                    .append(": ").append(p.durationMinutes()).append("min")
                    .append(" (R$ ").append(brl(p.unitPriceCents())).append("/sessão)");
                if (p.category() != null && !p.category().isBlank()) {
                    sb.append(" [").append(p.category()).append("]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- PROFISSIONAIS ---
        if (pros.isEmpty()) {
            sb.append("PROFISSIONAIS: (nenhum ativo.)\n\n");
        } else {
            sb.append("PROFISSIONAIS (use o professional_id EXATO):\n");
            for (AestheticProfessional p : pros) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name());
                if (p.specialty() != null && !p.specialty().isBlank()) {
                    sb.append(" (").append(p.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- PACOTES ATIVOS DO CLIENTE (pra agendar consumindo) + histórico ---
        if (contactId != null) {
            List<AestheticPackage> pkgs = packageRepository.listActiveWithBalanceByContact(companyId, contactId);
            if (!pkgs.isEmpty()) {
                sb.append("PACOTES ATIVOS DO CLIENTE (use o package_id ao agendar uma sessão DO pacote):\n");
                for (AestheticPackage pk : pkgs) {
                    sb.append("- ").append(pk.id()).append(" · ").append(pk.procedureName())
                        .append(" · ").append(pk.sessionsRemaining()).append(" de ")
                        .append(pk.totalSessions()).append(" sessões restantes\n");
                }
                sb.append("\n");
            } else {
                sb.append("CLIENTE SEM PACOTE ATIVO: agendamentos serão avulsos (package_id null), ou "
                    + "ofereça a compra de um pacote.\n\n");
            }

            List<AestheticAppointment> history = appointmentRepository.listByContact(companyId, contactId, HISTORY_LIMIT);
            if (!history.isEmpty()) {
                sb.append("HISTÓRICO DO CLIENTE:\n");
                for (AestheticAppointment a : history) {
                    ZonedDateTime z = a.startAt().atZone(TENANT_ZONE);
                    sb.append("- ").append(DATE_FMT.format(z)).append(": ").append(a.procedureName())
                        .append(" com ").append(a.professionalName()).append("\n");
                }
                sb.append("\n");
            }
        } else {
            sb.append("CLIENTE NÃO IDENTIFICADO pelo telefone. Peça o nome para registrar.\n\n");
        }

        // --- SLOTS LIVRES POR PROFISSIONAL ---
        sb.append(buildSlotsSegment(companyId, config, pros));

        // --- INSTRUÇÕES + TAGS + TRAVA ESTÉTICA ---
        sb.append("INSTRUÇÕES:\n")
            .append("Você AGENDA sessões e CAPTURA a intenção de compra de pacote. NUNCA indique ou "
                + "recomende procedimento ('para isso a profissional vai te avaliar'); NUNCA opine "
                + "sobre o corpo/aparência do cliente; NUNCA prometa resultado; NUNCA confirme "
                + "pagamento de pacote; NUNCA invente preço (use o preço do procedimento acima); NUNCA "
                + "discuta contraindicação/condição de saúde (encaminhe à avaliação). Acolha sem "
                + "reforçar inseguranças.\n")
            .append("Para AGENDAR uma sessão (consumindo um pacote do cliente OU avulsa), termine com a "
                + "tag (linha própria, sem markdown):\n")
            .append("<agendamento_estetica>{\"professional_id\":\"UUID\",\"procedure_id\":\"UUID\","
                + "\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\",\"package_id\":\"UUID|null\","
                + "\"notes\":\"...\"}</agendamento_estetica>\n")
            .append("Para registrar a INTENÇÃO de compra de um pacote (a clínica confirma o pagamento "
                + "depois), termine com:\n")
            .append("<compra_pacote>{\"procedure_id\":\"UUID\",\"total_sessions\":N,\"notes\":\"...\"}"
                + "</compra_pacote>\n")
            .append("Use ids EXATOS. O pacote nasce aguardando confirmação de pagamento pela clínica.\n")
            .append("Quando a cliente RESPONDER a um lembrete de sessão (SIM confirma / NÃO desmarca), "
                + "termine com a tag (linha própria, sem markdown):\n")
            .append("<confirmacao_estetica>{\"appointment_id\":\"UUID_DA_SESSAO\","
                + "\"decisao\":\"confirmado\"}</confirmacao_estetica>\n")
            .append("\"decisao\" é \"confirmado\" ou \"cancelado\" (cancelar DEVOLVE a sessão ao pacote "
                + "automaticamente). Use o appointment_id EXATO das sessões da cliente acima.\n\n");

        return sb.toString();
    }

    private String buildSlotsSegment(UUID companyId, AestheticConfig config, List<AestheticProfessional> pros) {
        StringBuilder sb = new StringBuilder("HORÁRIOS LIVRES (próximos ")
            .append(CONTEXT_DAYS).append(" dias, por profissional):\n");
        if (pros.isEmpty()) {
            sb.append("(sem profissionais ativos — não há disponibilidade.)\n\n");
            return sb.toString();
        }
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);

        for (AestheticProfessional p : pros) {
            List<AestheticAppointment> active =
                appointmentRepository.listActiveByProfessional(companyId, p.id(), now, until);
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
                        !(a.endAt().compareTo(slotStart.toInstant()) <= 0
                            || a.startAt().compareTo(slotEnd.toInstant()) >= 0));
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
