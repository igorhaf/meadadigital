package com.meada.whatsapp.profiles.features;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.ProfileFeature;
import com.meada.whatsapp.profiles.ProfileType;
import com.meada.whatsapp.profiles.features.ProfileFeatureService.Grid;
import com.meada.whatsapp.profiles.features.ProfileFeatureService.NicheRow;
import com.meada.whatsapp.profiles.features.ProfileFeatureService.UnknownFeatureException;
import com.meada.whatsapp.profiles.features.ProfileFeatureService.UnknownProfileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ProfileFeatureService (camada 9.0): resolver (ausência = OFF), grade completa, toggle +
 * persistência, ausência=false no resolvedMap, e validação de ids desconhecidos.
 */
class ProfileFeatureServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ProfileFeatureService service;

    private static final UUID ROOT = UUID.fromString("99999999-0000-0000-0000-000000000001");

    @Test
    @DisplayName("default OFF: sem linha no banco, enabledFor é vazio e resolvedMap traz cms=false")
    void default_off() {
        assertThat(service.enabledFor("nutri")).isEmpty();
        assertThat(service.resolvedMap("nutri")).containsEntry("cms", false);
    }

    @Test
    @DisplayName("grade completa: 1 linha por perfil (allActive) × 1 coluna por feature (allActive)")
    void grid_isComplete() {
        Grid grid = service.grid();
        assertThat(grid.features()).extracting(ProfileFeatureService.FeatureView::key).containsExactly("cms");
        assertThat(grid.niches()).hasSize(ProfileType.allActive().size());
        // toda célula presente e default false.
        for (NicheRow n : grid.niches()) {
            assertThat(n.flags()).containsKey("cms");
            assertThat(n.flags().get("cms")).isFalse();
        }
        // os ids batem com o enum.
        assertThat(grid.niches()).extracting(NicheRow::profileId)
            .containsExactlyElementsOf(ProfileType.allActive().stream().map(ProfileType::id).toList());
    }

    @Test
    @DisplayName("setFlag liga → enabledFor reflete; desliga → some; persiste no banco")
    void setFlag_togglesAndPersists() {
        service.setFlag("nutri", "cms", true, ROOT);
        assertThat(service.enabledFor("nutri")).containsExactly(ProfileFeature.CMS);
        assertThat(service.resolvedMap("nutri")).containsEntry("cms", true);
        // só a flag do nutri mudou; outro perfil segue OFF.
        assertThat(service.enabledFor("oficina")).isEmpty();
        // a linha existe no banco.
        Integer rows = jdbcTemplate.queryForObject(
            "select count(*) from profile_features where profile_id = 'nutri' and feature_key = 'cms' and enabled = true",
            Integer.class);
        assertThat(rows).isEqualTo(1);

        service.setFlag("nutri", "cms", false, ROOT);
        assertThat(service.enabledFor("nutri")).isEmpty();
        assertThat(service.resolvedMap("nutri")).containsEntry("cms", false);
    }

    @Test
    @DisplayName("grade reflete o toggle (sobreposição do banco sobre o default)")
    void grid_reflectsToggle() {
        service.setFlag("pet", "cms", true, ROOT);
        Grid grid = service.grid();
        NicheRow pet = grid.niches().stream().filter(n -> n.profileId().equals("pet")).findFirst().orElseThrow();
        NicheRow legal = grid.niches().stream().filter(n -> n.profileId().equals("legal")).findFirst().orElseThrow();
        assertThat(pet.flags().get("cms")).isTrue();
        assertThat(legal.flags().get("cms")).isFalse();
    }

    @Test
    @DisplayName("setFlag com profile_id desconhecido → UnknownProfileException")
    void setFlag_unknownProfile() {
        assertThatThrownBy(() -> service.setFlag("naoexiste", "cms", true, ROOT))
            .isInstanceOf(UnknownProfileException.class);
    }

    @Test
    @DisplayName("setFlag com feature_key desconhecida → UnknownFeatureException")
    void setFlag_unknownFeature() {
        assertThatThrownBy(() -> service.setFlag("nutri", "naoexiste", true, ROOT))
            .isInstanceOf(UnknownFeatureException.class);
    }

    @Test
    @DisplayName("enabledFor com perfil null/desconhecido → vazio (super-admin sem empresa)")
    void enabledFor_nullProfile() {
        assertThat(service.enabledFor(null)).isEmpty();
        assertThat(service.enabledFor("naoexiste")).isEmpty();
    }

    @Test
    @DisplayName("cache invalidado em setFlag: leitura após toggle reflete o novo estado")
    void cache_invalidatedOnSetFlag() {
        // popula o cache com o estado OFF.
        Set<ProfileFeature> before = service.enabledFor("salon");
        assertThat(before).isEmpty();
        // liga: o setFlag invalida a entrada do cache.
        service.setFlag("salon", "cms", true, ROOT);
        assertThat(service.enabledFor("salon")).containsExactly(ProfileFeature.CMS);
    }
}
