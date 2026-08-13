package com.meada.profiles.sushi;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 7.1): garante que só um tenant do perfil 'sushi' acessa os endpoints
 * do SushiBot. Defesa contra um tenant generic/legal/dental tentar bater nas rotas /api/sushi/**.
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil
 * não casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class SushiProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public SushiProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil sushi. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil sushi e devolve o companyId (conveniência —
     * os endpoints sempre precisam dele logo em seguida). Lança WrongProfileException caso
     * contrário (sem empresa, ou empresa de outro perfil).
     */
    public UUID requireSushi(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"sushi".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
