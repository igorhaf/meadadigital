package com.meada.whatsapp.profiles.legal;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 7.2): só um tenant do perfil 'legal' acessa os endpoints do
 * ProcessoBot. Espelho do SushiProfileGuard. Lança {@link WrongProfileException} (→ 403
 * forbidden_wrong_profile) quando o perfil não casa ou o usuário não tem empresa.
 */
@Component
public class LegalProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public LegalProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    public static class WrongProfileException extends RuntimeException {}

    /** Exige tenant do perfil legal; devolve o companyId. */
    public UUID requireLegal(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"legal".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
