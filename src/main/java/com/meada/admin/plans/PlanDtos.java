package com.meada.admin.plans;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTOs dos planos (camada 6.8). Limites nullable = ilimitado. features é jsonb livre.
 */
public final class PlanDtos {
    private PlanDtos() {}

    /** Resposta de um plano (formato de saída). */
    public record PlanResponse(
        UUID id,
        String name,
        String slug,
        int monthlyPriceCents,
        Integer maxAdmins,
        Integer maxFaqs,
        Integer maxConversationsMonth,
        Integer maxUsers,
        JsonNode features,
        boolean active,
        String createdAt,
        String updatedAt) {}

    /** Criação. name/slug obrigatórios; limites/price opcionais (default 0/null). */
    public record CreatePlanRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String slug,
        Integer monthlyPriceCents,
        Integer maxAdmins,
        Integer maxFaqs,
        Integer maxConversationsMonth,
        Integer maxUsers,
        JsonNode features) {}

    /** Edição parcial (PATCH). Todos opcionais. */
    public record UpdatePlanRequest(
        @Size(max = 120) String name,
        @Size(max = 120) String slug,
        Integer monthlyPriceCents,
        Integer maxAdmins,
        Integer maxFaqs,
        Integer maxConversationsMonth,
        Integer maxUsers,
        JsonNode features,
        Boolean active) {}
}
