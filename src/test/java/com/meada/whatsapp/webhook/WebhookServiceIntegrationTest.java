package com.meada.whatsapp.webhook;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.webhook.dto.EvolutionWebhookPayload;
import com.meada.whatsapp.webhook.dto.EvolutionWebhookPayload.ExtendedTextMessage;
import com.meada.whatsapp.webhook.dto.EvolutionWebhookPayload.MessageContent;
import com.meada.whatsapp.webhook.dto.EvolutionWebhookPayload.MessageData;
import com.meada.whatsapp.webhook.dto.EvolutionWebhookPayload.MessageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link WebhookService} ponta a ponta contra PostgreSQL real
 * (Testcontainers), como service_role. Cobre os 10 {@link WebhookOutcome} + idempotência
 * + contato novo, verificando tanto o outcome quanto o estado persistido.
 */
class WebhookServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookService service;

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INSTANCE_A = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final String INSTANCE_NAME = "inst-a";

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_A, "Empresa A", "empresa-a");
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE_A, COMPANY_A, INSTANCE_NAME, "tok-a");
    }

    // ---- builder de payload com defaults (inbound user de texto válida) --------

    /** Payload inbound válido completo, com overrides pontuais por named-args. */
    private EvolutionWebhookPayload payload(
            String event, String instance, Boolean fromMe,
            String remoteJid, String evolutionId, String pushName,
            Long timestamp, String conversation, ExtendedTextMessage extended) {
        return new EvolutionWebhookPayload(
            event, instance,
            new MessageData(
                new MessageKey(evolutionId, remoteJid, fromMe),
                pushName, timestamp,
                new MessageContent(conversation, extended)),
            null, null, null, null, null);
    }

    /** Inbound user de texto, válida — base dos casos que devem PROCESSAR. */
    private EvolutionWebhookPayload validInbound(String remoteJid, String evolutionId, String text) {
        return payload("messages.upsert", INSTANCE_NAME, false,
            remoteJid, evolutionId, "Cliente A", recentTimestamp(), text, null);
    }

    private long count(String table, UUID companyId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where company_id = ?", Long.class, companyId);
    }

    // ---- casos por outcome -----------------------------------------------------

    @Test
    @DisplayName("evento != messages.upsert → IGNORED_NON_MESSAGE_EVENT")
    void nonMessageEvent() {
        var p = payload("presence.update", INSTANCE_NAME, false,
            "5511999990000@s.whatsapp.net", "EVT-1", "X", recentTimestamp(), "oi", null);
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_NON_MESSAGE_EVENT);
        assertThat(count("messages", COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("fromMe=true → IGNORED_FROM_ME")
    void fromMeTrue() {
        var p = payload("messages.upsert", INSTANCE_NAME, true,
            "5511999990000@s.whatsapp.net", "EVT-1", "X", recentTimestamp(), "oi", null);
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_FROM_ME);
        assertThat(count("messages", COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("fromMe=null → IGNORED_FROM_ME (defensivo)")
    void fromMeNull() {
        var p = payload("messages.upsert", INSTANCE_NAME, null,
            "5511999990000@s.whatsapp.net", "EVT-1", "X", recentTimestamp(), "oi", null);
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_FROM_ME);
        assertThat(count("messages", COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("instância desconhecida → IGNORED_UNKNOWN_INSTANCE")
    void unknownInstance() {
        var p = payload("messages.upsert", "nao-existe", false,
            "5511999990000@s.whatsapp.net", "EVT-1", "X", recentTimestamp(), "oi", null);
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_UNKNOWN_INSTANCE);
        assertThat(count("messages", COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("grupo (@g.us) → IGNORED_GROUP")
    void group() {
        var p = validInbound("120363012345678901@g.us", "EVT-1", "oi");
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_GROUP);
        assertThat(count("messages", COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("broadcast (@broadcast) → IGNORED_BROADCAST")
    void broadcast() {
        var p = validInbound("status@broadcast", "EVT-1", "oi");
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_BROADCAST);
        assertThat(count("messages", COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("JID não-suportado (@lid) → IGNORED_UNKNOWN_JID")
    void unknownJid() {
        var p = validInbound("5511999990000@lid", "EVT-1", "oi");
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_UNKNOWN_JID);
        assertThat(count("messages", COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("mensagem sem texto (message=null) → IGNORED_NON_TEXT")
    void nonText() {
        var p = payload("messages.upsert", INSTANCE_NAME, false,
            "5511999990000@s.whatsapp.net", "EVT-1", "X", recentTimestamp(), null, null);
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_NON_TEXT);
        assertThat(count("messages", COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("messageTimestamp além do threshold (1h atrás) → IGNORED_STALE (nada persistido)")
    void staleMessageIsIgnored() {
        // 1h atrás = bem além do threshold default (180s) — simula append-on-reconnect
        var p = payload("messages.upsert", INSTANCE_NAME, false,
            "5511999990000@s.whatsapp.net", "EVT-STALE", "Cliente Velho",
            recentTimestamp() - 3600, "oi de 1h atrás", null);

        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_STALE);

        // stale é no-op total: nada persistido (consistente com os outros IGNORED_*)
        assertThat(count("messages", COMPANY_A)).isZero();
        assertThat(count("conversations", COMPANY_A)).isZero();
        assertThat(count("contacts", COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("inbound válida → PROCESSED, com conteúdo persistido correto")
    void processed_verifiesContent() {
        var p = validInbound("5511999990000@s.whatsapp.net", "EVT-ABC", "Olá, tudo bem?");
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.PROCESSED);

        assertThat(count("contacts", COMPANY_A)).isEqualTo(1);
        assertThat(count("conversations", COMPANY_A)).isEqualTo(1);
        assertThat(count("messages", COMPANY_A)).isEqualTo(1);

        // contato: phone normalizado para E.164 (+55...)
        Map<String, Object> contact = jdbcTemplate.queryForMap(
            "select phone_number, name from contacts where company_id = ?", COMPANY_A);
        assertThat(contact.get("phone_number")).isEqualTo("+5511999990000");
        assertThat(contact.get("name")).isEqualTo("Cliente A");

        // conversa aberta
        Map<String, Object> conv = jdbcTemplate.queryForMap(
            "select status, last_message_at from conversations where company_id = ?", COMPANY_A);
        assertThat(conv.get("status")).isEqualTo("open");
        assertThat(conv.get("last_message_at")).isNotNull();

        // mensagem: direction/sender/content corretos
        Map<String, Object> msg = jdbcTemplate.queryForMap(
            "select direction, sender, content, evolution_message_id from messages where company_id = ?",
            COMPANY_A);
        assertThat(msg.get("direction")).isEqualTo("inbound");
        assertThat(msg.get("sender")).isEqualTo("contact");
        assertThat(msg.get("content")).isEqualTo("Olá, tudo bem?");
        assertThat(msg.get("evolution_message_id")).isEqualTo("EVT-ABC");
    }

    @Test
    @DisplayName("reentrega (mesmo evolution_message_id) → 1ª PROCESSED, 2ª IGNORED_DUPLICATE, sem duplicar")
    void duplicate_isIdempotent() {
        var p = validInbound("5511999990000@s.whatsapp.net", "EVT-DUP", "oi");

        assertThat(service.process(p)).isEqualTo(WebhookOutcome.PROCESSED);
        assertThat(service.process(p)).isEqualTo(WebhookOutcome.IGNORED_DUPLICATE);

        // nada duplicado: 1 de cada
        assertThat(count("messages", COMPANY_A)).isEqualTo(1);
        assertThat(count("contacts", COMPANY_A)).isEqualTo(1);
        assertThat(count("conversations", COMPANY_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("segundo número após uma inbound → novo contato e conversa")
    void secondContact_createsNew() {
        service.process(validInbound("5511999990000@s.whatsapp.net", "EVT-1", "oi"));
        service.process(validInbound("5511888880000@s.whatsapp.net", "EVT-2", "ola"));

        assertThat(count("contacts", COMPANY_A)).isEqualTo(2);
        assertThat(count("conversations", COMPANY_A)).isEqualTo(2);
        assertThat(count("messages", COMPANY_A)).isEqualTo(2);
    }
}
