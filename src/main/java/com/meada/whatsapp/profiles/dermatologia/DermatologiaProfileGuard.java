package com.meada.whatsapp.profiles.dermatologia;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.11): garante que só um tenant do perfil 'dermatologia' acessa os
 * endpoints do DermatologiaBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile)
 * caso contrário.
 */
@Component
public class DermatologiaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public DermatologiaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil dermatologia. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil dermatologia e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireDermatologia(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"dermatologia".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
