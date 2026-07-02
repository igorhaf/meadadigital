package com.meada.profiles.academia.loyalty;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.academia.loyalty.AcademiaLoyaltyService.ContactNotFoundException;
import com.meada.profiles.academia.loyalty.AcademiaLoyaltyService.InvalidConfigException;
import com.meada.profiles.academia.loyalty.AcademiaLoyaltyService.InvalidPointsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test da fidelidade por assiduidade da academia (camada 7.7, feature #12) contra
 * PostgreSQL real.
 *
 * <p>Cenários: config default (ausente → enabled=false, 1 ponto, sem recompensa) → update grava e
 * relê; addPoints ACUMULA entre chamadas e cria/atualiza a linha de saldo; rewardReached quando o
 * limiar é atingido; validações (pontos <=0 → 400; contato de outro tenant → 404; config inválida →
 * 400).
 */
class AcademiaLoyaltyServiceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-0000000000c8");
    private static final UUID USER = UUID.fromString("aa000000-0000-0000-0000-0000000000c8");
    private static final UUID CONTACT = UUID.fromString("dd000000-0000-0000-0000-0000000000c8");

    @Autowired
    private AcademiaLoyaltyService service;

    @BeforeEach
    void seed() {
        jdbcTemplate.update(
            "insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia F", "academia-f");
        // USER precisa existir em auth.users: o AuditLogger insere audit_log.user_id (FK) DENTRO da
        // transação do service — user inexistente aborta a tx e o commit vira rollback silencioso.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update(
            "insert into users (id, company_id, email, role) values (?, ?, 'u@aca-fid.dev', 'admin')",
            USER, COMPANY);
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, '+5511990001122', 'Aluno F')",
            CONTACT, COMPANY);
    }

    @Test
    @DisplayName("config ausente devolve defaults; update grava e relê")
    void configDefaultsThenUpdate() {
        AcademiaLoyaltyConfig def = service.getConfig(COMPANY);
        assertThat(def.enabled()).isFalse();
        assertThat(def.pointsPerCheckin()).isEqualTo(1);
        assertThat(def.rewardThreshold()).isNull();
        assertThat(def.rewardText()).isNull();

        AcademiaLoyaltyConfig saved = service.updateConfig(
            COMPANY, USER, true, 2, 10, "  Uma aula grátis  ");
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.pointsPerCheckin()).isEqualTo(2);
        assertThat(saved.rewardThreshold()).isEqualTo(10);
        assertThat(saved.rewardText()).isEqualTo("Uma aula grátis");   // trim aplicado

        AcademiaLoyaltyConfig reread = service.getConfig(COMPANY);
        assertThat(reread.enabled()).isTrue();
        assertThat(reread.pointsPerCheckin()).isEqualTo(2);
        assertThat(reread.rewardThreshold()).isEqualTo(10);
    }

    @Test
    @DisplayName("config inválida (points_per_checkin < 1 ou threshold < 1) → 400")
    void invalidConfigRejected() {
        assertThatThrownBy(() -> service.updateConfig(COMPANY, USER, true, 0, null, null))
            .isInstanceOf(InvalidConfigException.class);
        assertThatThrownBy(() -> service.updateConfig(COMPANY, USER, true, 1, 0, null))
            .isInstanceOf(InvalidConfigException.class);
    }

    @Test
    @DisplayName("addPoints acumula entre chamadas e materializa o saldo")
    void addPointsAccumulates() {
        assertThat(service.getBalance(COMPANY, CONTACT).points()).isZero();   // sem linha → 0

        AcademiaLoyaltyBalance b1 = service.addPoints(COMPANY, USER, CONTACT, 3);
        assertThat(b1.points()).isEqualTo(3);
        assertThat(b1.updatedAt()).isNotNull();

        AcademiaLoyaltyBalance b2 = service.addPoints(COMPANY, USER, CONTACT, 5);
        assertThat(b2.points()).isEqualTo(8);   // 3 + 5

        assertThat(service.getBalance(COMPANY, CONTACT).points()).isEqualTo(8);
    }

    @Test
    @DisplayName("rewardReached quando o saldo atinge o limiar configurado")
    void rewardReachedAtThreshold() {
        service.updateConfig(COMPANY, USER, true, 1, 10, "Camiseta");
        AcademiaLoyaltyConfig config = service.getConfig(COMPANY);

        service.addPoints(COMPANY, USER, CONTACT, 9);
        assertThat(service.rewardReached(config, service.getBalance(COMPANY, CONTACT))).isFalse();

        service.addPoints(COMPANY, USER, CONTACT, 1);   // total 10
        assertThat(service.rewardReached(config, service.getBalance(COMPANY, CONTACT))).isTrue();
    }

    @Test
    @DisplayName("addPoints: pontos <= 0 → 400; contato de outro tenant → 404")
    void addPointsValidations() {
        assertThatThrownBy(() -> service.addPoints(COMPANY, USER, CONTACT, 0))
            .isInstanceOf(InvalidPointsException.class);
        assertThatThrownBy(() -> service.addPoints(COMPANY, USER, CONTACT, -5))
            .isInstanceOf(InvalidPointsException.class);
        assertThatThrownBy(() -> service.addPoints(COMPANY, USER, UUID.randomUUID(), 3))
            .isInstanceOf(ContactNotFoundException.class);
    }
}
