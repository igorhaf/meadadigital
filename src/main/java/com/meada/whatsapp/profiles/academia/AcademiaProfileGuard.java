package com.meada.whatsapp.profiles.academia;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 7.7): garante que só um tenant do perfil 'academia' acessa os endpoints
 * do AcademiaBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário.
 */
@Component
public class AcademiaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public AcademiaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil academia. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil academia e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireAcademia(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"academia".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
