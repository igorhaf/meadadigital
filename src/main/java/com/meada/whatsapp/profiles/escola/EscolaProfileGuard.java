package com.meada.whatsapp.profiles.escola;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.19): garante que só um tenant do perfil 'escola' acessa os endpoints
 * do EscolaBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário.
 */
@Component
public class EscolaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public EscolaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil escola. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil escola e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireEscola(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"escola".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
