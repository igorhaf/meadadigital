package com.meada.whatsapp.profiles.atelie;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.14): garante que só um tenant do perfil 'atelie' acessa os endpoints
 * do AtelieBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário.
 * Espelho do EventosProfileGuard.
 */
@Component
public class AtelieProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public AtelieProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil atelie. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil atelie e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireAtelie(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"atelie".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
