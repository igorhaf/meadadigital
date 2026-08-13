package com.meada.admin.companies;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo do PATCH /admin/companies/{id} (editar empresa — camada 6.1). Atualiza
 * identidade (name/slug/paletteId) e limites do plano (maxAdmins/maxFaqs/
 * maxConversationsMonth). Todos os campos vêm preenchidos pelo form do super-admin —
 * é uma edição completa do registro editável, não um PATCH parcial campo-a-campo.
 *
 * <p>Validação via Bean Validation (@Valid). slug segue o mesmo regex do
 * {@link CreateCompanyRequest}; colisão de slug vira 409 slug_already_exists no controller
 * (UNIQUE em companies.slug). Limites são nullable (null = sem limite) mas, quando
 * presentes, têm de ser >= 0.
 *
 * @param name                  nome, obrigatório.
 * @param slug                  slug único; minúsculas, números e hífens.
 * @param paletteId             id da paleta de tema (obrigatório; ver nota no CreateCompanyRequest).
 * @param maxAdmins             limite de admins (nullable = sem limite; >= 0).
 * @param maxFaqs               limite de FAQs (nullable = sem limite; >= 0).
 * @param maxConversationsMonth limite de conversas/mês (nullable = sem limite; >= 0).
 */
public record UpdateCompanyRequest(
    @NotBlank(message = "name é obrigatório")
    String name,

    @NotBlank(message = "slug é obrigatório")
    @Pattern(
        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
        message = "slug inválido: use minúsculas, números e hífens (ex.: acme-corp)")
    String slug,

    @NotBlank(message = "paletteId é obrigatório")
    String paletteId,

    @Min(value = 0, message = "maxAdmins não pode ser negativo")
    Integer maxAdmins,

    @Min(value = 0, message = "maxFaqs não pode ser negativo")
    Integer maxFaqs,

    @Min(value = 0, message = "maxConversationsMonth não pode ser negativo")
    Integer maxConversationsMonth,

    // Perfil vertical (camada 7.0). OPCIONAL no PATCH (null = não alterar). Validado contra
    // ProfileType no controller (400 invalid_profile_id) — não via Bean Validation, porque o
    // conjunto válido vive no enum, não num @Pattern.
    String profileId
) {
}
