package com.meada.whatsapp.profiles.features;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import com.meada.whatsapp.profiles.ProfileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints ROOT de feature flags (camada 9.0): grade, toggle + audit, authz de plataforma
 * (super-admin only → 403 forbidden_not_super_admin para tenant; 401 sem token) e validação de ids.
 */
class ProfileFeatureControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUB = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String JSON = "application/json";

    @Test
    @DisplayName("super-admin → GET grade com todos os nichos × a feature cms (default false)")
    void grid_listsAllNiches() throws Exception {
        String t = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/profile-features").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.features.length()").value(1))
            .andExpect(jsonPath("$.features[0].key").value("cms"))
            .andExpect(jsonPath("$.niches.length()").value(ProfileType.allActive().size()))
            .andExpect(jsonPath("$.niches[?(@.profileId == 'nutri')].flags.cms").value(false));
    }

    @Test
    @DisplayName("super-admin → PUT liga cms do nutri → 200 + audit; GET reflete true; off → false")
    void toggle_persistsAndAudits() throws Exception {
        String t = mintValidToken(SUPER_ADMIN_EMAIL, SUB);

        mockMvc.perform(put("/admin/profile-features/nutri/cms").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"enabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));

        // audit em admin_action_log.
        Integer audit = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action = 'PROFILE_FEATURE_TOGGLED' and target_type = 'profile_feature'",
            Integer.class);
        org.assertj.core.api.Assertions.assertThat(audit).isEqualTo(1);

        mockMvc.perform(get("/admin/profile-features").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.niches[?(@.profileId == 'nutri')].flags.cms").value(true));

        // desliga → reflete false.
        mockMvc.perform(put("/admin/profile-features/nutri/cms").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"enabled\":false}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/admin/profile-features").header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.niches[?(@.profileId == 'nutri')].flags.cms").value(false));
    }

    @Test
    @DisplayName("tenant-admin (não-root) → GET → 403 forbidden_not_super_admin")
    void tenantAdmin_get_forbidden() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, SUB);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/profile-features").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_super_admin"));
    }

    @Test
    @DisplayName("tenant-admin (não-root) → PUT → 403 forbidden_not_super_admin")
    void tenantAdmin_put_forbidden() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, SUB);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, SUB);
        mockMvc.perform(put("/admin/profile-features/nutri/cms").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"enabled\":true}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_super_admin"));
    }

    @Test
    @DisplayName("sem token → 401 (endpoint atrás do filtro /admin/**)")
    void noToken_unauthorized() throws Exception {
        mockMvc.perform(get("/admin/profile-features"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT com profile_id desconhecido → 400 unknown_profile")
    void unknownProfile_400() throws Exception {
        String t = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(put("/admin/profile-features/naoexiste/cms").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"enabled\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("unknown_profile"));
    }

    @Test
    @DisplayName("PUT com feature_key desconhecida → 400 unknown_feature")
    void unknownFeature_400() throws Exception {
        String t = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(put("/admin/profile-features/nutri/naoexiste").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"enabled\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("unknown_feature"));
    }
}
