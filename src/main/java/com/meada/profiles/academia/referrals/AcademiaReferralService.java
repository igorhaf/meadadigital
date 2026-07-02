package com.meada.profiles.academia.referrals;

import com.meada.common.audit.AuditLogger;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do programa de indicação da academia (camada 7.7). Gera um código ÚNICO por company
 * (retry em colisão), lista as indicações e converte uma indicação pendente. O desconto
 * (reward_percent) é LOCAL e a concessão é operação manual do tenant — aqui só rastreamos.
 */
@Service
public class AcademiaReferralService {

    /** Alfabeto sem caracteres ambíguos (0/O, 1/I) — código lido/digitado por humano. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;
    private static final int MAX_CODE_ATTEMPTS = 8;

    private final AcademiaReferralRepository repository;
    private final AuditLogger auditLogger;
    private final SecureRandom random = new SecureRandom();

    public AcademiaReferralService(AcademiaReferralRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    /** Indicação não encontrada / de outro tenant (→ 404). */
    public static class ReferralNotFoundException extends RuntimeException {}

    /** Indicação não está mais 'pendente' (já convertida/expirada) — não pode converter (→ 409). */
    public static class ReferralNotPendingException extends RuntimeException {}

    /** Não conseguiu gerar um código único após várias tentativas (→ 500 improvável). */
    public static class CodeGenerationFailedException extends RuntimeException {}

    @Transactional
    public AcademiaReferral create(UUID companyId, UUID userId, UUID referrerContactId,
                                   String referredName, String referredPhone, Integer rewardPercent) {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generateCode();
            try {
                AcademiaReferral created = repository.insert(
                    companyId, referrerContactId, referredName, referredPhone, code, rewardPercent);
                auditLogger.log(companyId, userId, "academia_referral_created", "academia_referral",
                    created.id(), Map.of("code", created.code()));
                return created;
            } catch (DuplicateKeyException e) {
                // Colisão do unique (company_id, code) — tenta outro código.
            }
        }
        throw new CodeGenerationFailedException();
    }

    @Transactional
    public AcademiaReferral convert(UUID companyId, UUID userId, UUID id) {
        AcademiaReferral existing = repository.findById(companyId, id)
            .orElseThrow(ReferralNotFoundException::new);
        if (!"pendente".equals(existing.status())) {
            throw new ReferralNotPendingException();
        }
        if (repository.markConverted(companyId, id) == 0) {
            // Corrida rara: mudou de status entre o find e o update.
            throw new ReferralNotPendingException();
        }
        auditLogger.log(companyId, userId, "academia_referral_converted", "academia_referral", id, Map.of());
        return repository.findById(companyId, id).orElseThrow(ReferralNotFoundException::new);
    }

    public List<AcademiaReferral> list(UUID companyId, String statusFilter) {
        return repository.listByCompany(companyId, statusFilter);
    }

    public Optional<AcademiaReferral> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
