package com.meada.whatsapp.profiles.casamento;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.7): garante que só um tenant do perfil 'casamento' acessa os endpoints
 * do CasamentoBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário.
 * Espelho do EventosProfileGuard.
 */
@Component
public class CasamentoProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public CasamentoProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil casamento. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil casamento e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireCasamento(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"casamento".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
