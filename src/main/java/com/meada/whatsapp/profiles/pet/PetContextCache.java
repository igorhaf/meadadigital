package com.meada.whatsapp.profiles.pet;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.profiles.pet.animals.PetAnimal;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalRepository;
import com.meada.whatsapp.profiles.pet.appointments.PetAppointment;
import com.meada.whatsapp.profiles.pet.appointments.PetAppointmentRepository;
import com.meada.whatsapp.profiles.pet.config.PetConfig;
import com.meada.whatsapp.profiles.pet.config.PetConfigRepository;
import com.meada.whatsapp.profiles.pet.professionals.PetProfessional;
import com.meada.whatsapp.profiles.pet.professionals.PetProfessionalRepository;
import com.meada.whatsapp.profiles.pet.services.PetService;
import com.meada.whatsapp.profiles.pet.services.PetServiceRepository;
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
 * Cache do bloco de contexto dinâmico injetado no prompt do PetBot (camada 7.8). TTL 20s (espelho
 * salon). Keyed por {@code (companyId, contactId)}. Conteúdo: profissionais ativos, serviços ativos
 * (com species_restriction), animais do tutor (até 10, com último agendamento), slots livres por
 * profissional (próximos 7 dias), persona + instruções + 2 variantes da tag.
 */
@Component
public class PetContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 7;
    private static final int SLOT_GRANULARITY_MIN = 30;
    private static final int MAX_SLOTS_PER_PROF_DAY = 6;
    private static final int MAX_ANIMALS = 10;

    private final PetProfessionalRepository professionalRepository;
    private final PetServiceRepository serviceRepository;
    private final PetAnimalRepository animalRepository;
    private final PetAppointmentRepository appointmentRepository;
    private final PetConfigRepository configRepository;
    private final Cache<String, String> cache;

    public PetContextCache(PetProfessionalRepository professionalRepository,
                           PetServiceRepository serviceRepository,
                           PetAnimalRepository animalRepository,
                           PetAppointmentRepository appointmentRepository,
                           PetConfigRepository configRepository) {
        this.professionalRepository = professionalRepository;
        this.serviceRepository = serviceRepository;
        this.animalRepository = animalRepository;
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

    /** Invalida todas as entradas de uma empresa (mutação de prof/service/config/animal/appointment). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String speciesLabel(String s) {
        return switch (s) {
            case "cao" -> "cão";
            case "gato" -> "gato";
            case "outro" -> "outro";
            default -> s;
        };
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        PetConfig config = configRepository.findByCompany(companyId);
        List<PetProfessional> pros = professionalRepository.listByCompany(companyId, true);
        List<PetService> services = serviceRepository.listByCompany(companyId, true);

        StringBuilder sb = new StringBuilder();

        // --- PROFISSIONAIS ---
        if (pros.isEmpty()) {
            sb.append("PROFISSIONAIS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("PROFISSIONAIS (use o professional_id EXATO na tag):\n");
            for (PetProfessional p : pros) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name());
                if (p.specialty() != null && !p.specialty().isBlank()) {
                    sb.append(" (").append(p.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- SERVIÇOS ---
        if (services.isEmpty()) {
            sb.append("SERVIÇOS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("SERVIÇOS (use o service_id EXATO):\n");
            for (PetService s : services) {
                sb.append("- ").append(s.id()).append(" · ");
                if (s.category() != null && !s.category().isBlank()) {
                    sb.append(s.category()).append(" ");
                }
                sb.append(s.name()).append(": ").append(s.durationMinutes()).append("min");
                if (s.priceCents() != null) {
                    sb.append(" R$ ").append(formatBrl(s.priceCents()));
                }
                if (s.speciesRestriction() != null) {
                    sb.append(" [só para ").append(speciesLabel(s.speciesRestriction())).append("]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- ANIMAIS DO TUTOR ---
        if (contactId != null) {
            List<PetAnimal> animals = animalRepository.listByContact(companyId, contactId, true);
            if (!animals.isEmpty()) {
                sb.append("ANIMAIS DO TUTOR (use o animal_id EXATO; ofereça os já cadastrados):\n");
                int count = 0;
                for (PetAnimal a : animals) {
                    if (count++ >= MAX_ANIMALS) break;
                    sb.append("- ").append(a.id()).append(" · ").append(a.name())
                        .append(" (").append(speciesLabel(a.species()));
                    if (a.breed() != null && !a.breed().isBlank()) {
                        sb.append(", ").append(a.breed());
                    }
                    sb.append(")");
                    List<PetAppointment> last = appointmentRepository.listByAnimal(companyId, a.id(), 1);
                    if (!last.isEmpty()) {
                        ZonedDateTime z = last.get(0).startAt().atZone(TENANT_ZONE);
                        sb.append(" — último: ").append(DATE_FMT.format(z)).append(" ").append(last.get(0).serviceName());
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("TUTOR SEM ANIMAIS cadastrados: peça nome, espécie (cão/gato/outro) e raça "
                    + "(opcional) para cadastrar o primeiro animal junto com o agendamento.\n\n");
            }
        } else {
            sb.append("TUTOR NÃO IDENTIFICADO pelo telefone. Peça os dados para cadastrar.\n\n");
        }

        // --- SLOTS LIVRES POR PROFISSIONAL ---
        sb.append(buildSlotsSegment(companyId, config, pros, services));

        // --- PERSONA + INSTRUÇÕES (decisão 11) ---
        sb.append("INSTRUÇÕES DE AGENDAMENTO:\n")
            .append("Se o tutor tem um só animal cadastrado, ofereça aquele; se tem mais, pergunte "
                + "qual. Se for a primeira vez (sem animal), peça nome + espécie (cão/gato/outro) + "
                + "raça opcional. Respeite a restrição de espécie do serviço. NUNCA dê diagnóstico "
                + "veterinário, NUNCA prescreva medicação, NUNCA recomende tratamento — se o tutor "
                + "descrever sintoma, oriente a agendar consulta presencial. Sem julgamento. Confirme "
                + "animal + serviço + profissional + dia + hora ANTES da tag. Sua ÚLTIMA mensagem deve "
                + "TERMINAR com UMA das tags (linha própria, sem markdown):\n")
            .append("Tutor COM animal cadastrado:\n")
            .append("<agendamento_pet>{\"professional_id\":\"UUID\",\"service_id\":\"UUID\","
                + "\"animal_id\":\"UUID\",\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\",\"notes\":\"...\"}</agendamento_pet>\n")
            .append("Tutor SEM animal (cadastra junto):\n")
            .append("<agendamento_pet>{\"professional_id\":\"UUID\",\"service_id\":\"UUID\","
                + "\"new_animal\":{\"name\":\"...\",\"species\":\"cao|gato|outro\",\"breed\":\"...\"},"
                + "\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\",\"notes\":\"...\"}</agendamento_pet>\n")
            .append("Use ids EXATOS. Só emita a tag na confirmação final.\n\n");

        return sb.toString();
    }

    private String buildSlotsSegment(UUID companyId, PetConfig config, List<PetProfessional> pros, List<PetService> services) {
        StringBuilder sb = new StringBuilder("HORÁRIOS LIVRES (próximos ").append(CONTEXT_DAYS).append(" dias, por profissional):\n");
        if (pros.isEmpty() || services.isEmpty()) {
            sb.append("(sem profissionais ou serviços ativos.)\n\n");
            return sb.toString();
        }
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);

        for (PetProfessional p : pros) {
            List<PetAppointment> active = appointmentRepository.listActiveByProfessional(companyId, p.id(), now, until);
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

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
