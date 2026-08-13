package com.meada.admin.companies;

/**
 * Corpo (opcional) do POST /admin/companies/{id}/suspend (camada 6.1). O motivo é
 * livre e opcional — registrado no payload do admin_action_log para rastrear o porquê
 * da suspensão. Body inteiramente ausente também é aceito (reason = null).
 *
 * @param reason motivo da suspensão (nullable).
 */
public record SuspendCompanyRequest(String reason) {
}
