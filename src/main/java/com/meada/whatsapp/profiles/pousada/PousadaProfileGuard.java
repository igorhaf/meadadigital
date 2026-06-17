package com.meada.whatsapp.profiles.pousada;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 7.6): garante que só um tenant do perfil 'pousada' acessa os endpoints do
 * PousadaBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário.
 */
@Component
public class PousadaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public PousadaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil pousada. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil pousada e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requirePousada(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"pousada".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
