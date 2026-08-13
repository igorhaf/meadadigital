package com.meada.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Corpo de resposta para erros de validação (HTTP 400).
 *
 * <p>{@code error} é constante ("validation_failed") — chave única para o cliente
 * parsear, igual em dev e prod. {@code violations} só aparece em DEV (em prod é
 * null e omitido pelo {@code @JsonInclude(NON_NULL)}): não revelar a estrutura
 * interna (nomes de campos) a um cliente externo é defesa em profundidade.
 *
 * @param error      sempre "validation_failed"
 * @param violations campos violados — preenchido em dev, null (omitido) em prod
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationErrorResponse(String error, List<FieldViolation> violations) {

    public static final String ERROR_CODE = "validation_failed";

    /** Corpo detalhado (dev): com a lista de campos violados. */
    public static ValidationErrorResponse detailed(List<FieldViolation> violations) {
        return new ValidationErrorResponse(ERROR_CODE, violations);
    }

    /** Corpo opaco (prod): só o código de erro, sem revelar campos. */
    public static ValidationErrorResponse opaque() {
        return new ValidationErrorResponse(ERROR_CODE, null);
    }

    /**
     * Um campo violado.
     *
     * @param field   caminho do campo (ex. "data.key.id")
     * @param message regra violada (ex. "must not be blank")
     */
    public record FieldViolation(String field, String message) {
    }
}
