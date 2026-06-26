package com.meada.whatsapp.profiles.suplementos;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.24 / perfil suplementos): garante que só um tenant do perfil
 * 'suplementos' acessa os endpoints do SuplementosBot. Defesa contra um tenant de outro perfil
 * tentar bater nas rotas /api/suplementos/**. Clone literal de
 * {@link com.meada.whatsapp.profiles.lingerie.LingerieProfileGuard} (chassi de varejo) /
 * {@link com.meada.whatsapp.profiles.adega.AdegaProfileGuard} (chassi comida).
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil não
 * casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class SuplementosProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public SuplementosProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil suplementos. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil suplementos e devolve o companyId (conveniência —
     * os endpoints sempre precisam dele logo em seguida). Lança WrongProfileException caso contrário
     * (sem empresa, ou empresa de outro perfil).
     */
    public UUID requireSuplementos(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"suplementos".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
