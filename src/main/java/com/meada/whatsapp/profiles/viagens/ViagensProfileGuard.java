package com.meada.whatsapp.profiles.viagens;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.18 / perfil viagens): garante que só um tenant do perfil 'viagens' acessa
 * os endpoints do ViagensBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso
 * contrário. Espelho EXATO do EventosProfileGuard (camada 8.2 — chassi clonado).
 */
@Component
public class ViagensProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public ViagensProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil viagens. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil viagens e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireViagens(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"viagens".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
