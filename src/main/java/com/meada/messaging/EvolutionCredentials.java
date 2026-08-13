package com.meada.messaging;

/**
 * Credenciais de envio de uma instância WhatsApp — o par {@code (instance_name, token)}
 * que a Evolution API exige para mandar mensagem ({@code POST /message/sendText/{instance}}
 * com header {@code apikey: token}).
 *
 * <p>Lido junto numa query só pelo {@code WhatsappInstanceRepository.findEvolutionCredentials},
 * no único caminho que precisa do segredo (o OutboundService, na hora do envio). Não
 * circula no domínio {@link WhatsappInstance} (que é enxuto: id, companyId) — defesa
 * em profundidade: o token não vaza para o fluxo inbound nem para consumidores que não
 * tenham motivo de vê-lo.
 *
 * @param instanceName nome da instância na Evolution (vai no path da URL de envio)
 * @param token        evolution_token (vai no header apikey; segredo)
 */
public record EvolutionCredentials(String instanceName, String token) {
}
