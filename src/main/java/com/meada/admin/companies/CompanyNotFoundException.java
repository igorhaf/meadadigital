package com.meada.admin.companies;

import java.util.UUID;

/**
 * Empresa não encontrada num endpoint do drill-down (camada 6.1). O controller mapeia
 * para 404 {error, reason: company_not_found} (mesmo shape {error, reason} dos demais
 * erros de negócio do CompanyAdminController).
 */
public class CompanyNotFoundException extends RuntimeException {
    public CompanyNotFoundException(UUID companyId) {
        super("company " + companyId + " not found");
    }
}
