package com.meada.whatsapp.profiles.features;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.ProfileFeature;
import com.meada.whatsapp.profiles.features.ProfileFeatureGuard.FeatureDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ProfileFeatureGuard (camada 9.0): com a feature LIGADA pro nicho do tenant, requireFeature
 * passa (devolve companyId); DESLIGADA (default OFF), lança FeatureDisabledException (→ 403). É o
 * gate que a SM-M usa nos endpoints do CMS.
 */
class ProfileFeatureGuardTest extends AbstractIntegrationTest {

    @Autowired
    private ProfileFeatureGuard guard;
    @Autowired
    private ProfileFeatureService service;

    private static final UUID COMPANY = UUID.fromString("88888888-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("88888888-0000-0000-0000-000000000002");
    private static final UUID ROOT = UUID.fromString("88888888-0000-0000-0000-000000000003");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            COMPANY, "Nutri Guard Co", "nutri-guard-co");
    }

    private AuthenticatedUser tenantUser() {
        return new AuthenticatedUser("tenant@guard.dev", USER, AdminRole.TENANT_ADMIN, COMPANY, "meada-default");
    }

    @Test
    @DisplayName("feature OFF (default) → FeatureDisabledException")
    void off_throws() {
        assertThatThrownBy(() -> guard.requireFeature(tenantUser(), ProfileFeature.CMS))
            .isInstanceOf(FeatureDisabledException.class);
    }

    @Test
    @DisplayName("feature ON → passa e devolve o companyId")
    void on_passes() {
        service.setFlag("nutri", "cms", true, ROOT);
        assertThatCode(() -> {
            UUID companyId = guard.requireFeature(tenantUser(), ProfileFeature.CMS);
            assertThat(companyId).isEqualTo(COMPANY);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("usuário sem empresa (super-admin) → FeatureDisabledException")
    void noCompany_throws() {
        AuthenticatedUser superUser = new AuthenticatedUser("root@guard.dev", ROOT, AdminRole.SUPER_ADMIN, null, "meada-default");
        assertThatThrownBy(() -> guard.requireFeature(superUser, ProfileFeature.CMS))
            .isInstanceOf(FeatureDisabledException.class);
    }
}
