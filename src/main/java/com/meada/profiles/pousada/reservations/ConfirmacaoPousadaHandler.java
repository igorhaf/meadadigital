package com.meada.profiles.pousada.reservations;

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
 * Extrai a tag {@code <confirmacao_pousada>{...}</confirmacao_pousada>} da resposta da IA e MUTA o
 * status da reserva (onda Pousada 1, backlog #2 — fecha o loop do lembrete de check-in D-1
 * do PousadaReminderJob). A IA só REFLETE a decisão do CLIENTE: {@code decisao} confirmado (de
 * 'reservado') ou cancelado (de 'reservado'/'confirmado' — a máquina de status valida; cancelar
 * antecipado LIBERA o quarto pra revenda; a notificação padrão dispara pelo service).
 *
 * <p>BARREIRA DE CONTATO: a reserva tem de pertencer ao MESMO contato da conversa. Transição
 * inválida, id inexistente, decisão desconhecida ou contato divergente → {@link Optional#empty()}
 * + warn (a mensagem da IA segue sem mutação). O OutboundService remove a tag antes de enviar.
 * Clone do ConfirmacaoReservaHandler (restaurant, mig 91).
 */
@Component
public class ConfirmacaoPousadaHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoPousadaHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_pousada>\\s*(\\{.*?\\})\\s*</confirmacao_pousada>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final PousadaReservationService reservationService;

    public ConfirmacaoPousadaHandler(ObjectMapper objectMapper, PousadaReservationService reservationService) {
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
    public Optional<PousadaReservation> parseAndApply(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("pousada: tag <confirmacao_pousada> com JSON inválido p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("reservation_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || (!"confirmado".equals(decisao) && !"cancelado".equals(decisao))) {
            log.warn("pousada: tag <confirmacao_pousada> com campos inválidos p/ conversa {}", conversationId);
            return Optional.empty();
        }
        UUID reservationId;
        try {
            reservationId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            log.warn("pousada: <confirmacao_pousada> com reservation_id inválido p/ conversa {}", conversationId);
            return Optional.empty();
        }

        Optional<PousadaReservation> current = reservationService.get(companyId, reservationId);
        if (current.isEmpty()) {
            log.warn("pousada: <confirmacao_pousada> p/ reserva inexistente {} (conversa {})",
                reservationId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: só o dono da reserva confirma/cancela pela conversa.
        if (current.get().contactId() == null || !current.get().contactId().equals(contactId)) {
            log.warn("pousada: <confirmacao_pousada> de contato divergente p/ reserva {} (conversa {}) — ignorada",
                reservationId, conversationId);
            return Optional.empty();
        }

        try {
            PousadaReservation updated = reservationService.updateStatus(companyId, reservationId, decisao);
            log.info("pousada: reserva {} → {} pela resposta do cliente (conversa {})",
                reservationId, decisao, conversationId);
            return Optional.of(updated);
        } catch (PousadaReservationService.InvalidStatusTransitionException e) {
            log.warn("pousada: <confirmacao_pousada> com transição inválida p/ reserva {} (status atual não permite {})",
                reservationId, decisao);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("pousada: falha ao aplicar <confirmacao_pousada> p/ reserva {} ({})",
                reservationId, e.getMessage());
            return Optional.empty();
        }
    }
}
