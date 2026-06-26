package com.meada.whatsapp.profiles.las;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.23 / perfil las): garante que só um tenant do perfil 'las' acessa os
 * endpoints do LasBot. Defesa contra um tenant de outro perfil tentar bater nas rotas /api/las/**.
 * Clone literal de {@link com.meada.whatsapp.profiles.lingerie.LingerieProfileGuard} (chassi de
 * varejo com variantes da camada 8.21).
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil não
 * casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class LasProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public LasProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil las. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil las e devolve o companyId (conveniência — os
     * endpoints sempre precisam dele logo em seguida). Lança WrongProfileException caso contrário
     * (sem empresa, ou empresa de outro perfil).
     */
    public UUID requireLas(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"las".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
