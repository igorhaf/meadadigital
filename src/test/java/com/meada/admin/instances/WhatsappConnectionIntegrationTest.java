package com.meada.admin.instances;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conexão do WhatsApp pelo painel do tenant (Fase 0). Evolution FAKE — nenhum teste depende de um
 * container real. Cobre: guard de tenant, provisionamento (QR + token + guard do histórico + webhook),
 * idempotência, 409 já conectado, sync do NÚMERO a partir do ownerJid, desconectar, e o 503 quando o
 * servidor não tem a API key global.
 */
@Import(WhatsappConnectionIntegrationTest.TestConfig.class)
class WhatsappConnectionIntegrationTest extends AbstractAdminIntegrationTest {

    @Autowired
    private FakeEvolutionInstanceApi fakeEvolution;

    /** Sem webhook-url o serviço pula o setWebhook (com warn) — aqui exercitamos o caminho completo. */
    @DynamicPropertySource
    static void webhookProps(DynamicPropertyRegistry registry) {
        registry.add("evolution.webhook-url", () -> "http://meada.test/webhooks/evolution");
    }

    /** O fake é singleton no contexto; o TRUNCATE da classe-mãe não o limpa. */
    @BeforeEach
    void resetFake() {
        fakeEvolution.reset();
    }

    @Test
    @DisplayName("tenant sem instância → not_configured; super-admin → 403 forbidden_not_tenant")
    void statusAndGuard() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, sub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, sub);

        mockMvc.perform(get("/admin/whatsapp").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.status").value("not_configured"))
            .andExpect(jsonPath("$.phoneNumber").doesNotExist());

        // super-admin não tem company_id → não conecta WhatsApp de ninguém.
        UUID superSub = UUID.randomUUID();
        String st = mintValidToken(SUPER_ADMIN_EMAIL, superSub);
        mockMvc.perform(get("/admin/whatsapp").header("Authorization", "Bearer " + st))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_tenant"));
        mockMvc.perform(post("/admin/whatsapp/connect").header("Authorization", "Bearer " + st))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_tenant"));
    }

    @Test
    @DisplayName("connect provisiona: QR + linha 'connecting' + token gravado + GUARD do histórico + webhook apontado")
    void connectProvisions() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, sub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, sub);

        mockMvc.perform(post("/admin/whatsapp/connect").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qrCode").value("data:image/png;base64,FAKEQR"))
            .andExpect(jsonPath("$.status").value("connecting"));

        String instanceName = jdbcTemplate.queryForObject(
            "select instance_name from whatsapp_instances where company_id = ?", String.class, companyId);
        String token = jdbcTemplate.queryForObject(
            "select evolution_token from whatsapp_instances where company_id = ?", String.class, companyId);
        String dbStatus = jdbcTemplate.queryForObject(
            "select status from whatsapp_instances where company_id = ?", String.class, companyId);

        assertThat(instanceName).isNotBlank();
        assertThat(token).isEqualTo("TOKEN-" + instanceName);   // o 'hash' devolvido pela Evolution
        assertThat(dbStatus).isEqualTo("connecting");

        // GUARD do incidente 2026-06-10: syncFullHistory=false aplicado na criação.
        assertThat(fakeEvolution.safetyApplied).contains(instanceName);
        // Webhook apontado para o Meada, com o secret (que o WebhookSecretFilter valida).
        assertThat(fakeEvolution.webhooksSet).hasSize(1);
        assertThat(fakeEvolution.webhooksSet.get(0).secret()).isNotBlank();
    }

    @Test
    @DisplayName("connect é idempotente enquanto conectando (re-emite QR); já conectado → 409 already_connected")
    void connectIdempotentThenConflict() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, sub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, sub);

        mockMvc.perform(post("/admin/whatsapp/connect").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk());
        // 2ª chamada com a instância ainda em 'connecting' → só re-emite o QR, não duplica instância.
        mockMvc.perform(post("/admin/whatsapp/connect").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qrCode").value("data:image/png;base64,FAKEQR"));
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from whatsapp_instances where company_id = ?", Long.class, companyId);
        assertThat(count).isEqualTo(1);

        // O tenant escaneia o QR → a Evolution passa a 'open' com o ownerJid do número dele.
        fakeEvolution.pair("5511988887777@s.whatsapp.net", "Barbearia do Zé");
        mockMvc.perform(post("/admin/whatsapp/connect").header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("already_connected"));
    }

    @Test
    @DisplayName("após parear: status sincroniza o NÚMERO do ownerJid (E.164) e materializa no banco")
    void statusSyncsPhoneNumberFromPairing() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, sub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, sub);
        mockMvc.perform(post("/admin/whatsapp/connect").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk());

        // Pareamento real: quem dita o número é a Evolution (ownerJid), nunca um input do tenant.
        fakeEvolution.pair("5511988887777:12@s.whatsapp.net", "Barbearia do Zé");

        mockMvc.perform(get("/admin/whatsapp").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("connected"))
            .andExpect(jsonPath("$.phoneNumber").value("+5511988887777"))   // sufixo :12 (multi-device) removido
            .andExpect(jsonPath("$.profileName").value("Barbearia do Zé"));

        String phone = jdbcTemplate.queryForObject(
            "select phone_number from whatsapp_instances where company_id = ?", String.class, companyId);
        String dbStatus = jdbcTemplate.queryForObject(
            "select status from whatsapp_instances where company_id = ?", String.class, companyId);
        assertThat(phone).isEqualTo("+5511988887777");
        assertThat(dbStatus).isEqualTo("connected");
    }

    @Test
    @DisplayName("disconnect faz logout e marca disconnected — a instância e o histórico PERMANECEM")
    void disconnectKeepsRow() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, sub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, sub);
        mockMvc.perform(post("/admin/whatsapp/connect").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk());
        fakeEvolution.pair("5511988887777@s.whatsapp.net", "Zé");

        mockMvc.perform(post("/admin/whatsapp/disconnect").header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());

        assertThat(fakeEvolution.loggedOut).isTrue();
        // FK on delete restrict das conversas: a linha NÃO pode sumir.
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from whatsapp_instances where company_id = ?", Long.class, companyId);
        String dbStatus = jdbcTemplate.queryForObject(
            "select status from whatsapp_instances where company_id = ?", String.class, companyId);
        assertThat(count).isEqualTo(1);
        assertThat(dbStatus).isEqualTo("disconnected");
    }

    @Test
    @DisplayName("servidor sem API key global da Evolution → status available=false e connect → 503 whatsapp_unavailable")
    void unavailableWhenNoGlobalKey() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, sub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, sub);
        fakeEvolution.available = false;   // simula EVOLUTION_GLOBAL_API_KEY ausente

        mockMvc.perform(get("/admin/whatsapp").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(post("/admin/whatsapp/connect").header("Authorization", "Bearer " + t))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.reason").value("whatsapp_unavailable"));
    }

    // ---- Evolution fake ------------------------------------------------------

    record WebhookCall(String instanceName, String url, String secret) {}

    /** Evolution em memória: cria instância 'connecting', permite "escanear o QR" via {@link #pair}. */
    static class FakeEvolutionInstanceApi implements EvolutionInstanceApi {
        boolean available = true;
        boolean loggedOut = false;
        String state = null;               // null = instância não existe na Evolution
        String ownerJid = null;
        String profileName = null;
        final List<String> safetyApplied = new ArrayList<>();
        final List<WebhookCall> webhooksSet = new ArrayList<>();

        void reset() {
            available = true;
            loggedOut = false;
            state = null;
            ownerJid = null;
            profileName = null;
            safetyApplied.clear();
            webhooksSet.clear();
        }

        /** Simula o tenant escaneando o QR com o celular do número dele. */
        void pair(String jid, String profile) {
            this.state = STATE_OPEN;
            this.ownerJid = jid;
            this.profileName = profile;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public CreatedInstance createInstance(String instanceName) {
            state = STATE_CONNECTING;
            return new CreatedInstance(instanceName, "TOKEN-" + instanceName, "data:image/png;base64,FAKEQR");
        }

        @Override
        public Optional<String> fetchQrCode(String instanceName) {
            return state == null ? Optional.empty() : Optional.of("data:image/png;base64,FAKEQR");
        }

        @Override
        public Optional<InstanceState> fetchState(String instanceName) {
            return state == null ? Optional.empty()
                : Optional.of(new InstanceState(state, ownerJid, profileName));
        }

        @Override
        public void applySafetySettings(String instanceName) {
            safetyApplied.add(instanceName);
        }

        @Override
        public void setWebhook(String instanceName, String webhookUrl, String webhookSecret) {
            webhooksSet.add(new WebhookCall(instanceName, webhookUrl, webhookSecret));
        }

        @Override
        public void logout(String instanceName) {
            loggedOut = true;
            state = "close";
            ownerJid = null;
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionInstanceApi fakeEvolutionInstanceApi() {
            return new FakeEvolutionInstanceApi();
        }
    }
}
