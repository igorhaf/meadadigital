package com.meada.whatsapp.profiles.padaria;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.8 / perfil padaria): garante que só um tenant do perfil 'padaria' acessa os
 * endpoints do PadariaBot. Defesa contra um tenant de outro perfil tentar bater nas rotas
 * /api/padaria/**. Clone literal de {@link com.meada.whatsapp.profiles.floricultura.FloriculturaProfileGuard}.
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil não
 * casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class PadariaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public PadariaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil padaria. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil padaria e devolve o companyId (conveniência — os
     * endpoints sempre precisam dele logo em seguida). Lança WrongProfileException caso contrário
     * (sem empresa, ou empresa de outro perfil).
     */
    public UUID requirePadaria(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"padaria".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
