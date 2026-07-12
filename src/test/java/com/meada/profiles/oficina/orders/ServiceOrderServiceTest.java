package com.meada.profiles.oficina.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.oficina.orders.ServiceOrderService.EmptyBudgetException;
import com.meada.profiles.oficina.orders.ServiceOrderService.InactiveVehicleException;
import com.meada.profiles.oficina.orders.ServiceOrderService.InvalidStatusTransitionException;
import com.meada.profiles.oficina.orders.ServiceOrderService.OrderLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ServiceOrderService (camada 7.9): open válida (snapshots de cliente/veículo), veículo
 * inativo, addItem recalcula total, orçar sem item → empty_budget, mutar item em OS travada →
 * order_locked, transição inválida, e notificação de orçamento (com total BRL). EvolutionSender fake.
 */
@Import(ServiceOrderServiceTest.TestConfig.class)
class ServiceOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ServiceOrderService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000003");
    private UUID contactId;
    private UUID conversationId;
    private UUID vehicleId;
    private UUID inactiveVehicleId;
    private UUID mechanicId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'oficina')",
            COMPANY, "Oficina S", "oficina-s");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990190", "João");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        vehicleId = UUID.randomUUID();
        jdbcTemplate.update("insert into os_vehicles (id, company_id, contact_id, plate, brand, model) "
            + "values (?, ?, ?, 'ABC1D23', 'Fiat', 'Uno')", vehicleId, COMPANY, contactId);
        inactiveVehicleId = UUID.randomUUID();
        jdbcTemplate.update("insert into os_vehicles (id, company_id, contact_id, plate, brand, model, active) "
            + "values (?, ?, ?, 'OFF1X00', 'VW', 'Gol', false)", inactiveVehicleId, COMPANY, contactId);
        mechanicId = UUID.randomUUID();
        jdbcTemplate.update("insert into os_mechanics (id, company_id, name, specialty) values (?, ?, 'Carlos', 'Motor')",
            mechanicId, COMPANY);
    }

    @Test
    @DisplayName("open válida → aberta, total 0, snapshots de cliente/veículo")
    void open_aberta() {
        ServiceOrder o = service.open(COMPANY, vehicleId, mechanicId, conversationId,
            "Barulho no motor", null, null, null);
        assertThat(o.status()).isEqualTo("aberta");
        assertThat(o.totalCents()).isZero();
        assertThat(o.customerName()).isEqualTo("João");
        assertThat(o.vehiclePlate()).isEqualTo("ABC1D23");
        assertThat(o.vehicleModel()).isEqualTo("Uno");
    }

    @Test
    @DisplayName("open com veículo inativo → InactiveVehicleException")
    void open_inactiveVehicle() {
        assertThatThrownBy(() -> service.open(COMPANY, inactiveVehicleId, null, null,
            "Revisão", null, null, null))
            .isInstanceOf(InactiveVehicleException.class);
    }

    @Test
    @DisplayName("addItem recalcula o total (peca 2x5000 + mao_de_obra 8000 = 18000)")
    void addItem_recalculatesTotal() {
        ServiceOrder o = service.open(COMPANY, vehicleId, null, null, "Troca de óleo", null, null, null);
        service.addItem(COMPANY, o.id(), "peca", "Filtro", 2, 5000);
        ServiceOrder afterPeca = service.get(COMPANY, o.id()).orElseThrow();
        assertThat(afterPeca.totalCents()).isEqualTo(10000);

        service.addItem(COMPANY, o.id(), "mao_de_obra", "Mão de obra", 1, 8000);
        ServiceOrder afterMo = service.get(COMPANY, o.id()).orElseThrow();
        assertThat(afterMo.totalCents()).isEqualTo(18000);
        assertThat(afterMo.items()).hasSize(2);
    }

    @Test
    @DisplayName("updateItem parcial (só quantity / só preço) materializa line_total com os valores FINAIS")
    void updateItem_partial_materializesFinalLineTotal() {
        ServiceOrder o = service.open(COMPANY, vehicleId, null, null, "Troca de óleo", null, null, null);
        OsItem item = service.addItem(COMPANY, o.id(), "peca", "Filtro", 2, 10000);
        assertThat(item.lineTotalCents()).isEqualTo(20000);

        // Muda SÓ a quantidade → 3 × 10000 (unit mantido) = 30000. Regressão real: a SET clause
        // referenciando quantity * unit_price_cents lia os valores ANTIGOS da linha e gravava 20000.
        OsItem updated = service.updateItem(COMPANY, o.id(), item.id(), null, null, 3, null);
        assertThat(updated.lineTotalCents()).isEqualTo(30000);
        assertThat(service.get(COMPANY, o.id()).orElseThrow().totalCents()).isEqualTo(30000);

        // Muda SÓ o preço unitário → 3 × 20000 = 60000.
        OsItem repriced = service.updateItem(COMPANY, o.id(), item.id(), null, null, null, 20000);
        assertThat(repriced.lineTotalCents()).isEqualTo(60000);
        assertThat(service.get(COMPANY, o.id()).orElseThrow().totalCents()).isEqualTo(60000);
    }

    @Test
    @DisplayName("orçar OS sem item → EmptyBudgetException")
    void orcar_emptyBudget() {
        ServiceOrder o = service.open(COMPANY, vehicleId, null, null, "Diagnóstico", null, null, null);
        assertThatThrownBy(() -> service.updateStatus(COMPANY, o.id(), "orcada"))
            .isInstanceOf(EmptyBudgetException.class);
    }

    @Test
    @DisplayName("mutar item em OS travada (em_execucao) → OrderLockedException")
    void addItem_locked() {
        ServiceOrder o = service.open(COMPANY, vehicleId, null, null, "Troca de óleo", null, null, null);
        service.addItem(COMPANY, o.id(), "peca", "Filtro", 1, 5000);
        // aberta → orcada (com item) → aprovada → em_execucao (trava itens).
        service.updateStatus(COMPANY, o.id(), "orcada");
        service.updateStatus(COMPANY, o.id(), "aprovada");
        service.updateStatus(COMPANY, o.id(), "em_execucao");

        assertThatThrownBy(() -> service.addItem(COMPANY, o.id(), "peca", "Vela", 1, 3000))
            .isInstanceOf(OrderLockedException.class);
    }

    @Test
    @DisplayName("transição inválida (aberta → aprovada) → InvalidStatusTransitionException")
    void invalidTransition() {
        ServiceOrder o = service.open(COMPANY, vehicleId, null, null, "Revisão", null, null, null);
        assertThatThrownBy(() -> service.updateStatus(COMPANY, o.id(), "aprovada"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("updateStatus → orcada notifica com 'Orçamento' + total BRL exato")
    void orcar_notifies() {
        ServiceOrder o = service.open(COMPANY, vehicleId, null, conversationId, "Troca de óleo", null, null, null);
        service.addItem(COMPANY, o.id(), "peca", "Filtro", 2, 5000);   // 10000
        service.addItem(COMPANY, o.id(), "mao_de_obra", "Mão de obra", 1, 8000);   // +8000 = 18000
        ServiceOrder orcada = service.updateStatus(COMPANY, o.id(), "orcada");
        assertThat(orcada.status()).isEqualTo("orcada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Orçamento").contains("R$ 180,00");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-oficina";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
