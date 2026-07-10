package com.meada.profiles.academia.coupons;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.academia.coupons.AcademiaCouponService.CouponValidation;
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
 * Testa o {@link AcademiaCouponService} (camada 7.7): CRUD, duplicate_coupon, validação de
 * kind/value, e o coração da feature — {@link AcademiaCouponService#validate} que aplica/rejeita o
 * cupom checando active + validade + pedido mínimo + limite de usos.
 */
class AcademiaCouponServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AcademiaCouponService service;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-0000000000c7");
    private static final UUID USER = UUID.fromString("dc000000-0000-0000-0000-0000000000c7");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia Cupom", "academia-cupom");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@aca-cupom.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create percent + create fixed + update + toggle + delete")
    void crud() {
        AcademiaCoupon pct = service.create(COMPANY, USER, "OFF10", "percent", 10, 2000, 5,
            LocalDate.now().plusDays(30), true);
        assertThat(pct.kind()).isEqualTo("percent");
        assertThat(pct.value()).isEqualTo(10);
        assertThat(pct.minCents()).isEqualTo(2000);
        assertThat(pct.maxUses()).isEqualTo(5);

        AcademiaCoupon fix = service.create(COMPANY, USER, "MINUS5", "fixed", 500, null, null, null, true);
        assertThat(fix.kind()).isEqualTo("fixed");
        assertThat(fix.maxUses()).isNull();

        AcademiaCoupon upd = service.update(COMPANY, USER, pct.id(), null, null, 15, null, null, false, null, false, null);
        assertThat(upd.value()).isEqualTo(15);

        AcademiaCoupon off = service.toggle(COMPANY, USER, pct.id(), false);
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
    @DisplayName("percent fora de 1..100 / kind inválido → InvalidCouponException")
    void invalidKindValue() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", "percent", 0, 0, null, null, true))
            .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> service.create(COMPANY, USER, "Y", "percent", 101, 0, null, null, true))
            .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> service.create(COMPANY, USER, "Z", "frete", 10, 0, null, null, true))
            .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    @DisplayName("validate percent aplica desconto (com clamp); fixed idem; código case-insensitive")
    void validateApplies() {
        service.create(COMPANY, USER, "OFF10", "percent", 10, 0, null, null, true);
        CouponValidation vPct = service.validate(COMPANY, "off10", 10000);   // 10% de 100,00 = 10,00
        assertThat(vPct.valid()).isTrue();
        assertThat(vPct.discountCents()).isEqualTo(1000);
        assertThat(vPct.reason()).isNull();

        service.create(COMPANY, USER, "MINUS5", "fixed", 500, 0, null, null, true);
        CouponValidation vFix = service.validate(COMPANY, "MINUS5", 3000);
        assertThat(vFix.valid()).isTrue();
        assertThat(vFix.discountCents()).isEqualTo(500);

        // fixed maior que o subtotal → clamp ao subtotal.
        CouponValidation vClamp = service.validate(COMPANY, "MINUS5", 300);
        assertThat(vClamp.valid()).isTrue();
        assertThat(vClamp.discountCents()).isEqualTo(300);
    }

    @Test
    @DisplayName("validate rejeita: inexistente, inativo, expirado, abaixo do mínimo, sem usos restantes")
    void validateRejects() {
        assertThat(service.validate(COMPANY, "NOPE", 10000).reason()).isEqualTo("coupon_not_found");

        service.create(COMPANY, USER, "INATIVO", "percent", 10, 0, null, null, false);
        assertThat(service.validate(COMPANY, "INATIVO", 10000).reason()).isEqualTo("coupon_inactive");

        service.create(COMPANY, USER, "VELHO", "percent", 10, 0, null, LocalDate.now().minusDays(1), true);
        assertThat(service.validate(COMPANY, "VELHO", 10000).reason()).isEqualTo("coupon_expired");

        service.create(COMPANY, USER, "MIN50", "percent", 10, 5000, null, null, true);
        CouponValidation below = service.validate(COMPANY, "MIN50", 4000);
        assertThat(below.valid()).isFalse();
        assertThat(below.reason()).isEqualTo("below_minimum");

        AcademiaCoupon used = service.create(COMPANY, USER, "UMSO", "fixed", 100, 0, 1, null, true);
        service.apply(COMPANY, used.id());   // uses = 1, max_uses = 1 → esgotado
        CouponValidation maxed = service.validate(COMPANY, "UMSO", 10000);
        assertThat(maxed.valid()).isFalse();
        assertThat(maxed.reason()).isEqualTo("max_uses_reached");
    }
}
