package com.meada.profiles.academia.checkins;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.academia.loyalty.AcademiaLoyaltyService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras dos check-ins / frequência (camada 7.7, feature #4). Registra a presença de uma matrícula
 * numa aula no DIA de hoje (fuso do tenant), impede duplicidade no dia (UNIQUE → 409), e lista por
 * aula/janela. Valida que matrícula e aula pertencem ao tenant (senão 404). NÃO invalida contexto da
 * IA (frequência não entra no prompt por ora).
 *
 * <p>Trava academia: presença é dado ADMINISTRATIVO — a IA nunca prescreve treino/dieta/avaliação
 * nem promete resultado; check-in só registra quem apareceu.
 */
@Service
public class AcademiaCheckinService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AcademiaCheckinRepository repository;
    private final AcademiaLoyaltyService loyaltyService;
    private final AuditLogger auditLogger;

    public AcademiaCheckinService(AcademiaCheckinRepository repository,
                                  AcademiaLoyaltyService loyaltyService, AuditLogger auditLogger) {
        this.repository = repository;
        this.loyaltyService = loyaltyService;
        this.auditLogger = auditLogger;
    }

    public static class MembershipNotFoundException extends RuntimeException {}
    public static class ClassNotFoundException extends RuntimeException {}
    public static class DuplicateCheckinException extends RuntimeException {}

    /** Registra a presença de HOJE (fuso do tenant). source ia|painel (default painel se em branco). */
    @Transactional
    public AcademiaCheckin register(UUID companyId, UUID userId, UUID membershipId, UUID classId,
                                    String source, String notes) {
        if (!repository.membershipExists(companyId, membershipId)) {
            throw new MembershipNotFoundException();
        }
        if (!repository.classExists(companyId, classId)) {
            throw new ClassNotFoundException();
        }
        String src = (source != null && ("ia".equals(source) || "painel".equals(source))) ? source : "painel";
        LocalDate today = LocalDate.now(TENANT_ZONE);
        AcademiaCheckin created;
        try {
            created = repository.insert(companyId, membershipId, classId, today, src, notes);
        } catch (DuplicateKeyException e) {
            throw new DuplicateCheckinException();
        }
        auditLogger.log(companyId, userId, "academia_checkin_recorded", "academia_checkin",
            created.id(), Map.of("membership_id", membershipId.toString(), "class_id", classId.toString(), "source", src));
        // Fidelidade por assiduidade (#12): check-in credita points_per_checkin ao contato da
        // matrícula, NA MESMA transação (duplicata → 409 acima, sem ponto dobrado). Matrícula sem
        // contato (cadastro manual) ou fidelidade desligada → sem crédito, sem erro.
        repository.findMembershipContactId(companyId, membershipId)
            .ifPresent(contactId -> loyaltyService.creditForCheckin(companyId, userId, contactId));
        return created;
    }

    public List<AcademiaCheckin> list(UUID companyId, UUID classId, LocalDate from, LocalDate to) {
        return repository.list(companyId, classId, from, to);
    }
}
