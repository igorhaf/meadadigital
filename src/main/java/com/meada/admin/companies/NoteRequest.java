package com.meada.admin.companies;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo do POST/PATCH de notas internas (/admin/companies/{id}/notes — camada 6.1).
 * content obrigatório; 1..5000 chars espelha o CHECK length(trim(content)) between 1 and
 * 5000 da migration 26 (validação client-side defensiva antes do constraint do banco).
 *
 * @param content texto da nota (obrigatório, 1..5000).
 */
public record NoteRequest(
    @NotBlank(message = "content é obrigatório")
    @Size(max = 5000, message = "content deve ter no máximo 5000 caracteres")
    String content
) {
}
