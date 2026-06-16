package com.meada.whatsapp.admin.invitations;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de convite via camada HTTP (filtro + controllers). Cobre o admin
 * (/admin/invitations: create/list, auth) e o público (/api/invitations/{token}/accept:
 * o ramo INVITEE do filtro — JWT válido sem linha em public.users).
 */
class InvitationControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    @DisplayName("POST /admin/invitations autenticado (tenant) → 201 com token + inviteUrl")
    void createInvitation_authenticated_returns201() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(post("/admin/invitations")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"email\":\"convidado@x.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("convidado@x.com"))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.inviteUrl").value(org.hamcrest.Matchers.containsString("/invite/")))
            .andExpect(jsonPath("$.usedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("POST /admin/invitations sem auth → 401")
    void createInvitation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/admin/invitations")
                .contentType("application/json")
                .content("{\"email\":\"convidado@x.com\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /admin/invitations email inválido → 400 invalid_email")
    void createInvitation_invalidEmail_returns400() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(post("/admin/invitations")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"email\":\"naoehemail\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_email"));
    }

    @Test
    @DisplayName("POST /admin/invitations como super-admin → 403 (não é tenant-admin)")
    void createInvitation_superAdmin_returns403() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, ADMIN_SUB);
        mockMvc.perform(post("/admin/invitations")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"email\":\"convidado@x.com\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_tenant_admin"));
    }

    @Test
    @DisplayName("GET /admin/invitations lista só convites da própria empresa")
    void listInvitations_returnsOwnCompanyOnly() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        // cria 1 convite da própria empresa via o próprio endpoint.
        mockMvc.perform(post("/admin/invitations")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"email\":\"a@x.com\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/invitations").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value("a@x.com"));
    }

    @Test
    @DisplayName("POST /api/invitations/{token}/accept: INVITEE (JWT sem linha em users) → 200, cria user")
    void acceptInvitation_inviteeWithoutUsersRow_returns200() throws Exception {
        // admin cria o convite.
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String adminToken = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String inviteToken = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(mockMvc.perform(post("/admin/invitations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"email\":\"novo-invitee@x.com\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
            .get("token").asText();

        // convidado: JWT válido, SEM linha em public.users. Precisa existir em auth.users
        // (FK de public.users criada no accept).
        UUID inviteeSub = UUID.fromString("44444444-4444-4444-4444-444444444444");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", inviteeSub);
        String inviteeToken = mintValidToken("novo-invitee@x.com", inviteeSub);

        mockMvc.perform(post("/api/invitations/" + inviteToken + "/accept")
                .header("Authorization", "Bearer " + inviteeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectTo").value("/dashboard"))
            .andExpect(jsonPath("$.companyId").isNotEmpty());

        // linha em users criada.
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from users where id = ?", Integer.class, inviteeSub);
        assertThatUserExists(count);
    }

    @Test
    @DisplayName("POST /api/invitations/{token}/accept sem JWT → 401")
    void acceptInvitation_noJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/invitations/qualquer-token/accept"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/invitations/{token} público (sem auth): convite ativo → 200 com companyName")
    void lookupInvitation_public_returnsCompanyName() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String adminToken = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String inviteToken = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(mockMvc.perform(post("/admin/invitations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"email\":\"look@x.com\"}"))
                .andReturn().getResponse().getContentAsString())
            .get("token").asText();

        // SEM Authorization header — endpoint público.
        mockMvc.perform(get("/api/invitations/" + inviteToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("look@x.com"))
            .andExpect(jsonPath("$.companyName").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/invitations/{token} token inválido → 404")
    void lookupInvitation_invalid_returns404() throws Exception {
        mockMvc.perform(get("/api/invitations/token-invalido"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("invitation_not_found"));
    }

    private void assertThatUserExists(Integer count) {
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }
}
