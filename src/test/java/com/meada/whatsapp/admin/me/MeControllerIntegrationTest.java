package com.meada.whatsapp.admin.me;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o shape COMPLETO do MeResponse (email, role, companyId, paletteId) para os 2
 * papéis. O filtro em si (auth, reasons) é coberto pelo
 * JwtAuthenticationFilterIntegrationTest; aqui o foco é o controller mapeando
 * corretamente o AuthenticatedUser → MeResponse.
 *
 * <p>companyId do super-admin é serializado como null EXPLÍCITO (MeResponse não tem
 * {@code @JsonInclude(NON_NULL)}): o shape cravado é {@code companyId: uuid | null}, então
 * o campo está presente com valor null — o frontend lê companyId como string | null.
 *
 * <p>paletteId é SEMPRE presente e não-null (camada 5.0): "meada-default" para
 * super-admin (constante do filtro) e para o tenant-admin do seed (que não seta
 * palette_id → assume o DEFAULT 'meada-default' da coluna).
 */
class MeControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("super-admin → email, role=super_admin, companyId=null, paletteId=meada-default")
    void superAdmin_returnsFullShape() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(SUPER_ADMIN_EMAIL))
            .andExpect(jsonPath("$.role").value("super_admin"))
            .andExpect(jsonPath("$.companyId").value(nullValue()))
            .andExpect(jsonPath("$.paletteId").value("meada-default"));
    }

    @Test
    @DisplayName("tenant-admin → email, role=tenant_admin, companyId=<uuid>, paletteId=meada-default")
    void tenantAdmin_returnsFullShape() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(TENANT_ADMIN_EMAIL))
            .andExpect(jsonPath("$.role").value("tenant_admin"))
            .andExpect(jsonPath("$.companyId").value(companyId.toString()))
            .andExpect(jsonPath("$.paletteId").value("meada-default"));
    }
}
