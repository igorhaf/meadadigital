package com.meada.profiles.pousada;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.pousada.config.PousadaConfig;
import com.meada.profiles.pousada.config.PousadaConfigRepository;
import com.meada.profiles.pousada.reservations.PousadaReservation;
import com.meada.profiles.pousada.reservations.PousadaReservationRepository;
import com.meada.profiles.pousada.rooms.PousadaRoom;
import com.meada.profiles.pousada.rooms.PousadaRoomRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do PousadaBot (camada 7.6).
 *
 * <p>TTL 30s. Keyed por {@code (companyId, contactId)}. Conteúdo: quartos ativos (nome/cap/preço/
 * descrição) + config (check-in/out + política) + histórico do contato + DISPONIBILIDADE por quarto
 * nos próximos 30 dias (intervalos LIVRES entre reservas ativas) + persona + instruções + exemplo
 * da tag. Fuso America/Sao_Paulo. Os services chamam {@link #invalidate} ao mutar.
 */
@Component
public class PousadaContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final int CONTEXT_DAYS = 30;
    private static final int HISTORY_LIMIT = 3;

    private final PousadaRoomRepository roomRepository;
    private final PousadaConfigRepository configRepository;
    private final PousadaReservationRepository reservationRepository;
    private final Cache<String, String> cache;

    public PousadaContextCache(PousadaRoomRepository roomRepository,
                               PousadaConfigRepository configRepository,
                               PousadaReservationRepository reservationRepository) {
        this.roomRepository = roomRepository;
        this.configRepository = configRepository;
        this.reservationRepository = reservationRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de quarto/config/reserva). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        PousadaConfig config = configRepository.findByCompany(companyId);
        List<PousadaRoom> rooms = roomRepository.listByCompany(companyId, true);
        LocalDate today = LocalDate.now(TENANT_ZONE);
        LocalDate horizon = today.plusDays(CONTEXT_DAYS);

        StringBuilder sb = new StringBuilder();

        // --- QUARTOS ---
        if (rooms.isEmpty()) {
            sb.append("QUARTOS DISPONÍVEIS: (nenhum quarto ativo no momento.)\n\n");
        } else {
            sb.append("QUARTOS DISPONÍVEIS (use o room_id EXATO na tag):\n");
            for (PousadaRoom r : rooms) {
                sb.append("- ").append(r.id()).append(" · ").append(r.name())
                    .append(": cap ").append(r.capacity()).append(" hóspedes, R$ ")
                    .append(formatBrl(r.nightlyRateCents())).append("/noite");
                if (r.description() != null && !r.description().isBlank()) {
                    sb.append(" — ").append(r.description().strip());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- POLÍTICA ---
        sb.append("POLÍTICA:\n")
            .append("Check-in a partir das ").append(config.checkInTime())
            .append(", check-out até ").append(config.checkOutTime()).append(".\n");
        if (config.cancellationPolicy() != null && !config.cancellationPolicy().isBlank()) {
            sb.append(config.cancellationPolicy().strip()).append("\n");
        }
        sb.append("\n");

        // --- HISTÓRICO DO CLIENTE ---
        if (contactId != null) {
            List<PousadaReservation> history = reservationRepository.listByContact(companyId, contactId, HISTORY_LIMIT);
            if (!history.isEmpty()) {
                sb.append("HISTÓRICO DO CLIENTE:\n");
                for (PousadaReservation r : history) {
                    sb.append("- ").append(DATE_FMT.format(r.checkInDate())).append(" a ")
                        .append(DATE_FMT.format(r.checkOutDate())).append(": ").append(r.roomName())
                        .append(" (").append(statusLabel(r.status())).append(")\n");
                }
                sb.append("\n");
            } else {
                sb.append("HISTÓRICO DO CLIENTE: primeira vez (sem reservas anteriores).\n\n");
            }
            // --- RESERVAS FUTURAS (onda 1, backlog #2 — a IA captura confirmação/cancelamento) ---
            List<PousadaReservation> upcoming =
                reservationRepository.listUpcomingByContact(companyId, contactId, 5);
            if (!upcoming.isEmpty()) {
                sb.append("RESERVAS FUTURAS DESTE HÓSPEDE (use o id EXATO na tag de confirmação):\n");
                for (PousadaReservation r : upcoming) {
                    sb.append("- ").append(r.id()).append(" · ").append(r.roomName())
                        .append(", ").append(DATE_FMT.format(r.checkInDate())).append(" a ")
                        .append(DATE_FMT.format(r.checkOutDate()))
                        .append(" · status ").append(statusLabel(r.status())).append("\n");
                }
                sb.append("Quando o hóspede CONFIRMAR a chegada (ex.: responder SIM ao lembrete) ou "
                    + "pedir para CANCELAR, sua ÚLTIMA mensagem deve TERMINAR com a tag (linha própria):\n"
                    + "<confirmacao_pousada>{\"reservation_id\":\"UUID\",\"decisao\":\"confirmado|cancelado\"}"
                    + "</confirmacao_pousada>\n"
                    + "Você só REFLETE a decisão do hóspede — NUNCA confirme ou cancele sem ele pedir.\n\n");
            }
        } else {
            sb.append("CLIENTE NÃO IDENTIFICADO pelo telefone. Peça o nome para registrar a reserva.\n\n");
        }

        // --- DISPONIBILIDADE POR QUARTO (próximos 30 dias) ---
        sb.append(buildAvailabilitySegment(companyId, rooms, today, horizon));

        // --- PERSONA + INSTRUÇÕES (decisão 7) ---
        sb.append("INSTRUÇÕES DE RESERVA:\n")
            .append("Quando o cliente pedir reserva, pergunte número de hóspedes + datas de check-in e "
                + "check-out + ajude a escolher um quarto que comporte o grupo. Calcule o total "
                + "(diária × noites) ANTES de confirmar. NUNCA prometa estrutura/vista/comodidade que "
                + "não esteja na descrição do quarto. Sem promessa de 'experiência única'. Confirme "
                + "quarto + datas + total + nº de hóspedes antes de emitir a tag. Sua ÚLTIMA mensagem "
                + "deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<reserva_pousada>{\"room_id\":\"UUID\",\"check_in\":\"YYYY-MM-DD\","
                + "\"check_out\":\"YYYY-MM-DD\",\"guests_count\":N,\"guest_name\":\"...\","
                + "\"notes\":\"...\"}</reserva_pousada>\n")
            .append("Use o room_id EXATO da lista. Só emita a tag na confirmação final.\n\n");

        return sb.toString();
    }

    /**
     * Para cada quarto ativo, lista os intervalos LIVRES entre as reservas ativas na janela
     * [today, horizon). Algoritmo: ordena as reservas por check_in, varre marcando os "buracos"
     * entre o fim de uma e o início da próxima (e antes da primeira / depois da última).
     */
    private String buildAvailabilitySegment(UUID companyId, List<PousadaRoom> rooms,
                                            LocalDate today, LocalDate horizon) {
        StringBuilder sb = new StringBuilder("DISPONIBILIDADE (próximos ")
            .append(CONTEXT_DAYS).append(" dias):\n");
        if (rooms.isEmpty()) {
            sb.append("(sem quartos ativos.)\n\n");
            return sb.toString();
        }
        for (PousadaRoom room : rooms) {
            List<PousadaReservation> active =
                reservationRepository.listActiveByRoom(companyId, room.id(), today, horizon);
            List<String> free = new ArrayList<>();
            LocalDate cursor = today;
            for (PousadaReservation r : active) {
                LocalDate occStart = r.checkInDate().isBefore(today) ? today : r.checkInDate();
                if (cursor.isBefore(occStart)) {
                    free.add(DATE_FMT.format(cursor) + "–" + DATE_FMT.format(occStart));
                }
                if (r.checkOutDate().isAfter(cursor)) {
                    cursor = r.checkOutDate();
                }
            }
            if (cursor.isBefore(horizon)) {
                free.add(DATE_FMT.format(cursor) + "–" + DATE_FMT.format(horizon));
            }
            sb.append("- ").append(room.name()).append(": ");
            sb.append(free.isEmpty() ? "sem disponibilidade no período" : "livre " + String.join(", ", free));
            sb.append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String statusLabel(String id) {
        return PousadaReservationStatus.fromId(id).map(PousadaReservationStatus::label).orElse(id);
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
