package com.meada.whatsapp.profiles.pousada.reservations;

import com.meada.whatsapp.profiles.pousada.PousadaContextCache;
import com.meada.whatsapp.profiles.pousada.PousadaReservationStatus;
import com.meada.whatsapp.profiles.pousada.rooms.PousadaRoom;
import com.meada.whatsapp.profiles.pousada.rooms.PousadaRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das reservas de pousada (camada 7.6).
 *
 * <p>{@link #create} valida o quarto (existe + ativo), as datas (check_out > check_in; check_in >=
 * hoje no fuso BRT) e a capacidade (guests_count <= room.capacity), computa nights = check_out -
 * check_in, e delega ao repositório — que re-verifica o conflito por quarto (overlap de intervalos)
 * na transação. Snapshots de room_name/nightly_rate/capacity vêm do room. Status inicial = reservado.
 *
 * <p>{@link #updateStatus} valida a transição (decisão 2) e notifica (decisão 3) com quarto/datas/
 * total. Fuso HARDCODED America/Sao_Paulo (relevante para validar check_in >= hoje).
 */
@Service
public class PousadaReservationService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PousadaReservationRepository reservationRepository;
    private final PousadaRoomRepository roomRepository;
    private final PousadaReservationNotifier notifier;
    private final PousadaContextCache contextCache;

    public PousadaReservationService(PousadaReservationRepository reservationRepository,
                                     PousadaRoomRepository roomRepository,
                                     PousadaReservationNotifier notifier,
                                     PousadaContextCache contextCache) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class ReservationNotFoundException extends RuntimeException {}
    public static class RoomNotFoundException extends RuntimeException {}
    public static class InactiveRoomException extends RuntimeException {}
    public static class OverCapacityException extends RuntimeException {}
    public static class InvalidDatesException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de intervalo (→ 409 conflict_dates). Carrega o conflito p/ o controller. */
    public static class ConflictException extends RuntimeException {
        private final transient PousadaReservationConflict conflict;

        public ConflictException(PousadaReservationConflict conflict) {
            this.conflict = conflict;
        }

        public PousadaReservationConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria uma reserva (status inicial reservado). Valida quarto + datas + capacidade; computa nights
     * e total (no repo). Snapshots vêm do room. O repo re-verifica o conflito por quarto na transação.
     */
    public PousadaReservation create(UUID companyId, UUID roomId, UUID contactId, UUID conversationId,
                                     LocalDate checkInDate, LocalDate checkOutDate, int guestsCount,
                                     String guestName, String guestPhone, String notes) {
        PousadaRoom room = roomRepository.findById(companyId, roomId)
            .orElseThrow(RoomNotFoundException::new);
        if (!room.active()) {
            throw new InactiveRoomException();
        }
        // Datas: check_out > check_in; check_in não pode ser no passado (fuso do tenant).
        if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new InvalidDatesException();
        }
        LocalDate today = LocalDate.now(TENANT_ZONE);
        if (checkInDate.isBefore(today)) {
            throw new InvalidDatesException();
        }
        if (guestsCount < 1 || guestsCount > room.capacity()) {
            throw new OverCapacityException();
        }
        int nights = (int) ChronoUnit.DAYS.between(checkInDate, checkOutDate);

        PousadaReservation created;
        try {
            created = reservationRepository.insertReservation(companyId, roomId, room.name(),
                room.nightlyRateCents(), room.capacity(), conversationId, contactId, guestName,
                guestPhone, guestsCount, checkInDate, checkOutDate, nights, notes);
        } catch (PousadaReservationRepository.DatesConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<PousadaReservation> list(UUID companyId, String status, LocalDate dateFrom,
                                         LocalDate dateTo, UUID roomId, UUID contactId, int limit, int offset) {
        return reservationRepository.listByCompany(companyId, status, dateFrom, dateTo, roomId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, LocalDate dateFrom, LocalDate dateTo,
                      UUID roomId, UUID contactId) {
        return reservationRepository.countByCompany(companyId, status, dateFrom, dateTo, roomId, contactId);
    }

    public Optional<PousadaReservation> get(UUID companyId, UUID id) {
        return reservationRepository.findById(companyId, id);
    }

    @Transactional
    public PousadaReservation updateStatus(UUID companyId, UUID id, String newStatusId) {
        PousadaReservationStatus newStatus = PousadaReservationStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        PousadaReservation current = reservationRepository.findById(companyId, id)
            .orElseThrow(ReservationNotFoundException::new);
        PousadaReservationStatus from = PousadaReservationStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        reservationRepository.updateStatus(companyId, id, newStatus.id());

        String text = newStatus.notificationText(
            DATE_FMT.format(current.checkInDate()), DATE_FMT.format(current.checkOutDate()),
            current.roomName(), formatBrl(current.totalCents()));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return reservationRepository.findById(companyId, id).orElseThrow(ReservationNotFoundException::new);
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
