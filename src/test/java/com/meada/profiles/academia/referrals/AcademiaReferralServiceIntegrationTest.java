package com.meada.profiles.academia.referrals;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.academia.referrals.AcademiaReferralService.ReferralNotPendingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test do programa de indicação da academia (camada 7.7) contra PostgreSQL real.
 *
 * <p>Cenários: geração de código ÚNICO por company (sem colisão numa amostra) + conversão de uma
 * indicação pendente (status → convertida, converted_at setado) + reconversão barrada.
 */
class AcademiaReferralServiceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-0000000000b7");
    private static final UUID USER = UUID.fromString("aa000000-0000-0000-0000-0000000000b7");

    @Autowired
    private AcademiaReferralService service;

    @BeforeEach
    void seed() {
        jdbcTemplate.update(
            "insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia R", "academia-r");
        // USER precisa existir em auth.users: o AuditLogger insere audit_log.user_id (FK) DENTRO da
        // transação do service — user inexistente aborta a tx e o commit vira rollback silencioso.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update(
            "insert into users (id, company_id, email, role) values (?, ?, 'u@aca-ref.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("cria indicações com código único por company")
    void createsWithUniqueCode() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            AcademiaReferral r = service.create(
                COMPANY, USER, null, "Amigo " + i, "+551199900000" + i, 10);
            assertThat(r.code()).isNotBlank();
            assertThat(r.status()).isEqualTo("pendente");
            assertThat(r.rewardPercent()).isEqualTo(10);
            assertThat(r.convertedAt()).isNull();
            codes.add(r.code());
        }
        // Todos os códigos distintos (unique por company garante o insert; o service faz retry).
        assertThat(codes).hasSize(25);

        List<AcademiaReferral> all = service.list(COMPANY, null);
        assertThat(all).hasSize(25);
        assertThat(service.list(COMPANY, "pendente")).hasSize(25);
        assertThat(service.list(COMPANY, "convertida")).isEmpty();
    }

    @Test
    @DisplayName("converte uma indicação pendente e barra a reconversão")
    void convertsPendingThenBlocksReconvert() {
        AcademiaReferral created = service.create(COMPANY, USER, null, "Maria", "+5511998887766", 15);
        assertThat(created.status()).isEqualTo("pendente");

        AcademiaReferral converted = service.convert(COMPANY, USER, created.id());
        assertThat(converted.status()).isEqualTo("convertida");
        assertThat(converted.convertedAt()).isNotNull();

        assertThat(service.list(COMPANY, "convertida")).hasSize(1);
        assertThat(service.list(COMPANY, "pendente")).isEmpty();

        // Reconverter já-convertida → 409.
        assertThatThrownBy(() -> service.convert(COMPANY, USER, created.id()))
            .isInstanceOf(ReferralNotPendingException.class);
    }
}
