package com.meada.whatsapp.messaging;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link WhatsappInstanceRepository} contra PostgreSQL real
 * (Testcontainers), rodando como service_role, com as migrations de produção.
 */
class WhatsappInstanceRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WhatsappInstanceRepository repository;

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID INSTANCE_A = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID INSTANCE_B = UUID.fromString("b1000000-0000-0000-0000-000000000001");

    private void seedCompany(UUID companyId, String slug) {
        jdbcTemplate.update(
            "insert into companies (id, name, slug) values (?, ?, ?)",
            companyId, "Empresa " + slug, slug);
    }

    private void seedInstance(UUID id, UUID companyId, String instanceName) {
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, ?)",
            id, companyId, instanceName, "tok-" + instanceName);
    }

    @Test
    @DisplayName("instância existente: resolve para o tenant correto")
    void findByInstanceName_existingInstance_returnsTenant() {
        seedCompany(COMPANY_A, "empresa-a");
        seedInstance(INSTANCE_A, COMPANY_A, "test-instance");

        Optional<WhatsappInstance> result = repository.findByInstanceName("test-instance");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(INSTANCE_A);
        assertThat(result.get().companyId()).isEqualTo(COMPANY_A);
    }

    @Test
    @DisplayName("instância desconhecida: retorna Optional.empty()")
    void findByInstanceName_unknownInstance_returnsEmpty() {
        seedCompany(COMPANY_A, "empresa-a");
        seedInstance(INSTANCE_A, COMPANY_A, "test-instance");

        Optional<WhatsappInstance> result = repository.findByInstanceName("nao-existe");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("isola por nome, não confunde tenants: inst-a retorna A, não B")
    void findByInstanceName_isolatesByName_notByCompany() {
        seedCompany(COMPANY_A, "empresa-a");
        seedCompany(COMPANY_B, "empresa-b");
        seedInstance(INSTANCE_A, COMPANY_A, "inst-a");
        seedInstance(INSTANCE_B, COMPANY_B, "inst-b");

        Optional<WhatsappInstance> result = repository.findByInstanceName("inst-a");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(INSTANCE_A);
        assertThat(result.get().companyId()).isEqualTo(COMPANY_A);
    }

    // ---- findEvolutionCredentials -------------------------------------------
    // Nota: NÃO há caso de "isolamento de tenant" aqui — instanceId é PK uuid
    // global única, e a FK para companies já garante o vínculo. Buscar por PK
    // não tem como confundir tenants (ao contrário de findByInstanceName, que
    // busca por um campo de negócio). Por isso só 2 casos: existe / não existe.

    @Test
    @DisplayName("findEvolutionCredentials: instância existente retorna instance_name + token")
    void findEvolutionCredentials_existing_returnsNameAndToken() {
        seedCompany(COMPANY_A, "empresa-a");
        seedInstance(INSTANCE_A, COMPANY_A, "inst-a");   // token = "tok-inst-a"

        Optional<EvolutionCredentials> creds = repository.findEvolutionCredentials(INSTANCE_A);

        assertThat(creds).isPresent();
        assertThat(creds.get().instanceName()).isEqualTo("inst-a");
        assertThat(creds.get().token()).isEqualTo("tok-inst-a");
    }

    @Test
    @DisplayName("findEvolutionCredentials: instância inexistente retorna empty")
    void findEvolutionCredentials_unknown_returnsEmpty() {
        Optional<EvolutionCredentials> creds =
            repository.findEvolutionCredentials(INSTANCE_A);   // nada semeado

        assertThat(creds).isEmpty();
    }
}
