package com.meada.whatsapp.profiles.estetica;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.3): só um tenant do perfil 'estetica' acessa os endpoints do EsteticaBot.
 * Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário. Espelho dos
 * guards anteriores.
 */
@Component
public class EsteticaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public EsteticaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    public static class WrongProfileException extends RuntimeException {}

    public UUID requireEstetica(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"estetica".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
