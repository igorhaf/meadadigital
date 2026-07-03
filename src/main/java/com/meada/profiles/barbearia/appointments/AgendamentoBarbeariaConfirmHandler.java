package com.meada.profiles.barbearia.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.messaging.ContactRepository;
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
 * Extrai a tag {@code <agendamento_barbearia>{...}</agendamento_barbearia>} da resposta da IA e cria
 * o agendamento (camada 8.1). Clone exato do AgendamentoConfirmHandler do salon, com namespace de tag
 * próprio (_barbearia) — distinto do {@code <agendamento>} do salon.
 *
 * <p>NÃO usa tool calling / responseSchema. {@code date}+{@code start_time} → instante no fuso
 * America/Sao_Paulo (hardcoded). O guest_name vem do contact.name (snapshot). O
 * BarberAppointmentService lê a duração do serviço (snapshot) e valida barbeiro/serviço/janela/
 * conflito. Qualquer falha → {@link Optional#empty()} + warn (a mensagem da IA segue sem agendamento).
 */
@Component
public class AgendamentoBarbeariaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoBarbeariaConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile(
        "<agendamento_barbearia>\\s*(\\{.*?\\})\\s*</agendamento_barbearia>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final BarberAppointmentService appointmentService;

    public AgendamentoBarbeariaConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                              BarberAppointmentService appointmentService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
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
     * Extrai a tag, resolve o contato e cria o agendamento. {@link Optional#empty()} quando: não há
     * tag, JSON inválido, campos faltando, ou a criação falha (barbeiro/serviço inválido/inativo, fora
     * do horário, conflito).
     */
    public Optional<BarberAppointment> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("barbearia: tag <agendamento_barbearia> com JSON inválido p/ conversa {} ({}) — não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawBarber = root.path("barber_id").asText(null);
        String rawService = root.path("service_id").asText(null);
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String notes = root.path("notes").asText(null);
        String cupom = root.path("cupom").asText(null);   // opcional (onda 1, backlog #12) — inválido não aborta
        if (rawBarber == null || rawService == null || date == null || startTime == null) {
            log.warn("barbearia: tag <agendamento_barbearia> com campos faltando p/ conversa {} — não criado",
                conversationId);
            return Optional.empty();
        }

        UUID barberId;
        UUID serviceId;
        Instant startAt;
        try {
            barberId = UUID.fromString(rawBarber);
            serviceId = UUID.fromString(rawService);
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("barbearia: tag <agendamento_barbearia> com ids/data inválidos p/ conversa {} ({}) — não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // guest_name/phone: snapshot do contato (a IA só emite tag com cliente na conversa).
        String guestName = contactRepository.findNameByConversationId(conversationId)
            .filter(n -> n != null && !n.isBlank())
            .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Cliente"));
        String guestPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            BarberAppointment a = appointmentService.create(companyId, barberId, serviceId,
                contactId, conversationId, startAt, guestName, guestPhone, notes, cupom);
            log.info("barbearia: agendamento {} criado p/ conversa {} (barbeiro {}, serviço {})",
                a.id(), conversationId, barberId, serviceId);
            return Optional.of(a);
        } catch (BarberAppointmentService.ConflictException e) {
            log.warn("barbearia: <agendamento_barbearia> conflitou no slot do barbeiro p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (BarberAppointmentService.OutsideHoursException e) {
            log.warn("barbearia: <agendamento_barbearia> fora do horário p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (BarberAppointmentService.BarberNotFoundException
                 | BarberAppointmentService.ServiceNotFoundException
                 | BarberAppointmentService.InactiveBarberException
                 | BarberAppointmentService.InactiveServiceException e) {
            log.warn("barbearia: <agendamento_barbearia> com barbeiro/serviço inválido ou inativo p/ conversa {} — não criado",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("barbearia: falha ao criar agendamento p/ conversa {} ({}) — mensagem segue sem agendamento",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
