package com.meada.profiles.comida.coupons;

import com.meada.AbstractIntegrationTest;
import com.meada.common.coupons.CouponServiceBase.DuplicateCouponException;
import com.meada.common.coupons.CouponServiceBase.InvalidCouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ComidaCouponService (onda 1 do comida, backlog #1 — clone do motor adega): CRUD,
 * duplicate_coupon (case-insensitive), validação de kind/value (percent 1..100, fixed >= 0).
 */
class ComidaCouponServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ComidaCouponService service;

    private static final UUID COMPANY = UUID.fromString("c4000000-0000-0000-0000-000000000085");
    private static final UUID USER = UUID.fromString("d4000000-0000-0000-0000-000000000085");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'comida')",
            COMPANY, "Comida Cp", "comida-cp");
        // USER em auth.users + users (FK audit_log_user_id_fkey) — lição AuditLogger.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@comida-cp.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create percent + create fixed + update + toggle + delete")
    void crud() {
        ComidaCoupon pct = service.create(COMPANY, USER, "OFF10", "percent", 10, 2000, 5,
            LocalDate.now().plusDays(30), true);
        assertThat(pct.kind()).isEqualTo("percent");
        assertThat(pct.value()).isEqualTo(10);
        assertThat(pct.minOrderCents()).isEqualTo(2000);
        assertThat(pct.maxUses()).isEqualTo(5);

        ComidaCoupon fix = service.create(COMPANY, USER, "MINUS5", "fixed", 500, null, null, null, true);
        assertThat(fix.kind()).isEqualTo("fixed");
        assertThat(fix.maxUses()).isNull();

        ComidaCoupon upd = service.update(COMPANY, USER, pct.id(), null, null, 15, null, null, false, null, false, null);
        assertThat(upd.value()).isEqualTo(15);

        ComidaCoupon off = service.toggle(COMPANY, USER, pct.id(), false);
        assertThat(off.active()).isFalse();

        service.delete(COMPANY, USER, fix.id());
        assertThat(service.get(COMPANY, fix.id())).isEmpty();
        assertThat(service.list(COMPANY)).hasSize(1);
    }

    @Test
    @DisplayName("code duplicado (case-insensitive) → DuplicateCouponException")
    void duplicate() {
        service.create(COMPANY, USER, "OFF10", "percent", 10, 0, null, null, true);
        assertThatThrownBy(() -> service.create(COMPANY, USER, "off10", "percent", 20, 0, null, null, true))
            .isInstanceOf(DuplicateCouponException.class);
    }

    @Test
    @DisplayName("percent fora de 1..100 → InvalidCouponException")
    void invalidPercent() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", "percent", 0, 0, null, null, true))
            .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> service.create(COMPANY, USER, "Y", "percent", 101, 0, null, null, true))
            .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    @DisplayName("kind inválido → InvalidCouponException")
    void invalidKind() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "Z", "frete", 10, 0, null, null, true))
            .isInstanceOf(InvalidCouponException.class);
    }
}
