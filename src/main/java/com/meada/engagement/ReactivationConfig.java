package com.meada.engagement;

import java.util.UUID;

/**
 * Configuração de reativação de UMA empresa (camada 5.21 #81): o limiar de inatividade
 * e a mensagem a enviar. Só empresas com AMBOS preenchidos (reactivation_days e
 * reactivation_message não-null) são candidatas — o {@link ReactivationJob} as varre.
 *
 * @param companyId            empresa
 * @param reactivationDays     dias de inatividade para reativar (ai_settings.reactivation_days)
 * @param reactivationMessage  texto a enviar (ai_settings.reactivation_message)
 */
public record ReactivationConfig(UUID companyId, int reactivationDays, String reactivationMessage) {
}
