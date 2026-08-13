package com.meada.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload do webhook da Evolution API para o evento {@code messages.upsert}.
 *
 * <p>Records aninhados espelham o aninhamento do JSON. São apenas TRANSPORTE:
 * carregam os valores crus do JSON (inclusive {@code remoteJid} com sufixo
 * {@code @s.whatsapp.net}/{@code @g.us}). Normalização (JID→E.164, detecção de
 * grupo), escolha do texto e filtro de {@code fromMe} são responsabilidade da
 * camada de serviço, não destes DTOs.
 *
 * <p><b>Confiança do schema:</b> cada campo é marcado inline como [CONFIRMADO]
 * (visto no código-fonte do envelope da Evolution — webhook.controller.ts) ou
 * [INFERIDO] (formato Baileys, montado de exemplos — pode divergir do real).
 *
 * <p><b>Pendência bloqueante:</b> ver RISKS.md — item "Schema do payload da
 * Evolution validado contra Baileys, não contra fonte oficial". Bloqueante para
 * ativação do primeiro cliente real.
 */
public record EvolutionWebhookPayload(

    @NotBlank
    String event,                 // [CONFIRMADO] esperado "messages.upsert"

    @NotBlank
    String instance,              // [CONFIRMADO] nome da instância → whatsapp_instances.instance_name (chave do tenant)

    @NotNull
    @Valid
    MessageData data,             // [CONFIRMADO] @Valid: cascata de validação para os aninhados

    @JsonProperty("server_url")
    String serverUrl,             // [CONFIRMADO] não usado no MVP

    @JsonProperty("date_time")
    String dateTime,              // [CONFIRMADO] não usado no MVP

    String sender,                // [CONFIRMADO] não usado no MVP

    String apikey,                // [CONFIRMADO] origem NÃO é validada por aqui — é o WEBHOOK_SECRET no header (decisão 7)

    String destination            // [CONFIRMADO] não usado no MVP
) {

    /**
     * Objeto {@code data} do payload (formato Baileys).
     */
    public record MessageData(

        @NotNull
        @Valid
        MessageKey key,           // [INFERIDO] @Valid: cascata para validar key.id / key.remoteJid

        String pushName,          // [INFERIDO] nome do WhatsApp → contacts.name; pode vir null

        Long messageTimestamp,    // [INFERIDO] Unix epoch em SEGUNDOS (não millis). Conversão p/ Instant é no serviço.

        MessageContent message    // [INFERIDO] nullable: mensagens não-textuais (mídia/status) não têm texto
    ) {}

    /**
     * Objeto {@code data.key}.
     */
    public record MessageKey(

        @NotBlank
        String id,                // [INFERIDO] id único da mensagem → evolution_message_id (idempotência)

        @NotBlank
        String remoteJid,         // [INFERIDO] JID CRU (ex.: "5511...@s.whatsapp.net" ou "...@g.us"); normalizer trata

        Boolean fromMe            // [INFERIDO] Boolean NULLABLE de propósito: ausência = null, e o SERVIÇO decide
                                  //   (não o DTO). Tratar null/ausente como inbound seria regra de negócio embutida
                                  //   no transporte — e classificaria reaction/edit/status como mensagem fantasma.
    ) {}

    /**
     * Objeto {@code data.message}.
     *
     * <p>Só modelamos texto. Quando a mensagem for de um dos tipos NÃO modelados
     * abaixo, {@code conversation} E {@code extendedTextMessage} virão ambos null —
     * e o serviço deve tratar como "mensagem não-textual, ignorar" (não é erro).
     * Tipos Evolution/Baileys não modelados (lista não exaustiva):
     *   imageMessage, audioMessage, videoMessage, documentMessage, stickerMessage,
     *   reactionMessage, locationMessage, contactMessage, pollCreationMessage,
     *   editedMessage, protocolMessage.
     */
    public record MessageContent(

        String conversation,                       // [INFERIDO] texto simples

        ExtendedTextMessage extendedTextMessage    // [INFERIDO] texto com reply/formatação/link; nullable
    ) {}

    /**
     * Objeto {@code data.message.extendedTextMessage}.
     */
    public record ExtendedTextMessage(

        String text               // [INFERIDO] texto alternativo (quando não é conversation)
    ) {}
}
