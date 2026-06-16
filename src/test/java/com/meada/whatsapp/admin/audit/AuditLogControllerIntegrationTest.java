package com.meada.whatsapp.admin.audit;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o visualizador de audit log (camada 5.20 #78) via camada HTTP (filtro + controller).
 * Cobre: GET devolve as linhas da empresa do tenant; só da própria empresa (uma 2ª empresa
 * não vaza); sem auth → 401. Semeia audit_log direto no banco (service_role).
 */
class AuditLogControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Insere uma linha em audit_log para a empresa dada. metadata jsonb literal. */
    private void seedAuditRow(UUID companyId, UUID userId, String action, String entity,
                              String metadataJson) {
        jdbcTemplate.update(
            "insert into audit_log (company_id, user_id, action, entity, metadata) "
                + "values (?, ?, ?, ?, ?::jsonb)",
            companyId, userId, action, entity, metadataJson);
    }

    @Test
    @DisplayName("GET /admin/audit-logs devolve as ações da empresa do tenant (metadata como objeto)")
    void listAuditLogs_returnsOwnCompanyRows() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        seedAuditRow(companyId, ADMIN_SUB, "created", "service", "{\"name\":\"Corte\"}");

        mockMvc.perform(get("/admin/audit-logs").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].action").value("created"))
            .andExpect(jsonPath("$[0].entity").value("service"))
            .andExpect(jsonPath("$[0].userId").value(ADMIN_SUB.toString()))
            .andExpect(jsonPath("$[0].metadata.name").value("Corte"))
            .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /admin/audit-logs não vaza linhas de outra empresa")
    void listAuditLogs_doesNotLeakOtherCompany() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        seedAuditRow(companyId, ADMIN_SUB, "created", "service", "{}");

        // 2ª empresa com a sua própria linha de audit — NÃO deve aparecer na resposta.
        UUID otherCompany = UUID.randomUUID();
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            otherCompany, "Outra", "outra-" + otherCompany);
        seedAuditRow(otherCompany, null, "updated", "faq", "{}");

        mockMvc.perform(get("/admin/audit-logs").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].entity").value("service"));
    }

    @Test
    @DisplayName("GET /admin/audit-logs sem auth → 401")
    void listAuditLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/audit-logs"))
            .andExpect(status().isUnauthorized());
    }
}
