package com.meada.profiles.atelie.proposals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <confirmacao_prova>{"fitting_id":"..."}</confirmacao_prova>} e registra a
 * CONFIRMAÇÃO de presença do cliente na prova (onda Ateliê 3, backlog #6 — fecha o loop do
 * lembrete de véspera da onda 1). Metadado: {@code confirmed_at} + {@code confirmed_due_date}
 * (remarcar a prova invalida a confirmação). BARREIRA DE CONTATO via proposta dona da prova.
 * Pedido de remarcação NÃO passa por aqui — segue pra equipe (gestão de prova é do painel).
 * Best-effort.
 */
@Component
public class ConfirmacaoProvaHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoProvaHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_prova>\\s*(\\{.*?\\})\\s*</confirmacao_prova>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public ConfirmacaoProvaHandler(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
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

    /** Extrai a tag e confirma a prova (pendente, do contato da conversa). Devolve true se confirmou. */
    public boolean parseAndConfirm(UUID companyId, UUID conversationId, UUID contactId, String aiResponseText) {
        if (aiResponseText == null || contactId == null) {
            return false;
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(m.group(1));
            UUID fittingId = UUID.fromString(root.path("fitting_id").asText());
            // BARREIRA: a prova precisa ser de proposta do CONTATO da conversa, do tenant, pendente.
            int n = jdbcTemplate.update(
                "update atelie_fittings f set confirmed_at = now(), confirmed_due_date = f.due_date "
                    + "from atelie_proposals p "
                    + "where f.id = ? and f.proposal_id = p.id and p.company_id = ? "
                    + "and p.contact_id = ? and f.status = 'pendente'",
                fittingId, companyId, contactId);
            log.info("atelie: confirmação de prova {} {} (conversa {})",
                fittingId, n > 0 ? "registrada" : "ignorada (barreira/estado)", conversationId);
            return n > 0;
        } catch (Exception e) {
            log.warn("atelie: tag <confirmacao_prova> inválida p/ conversa {} ({})",
                conversationId, e.getMessage());
            return false;
        }
    }
}
