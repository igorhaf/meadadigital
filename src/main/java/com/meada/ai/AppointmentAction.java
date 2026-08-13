package com.meada.ai;

/**
 * Ação de agendamento que a IA decidiu tomar numa resposta (camada 5.19 #60/#64) —
 * sub-objeto OPCIONAL do responseSchema. null quando a IA não age sobre agendamento.
 *
 * <p>kind:
 *   - "book"      (#60): confirma um novo agendamento. whenIso + serviceHint relevantes.
 *   - "reschedule"(#64): remarca o agendamento ativo do contato para whenIso.
 *   - "cancel"    (#64): cancela o agendamento ativo do contato.
 *
 * <p>whenIso é o horário em ISO-8601 local que o modelo propôs (ex.: "2026-06-20T14:00").
 * O backend valida contra availability_slots + conflitos antes de efetivar; se inválido,
 * NÃO agenda e o reply do modelo (que já pode ter oferecido alternativas) segue. service
 * Hint é livre (texto), casado de forma best-effort a um service_id no backend.
 *
 * @param kind       "book" | "reschedule" | "cancel"
 * @param whenIso    horário proposto (ISO local "yyyy-MM-ddTHH:mm"); null para cancel
 * @param serviceHint serviço mencionado; nullable
 */
public record AppointmentAction(String kind, String whenIso, String serviceHint) {
}
