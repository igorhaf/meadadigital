package com.meada.whatsapp.profiles.features;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import com.meada.whatsapp.profiles.ProfileFeature;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gate de feature por nicho (camada 9.0): garante que a feature está LIGADA para o perfil do tenant
 * antes de deixar passar. Lança {@link FeatureDisabledException} (→ 403 feature_disabled) se a
 * feature está desligada (default OFF). É o ponto onde a SM-M vai pendurar o CMS:
 * {@code featureGuard.requireFeature(user, ProfileFeature.CMS)} no início dos endpoints do CMS.
 */
@Component
public class ProfileFeatureGuard {

    private final CompanyProfileRepository companyProfileRepository;
    private final ProfileFeatureService featureService;

    public ProfileFeatureGuard(CompanyProfileRepository companyProfileRepository,
                               ProfileFeatureService featureService) {
        this.companyProfileRepository = companyProfileRepository;
        this.featureService = featureService;
    }

    /** Lançada quando a feature está desligada para o perfil do tenant. */
    public static class FeatureDisabledException extends RuntimeException {}

    /**
     * Exige que {@code feature} esteja ligada para o perfil do tenant e devolve o companyId. Lança
     * {@link FeatureDisabledException} se o usuário não tem empresa ou se a feature está OFF.
     */
    public UUID requireFeature(AuthenticatedUser user, ProfileFeature feature) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new FeatureDisabledException();
        }
        String profileId = companyProfileRepository.findProfileId(companyId);
        if (!featureService.enabledFor(profileId).contains(feature)) {
            throw new FeatureDisabledException();
        }
        return companyId;
    }
}
