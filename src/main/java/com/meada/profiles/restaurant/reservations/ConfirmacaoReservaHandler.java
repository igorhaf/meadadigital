package com.meada.profiles.restaurant.reservations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <confirmacao_reserva>{...}</confirmacao_reserva>} da resposta da IA e MUTA o
 * status da reserva (onda Restaurant 1, backlog #1 — fecha o loop do lembrete "confirma? SIM/NÃO"
 * do RestaurantReminderJob). A IA só REFLETE a decisão do CLIENTE: {@code decisao} confirmada (de
 * 'pendente') ou cancelada (de 'pendente'/'confirmada' — a máquina de status valida; cancelar
 * LIBERA o slot na hora, e a notificação padrão de confirmada/cancelada dispara pelo service).
 *
 * <p>BARREIRA DE CONTATO: a reserva tem de pertencer ao MESMO contato da conversa. Transição
 * inválida, id inexistente, decisão desconhecida ou contato divergente → {@link Optional#empty()}
 * + warn (a mensagem da IA segue sem mutação). O OutboundService remove a tag antes de enviar.
 * Clone EXATO do ConfirmacaoBarbeariaHandler (mig 83).
 */
@Component
public class ConfirmacaoReservaHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoReservaHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_reserva>\\s*(\\{.*?\\})\\s*</confirmacao_reserva>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ReservationService reservationService;

    public ConfirmacaoReservaHandler(ObjectMapper objectMapper, ReservationService reservationService) {
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
    }

    public boolean hasConfirmacaoTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripConfirmacaoTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e aplica a decisão do cliente à reserva. {@link Optional#empty()} quando: não há
     * tag, JSON/campos inválidos, reserva inexistente, contato divergente (barreira) ou transição
     * inválida na máquina de status.
     */
    public Optional<Reservation> parseAndApply(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("restaurant: tag <confirmacao_reserva> com JSON inválido p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("reservation_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || (!"confirmada".equals(decisao) && !"cancelada".equals(decisao))) {
            log.warn("restaurant: tag <confirmacao_reserva> com campos inválidos p/ conversa {}", conversationId);
            return Optional.empty();
        }
        UUID reservationId;
        try {
            reservationId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            log.warn("restaurant: <confirmacao_reserva> com reservation_id inválido p/ conversa {}", conversationId);
            return Optional.empty();
        }

        Optional<Reservation> current = reservationService.get(companyId, reservationId);
        if (current.isEmpty()) {
            log.warn("restaurant: <confirmacao_reserva> p/ reserva inexistente {} (conversa {})",
                reservationId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: só o dono da reserva confirma/cancela pela conversa.
        if (current.get().contactId() == null || !current.get().contactId().equals(contactId)) {
            log.warn("restaurant: <confirmacao_reserva> de contato divergente p/ reserva {} (conversa {}) — ignorada",
                reservationId, conversationId);
            return Optional.empty();
        }

        try {
            Reservation updated = reservationService.updateStatus(companyId, reservationId, decisao);
            log.info("restaurant: reserva {} → {} pela resposta do cliente (conversa {})",
                reservationId, decisao, conversationId);
            return Optional.of(updated);
        } catch (ReservationService.InvalidStatusTransitionException e) {
            log.warn("restaurant: <confirmacao_reserva> com transição inválida p/ reserva {} (status atual não permite {})",
                reservationId, decisao);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("restaurant: falha ao aplicar <confirmacao_reserva> p/ reserva {} ({})",
                reservationId, e.getMessage());
            return Optional.empty();
        }
    }
}
