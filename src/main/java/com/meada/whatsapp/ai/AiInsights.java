package com.meada.whatsapp.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Detecções OPCIONAIS extras que a IA pode fazer numa resposta (camada 5.18) — todas
 * nullable, preenchidas só quando o modelo as detecta (a maioria das respostas as omite).
 * Agrupadas num único objeto para não inflar o construtor de {@link AiResponse}.
 *
 * @param cancellationIntent intent de cancelamento (#51); null se não detectado
 * @param complaintIntent    intent de reclamação (#52); quando presente, o OutboundService
 *                           FORÇA handoff (handled_by='human')
 * @param extractedData      dados estruturados coletados na conversa (#53); JsonNode livre
 *                           (nome/email/cpf/etc.) — persistido como jsonb. null se nada novo
 * @param memoryUpdate       atualização da memória de longo prazo do contato (#55); JsonNode
 *                           livre, merge sobre contact_memory existente. null se nada novo
 * @param detectedTone       tom detectado do contato (#58): formal|informal|neutro|irritado;
 *                           null se o modelo não classificou (tipicamente só na 1ª interação)
 */
public record AiInsights(
    DetectedIntent cancellationIntent,
    DetectedIntent complaintIntent,
    JsonNode extractedData,
    JsonNode memoryUpdate,
    String detectedTone,
    AppointmentAction appointmentAction) {

    /** Insights vazio (nenhuma detecção) — evita null no AiResponse quando nada veio. */
    public static AiInsights empty() {
        return new AiInsights(null, null, null, null, null, null);
    }

    /** true se algum campo foi detectado (vale a pena persistir). */
    public boolean hasAny() {
        return cancellationIntent != null || complaintIntent != null
            || extractedData != null || memoryUpdate != null || detectedTone != null
            || appointmentAction != null;
    }
}
