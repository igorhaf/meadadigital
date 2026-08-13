package com.meada.admin.companies;

/**
 * Transição de status inválida no lifecycle da empresa (camada 6.1): suspender uma já
 * suspensa, ou reativar uma já ativa. O controller mapeia para 409 {error, reason} com o
 * reason carregado nesta exceção (already_suspended | already_active).
 */
public class CompanyStatusConflictException extends RuntimeException {
    private final String reason;

    public CompanyStatusConflictException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
