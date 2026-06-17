package com.meada.whatsapp.profiles.pet;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 7.8): garante que só um tenant do perfil 'pet' acessa os endpoints do
 * PetBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário.
 */
@Component
public class PetProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public PetProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil pet. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil pet e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requirePet(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"pet".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
