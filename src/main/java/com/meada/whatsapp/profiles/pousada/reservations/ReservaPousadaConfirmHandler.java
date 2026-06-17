package com.meada.whatsapp.profiles.pousada.reservations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.messaging.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <reserva_pousada>{...}</reserva_pousada>} da resposta da IA e cria a reserva
 * (camada 7.6). Espelho dos confirm handlers anteriores.
 *
 * <p>NAMESPACE distinto: a tag é {@code <reserva_pousada>}, NÃO {@code <reserva>} (essa é do
 * RestaurantBot). check_in/check_out são LocalDate (sem timezone — é dia). guest_name vem do JSON
 * ou cai no contact.name. O service valida quarto/datas/capacidade/conflito; qualquer falha →
 * {@link Optional#empty()} + warn.
 */
@Component
public class ReservaPousadaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(ReservaPousadaConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<reserva_pousada>\\s*(\\{.*?\\})\\s*</reserva_pousada>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final PousadaReservationService reservationService;

    public ReservaPousadaConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                        PousadaReservationService reservationService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.reservationService = reservationService;
    }

    public boolean hasReservaPousadaTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripReservaPousadaTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, resolve o contato e cria a reserva. {@link Optional#empty()} quando: não há tag,
     * JSON inválido, campos faltando, ou a criação falha (quarto inválido/inativo, datas inválidas,
     * over-capacity, conflito).
     */
    public Optional<PousadaReservation> parseAndCreate(UUID companyId, UUID conversationId,
                                                       UUID contactId, String aiResponseText) {
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
            log.warn("pousada: tag <reserva_pousada> com JSON inválido p/ conversa {} ({}) — reserva não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawRoom = root.path("room_id").asText(null);
        String checkInStr = root.path("check_in").asText(null);
        String checkOutStr = root.path("check_out").asText(null);
        int guestsCount = root.path("guests_count").asInt(0);
        String guestNameJson = root.path("guest_name").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawRoom == null || checkInStr == null || checkOutStr == null || guestsCount <= 0) {
            log.warn("pousada: tag <reserva_pousada> com campos faltando p/ conversa {} — reserva não criada",
                conversationId);
            return Optional.empty();
        }

        UUID roomId;
        LocalDate checkIn;
        LocalDate checkOut;
        try {
            roomId = UUID.fromString(rawRoom);
            checkIn = LocalDate.parse(checkInStr);
            checkOut = LocalDate.parse(checkOutStr);
        } catch (RuntimeException e) {
            log.warn("pousada: tag <reserva_pousada> com room_id/datas inválidos p/ conversa {} ({}) — reserva não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // guest_name: do JSON, ou contact.name, ou telefone, ou "Hóspede".
        String guestName = guestNameJson != null && !guestNameJson.isBlank()
            ? guestNameJson.strip()
            : contactRepository.findNameByConversationId(conversationId)
                .filter(n -> n != null && !n.isBlank())
                .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Hóspede"));
        String guestPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            PousadaReservation r = reservationService.create(companyId, roomId, contactId, conversationId,
                checkIn, checkOut, guestsCount, guestName, guestPhone, notes);
            log.info("pousada: reserva {} criada p/ conversa {} (quarto {}, {} a {})",
                r.id(), conversationId, roomId, checkIn, checkOut);
            return Optional.of(r);
        } catch (PousadaReservationService.ConflictException e) {
            log.warn("pousada: <reserva_pousada> conflitou no quarto p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (PousadaReservationService.InvalidDatesException e) {
            log.warn("pousada: <reserva_pousada> com datas inválidas p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (PousadaReservationService.OverCapacityException e) {
            log.warn("pousada: <reserva_pousada> acima da capacidade p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (PousadaReservationService.RoomNotFoundException
                 | PousadaReservationService.InactiveRoomException e) {
            log.warn("pousada: <reserva_pousada> com quarto inválido/inativo p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("pousada: falha ao criar reserva p/ conversa {} ({}) — mensagem segue sem reserva",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
