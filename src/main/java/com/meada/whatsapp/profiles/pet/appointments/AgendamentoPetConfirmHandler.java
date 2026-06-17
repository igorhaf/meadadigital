package com.meada.whatsapp.profiles.pet.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.profiles.pet.animals.PetAnimal;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <agendamento_pet>{...}</agendamento_pet>} da resposta da IA e cria o
 * agendamento (camada 7.8). Espelho dos confirm handlers, com a NOVIDADE dos 2 MODOS:
 *
 * <ul>
 *   <li><b>animal_id</b> existente: agenda direto para um animal já cadastrado do tutor.</li>
 *   <li><b>new_animal</b> {name, species, breed?}: cadastra o animal (sub-entidade do contato/tutor
 *       da conversa) e, em seguida, agenda — tudo no mesmo turno.</li>
 * </ul>
 *
 * <p>NÃO usa tool calling / responseSchema (mesma restrição da Gemini). {@code date}+{@code
 * start_time} → instante America/Sao_Paulo (hardcoded). O tutor vem do contato da conversa; o
 * snapshot de nome/telefone é resolvido pelo {@link PetAppointmentService} a partir do animal.
 * Qualquer falha → {@link Optional#empty()} + warn (a mensagem da IA segue sem agendamento).
 */
@Component
public class AgendamentoPetConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoPetConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile("<agendamento_pet>\\s*(\\{.*?\\})\\s*</agendamento_pet>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final PetAnimalService animalService;
    private final PetAppointmentService appointmentService;

    public AgendamentoPetConfirmHandler(ObjectMapper objectMapper, PetAnimalService animalService,
                                        PetAppointmentService appointmentService) {
        this.objectMapper = objectMapper;
        this.animalService = animalService;
        this.appointmentService = appointmentService;
    }

    public boolean hasAgendamentoTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripAgendamentoTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e cria o agendamento. Resolve o animal por um dos 2 modos. {@link Optional#empty()}
     * quando: não há tag, JSON inválido, campos faltando, animal/cadastro inválido, ou a criação do
     * agendamento falha (profissional/serviço inválido/inativo, espécie incompatível, fora do horário,
     * conflito). O {@code contactId} (tutor) vem da conversa.
     */
    public Optional<PetAppointment> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("pet: tag <agendamento_pet> com JSON inválido p/ conversa {} ({}) — agendamento não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawProf = root.path("professional_id").asText(null);
        String rawService = root.path("service_id").asText(null);
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawProf == null || rawService == null || date == null || startTime == null) {
            log.warn("pet: tag <agendamento_pet> com campos faltando p/ conversa {} — agendamento não criado",
                conversationId);
            return Optional.empty();
        }

        UUID professionalId;
        UUID serviceId;
        Instant startAt;
        try {
            professionalId = UUID.fromString(rawProf);
            serviceId = UUID.fromString(rawService);
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("pet: tag <agendamento_pet> com ids/data inválidos p/ conversa {} ({}) — agendamento não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // Resolve o animal: modo animal_id (existente) OU new_animal (cadastra e agenda).
        UUID animalId;
        try {
            animalId = resolveAnimal(companyId, contactId, conversationId, root);
        } catch (ResolveAnimalException e) {
            return Optional.empty();
        }
        if (animalId == null) {
            return Optional.empty();
        }

        try {
            PetAppointment a = appointmentService.create(companyId, professionalId, serviceId,
                animalId, conversationId, startAt, notes);
            log.info("pet: agendamento {} criado p/ conversa {} (prof {}, serviço {}, animal {})",
                a.id(), conversationId, professionalId, serviceId, animalId);
            return Optional.of(a);
        } catch (PetAppointmentService.ConflictException e) {
            log.warn("pet: <agendamento_pet> conflitou no slot do profissional p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (PetAppointmentService.SpeciesMismatchException e) {
            log.warn("pet: <agendamento_pet> com espécie incompatível com o serviço p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (PetAppointmentService.OutsideHoursException e) {
            log.warn("pet: <agendamento_pet> fora do horário p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (PetAppointmentService.ProfessionalNotFoundException
                 | PetAppointmentService.ServiceNotFoundException
                 | PetAppointmentService.AnimalNotFoundException
                 | PetAppointmentService.InactiveProfessionalException
                 | PetAppointmentService.InactiveServiceException
                 | PetAppointmentService.InactiveAnimalException e) {
            log.warn("pet: <agendamento_pet> com profissional/serviço/animal inválido ou inativo p/ conversa {} — não criado",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("pet: falha ao criar agendamento p/ conversa {} ({}) — mensagem segue sem agendamento",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static class ResolveAnimalException extends RuntimeException {}

    /**
     * Modo animal_id: valida UUID e usa direto (a criação do agendamento revalida que é do tenant).
     * Modo new_animal: cadastra o animal como sub-entidade do tutor (contato da conversa) e retorna
     * o id criado. Sem contato resolvido → não dá pra cadastrar. Espécie/dados inválidos → empty.
     */
    private UUID resolveAnimal(UUID companyId, UUID contactId, UUID conversationId, JsonNode root) {
        String rawAnimal = root.path("animal_id").asText(null);
        if (rawAnimal != null && !rawAnimal.isBlank()) {
            try {
                return UUID.fromString(rawAnimal);
            } catch (RuntimeException e) {
                log.warn("pet: <agendamento_pet> com animal_id inválido p/ conversa {} — não criado", conversationId);
                throw new ResolveAnimalException();
            }
        }

        JsonNode newAnimal = root.path("new_animal");
        if (newAnimal.isMissingNode() || !newAnimal.isObject()) {
            log.warn("pet: <agendamento_pet> sem animal_id nem new_animal p/ conversa {} — não criado", conversationId);
            throw new ResolveAnimalException();
        }
        if (contactId == null) {
            log.warn("pet: <agendamento_pet> new_animal sem tutor resolvido p/ conversa {} — não criado", conversationId);
            throw new ResolveAnimalException();
        }
        String name = newAnimal.path("name").asText(null);
        String species = newAnimal.path("species").asText(null);
        String breed = newAnimal.path("breed").asText(null);
        if (name == null || name.isBlank() || species == null || species.isBlank()) {
            log.warn("pet: <agendamento_pet> new_animal com nome/espécie faltando p/ conversa {} — não criado", conversationId);
            throw new ResolveAnimalException();
        }
        try {
            // userId null: cadastro disparado pela IA (sem ator humano). Audita com actor nulo.
            PetAnimal created = animalService.create(companyId, null, contactId, name, species,
                breed, null, null, null);
            log.info("pet: animal {} cadastrado pela IA p/ conversa {} (tutor {})", created.id(), conversationId, contactId);
            return created.id();
        } catch (PetAnimalService.InvalidSpeciesException e) {
            log.warn("pet: <agendamento_pet> new_animal com espécie inválida p/ conversa {} — não criado", conversationId);
            throw new ResolveAnimalException();
        } catch (PetAnimalService.ContactNotFoundException e) {
            log.warn("pet: <agendamento_pet> new_animal com tutor inexistente p/ conversa {} — não criado", conversationId);
            throw new ResolveAnimalException();
        } catch (RuntimeException e) {
            log.warn("pet: falha ao cadastrar new_animal p/ conversa {} ({}) — não criado", conversationId, e.getMessage());
            throw new ResolveAnimalException();
        }
    }
}
