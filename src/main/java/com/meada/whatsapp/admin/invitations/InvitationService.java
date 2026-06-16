package com.meada.whatsapp.admin.invitations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Regras do fluxo de convite de admin extra (camada 5.16 #6).
 *
 * <p>createInvitation: valida o email, gera um token aleatório seguro, define validade
 * (token-validity-days, default 7), persiste. acceptInvitation: valida o convite (existe,
 * ativo, não expirado, email bate com o do convidado), e numa transação cria/atualiza a
 * linha em public.users (role='admin') e marca o convite usado.
 *
 * <p>O accept roda como service_role (fora do RLS): o convidado acabou de criar conta no
 * Supabase Auth e ainda NÃO tem linha em public.users — app.company_id() seria null, então
 * o RLS não se aplica. O isolamento aqui é lógico (o token + o match de email garantem que
 * só quem recebeu o convite, com a conta certa, aceita).
 */
@Service
public class InvitationService {

    // Regex de email simples (espelha o CHECK do banco). 1ª barreira no app; o banco revalida.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@]+@[^@]+\\.[^@]+$");

    // 32 bytes aleatórios → base64-url sem padding ≈ 43 chars. Espaço de 256 bits: não
    // adivinhável, não enumerável. SecureRandom (CSPRNG), não Random.
    private static final int TOKEN_BYTES = 32;

    private final TenantInvitationRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final int tokenValidityDays;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder tokenEncoder = Base64.getUrlEncoder().withoutPadding();

    public InvitationService(TenantInvitationRepository repository,
                             JdbcTemplate jdbcTemplate,
                             @Value("${invitations.token-validity-days:7}") int tokenValidityDays) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.tokenValidityDays = tokenValidityDays;
    }

    /**
     * Cria um convite para {@code email}, atribuído a {@code companyId}, emitido por
     * {@code invitedBy}. Valida o email (regex); gera token seguro; expires_at = agora +
     * token-validity-days. Retorna o convite criado (com o token, que o controller compõe
     * na inviteUrl).
     *
     * @throws InvalidInvitationEmailException email malformado
     */
    public TenantInvitation createInvitation(UUID companyId, UUID invitedBy, String email) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        String normalized = email == null ? "" : email.trim();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidInvitationEmailException();
        }
        String token = generateToken();
        Instant expiresAt = Instant.now().plus(tokenValidityDays, ChronoUnit.DAYS);
        return repository.insert(companyId, normalized, token, invitedBy, expiresAt);
    }

    /**
     * Aceita o convite identificado por {@code token} para a conta {@code userId} /
     * {@code userEmail} (do JWT do convidado). Valida em cascata e, numa transação:
     * cria/atualiza a linha em public.users (role='admin', company_id do convite) e marca
     * o convite como usado.
     *
     * <p>ON CONFLICT (id) DO UPDATE: se o convidado já tinha linha em users (já era admin
     * de outra empresa), TRANSFERE para a empresa do convite (decisão cravada — caso raro).
     * O email é atualizado para o do JWT (fonte de verdade do Auth).
     *
     * @return o companyId para o qual o usuário foi vinculado (a UI redireciona ao painel)
     * @throws InvitationNotFoundException     token não existe
     * @throws InvitationAlreadyUsedException  já aceito
     * @throws InvitationExpiredException      passou da validade
     * @throws InvitationEmailMismatchException email do JWT ≠ email do convite
     */
    @Transactional
    public UUID acceptInvitation(String token, UUID userId, String userEmail) {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        TenantInvitation invitation = repository.findByToken(token)
            .orElseThrow(InvitationNotFoundException::new);

        if (invitation.usedAt() != null) {
            throw new InvitationAlreadyUsedException();
        }
        if (invitation.expiresAt().isBefore(Instant.now())) {
            throw new InvitationExpiredException();
        }
        String jwtEmail = userEmail == null ? "" : userEmail.trim();
        if (!invitation.email().equalsIgnoreCase(jwtEmail)) {
            throw new InvitationEmailMismatchException();
        }

        // Cria/atualiza a linha em public.users. role='admin' (CHECK permite owner|admin|
        // agent). ON CONFLICT (id): transfere de empresa se o convidado já existia.
        jdbcTemplate.update(
            "insert into public.users (id, company_id, email, role) values (?, ?, ?, 'admin') "
                + "on conflict (id) do update set company_id = excluded.company_id, "
                + "role = 'admin', email = excluded.email, updated_at = now()",
            userId, invitation.companyId(), jwtEmail);

        // Marca usado. Se outro accept concorrente marcou primeiro (race), markUsed retorna
        // false → o convite já foi consumido; tratamos como already_used (rollback da tx).
        boolean marked = repository.markUsed(token, userId);
        if (!marked) {
            throw new InvitationAlreadyUsedException();
        }

        return invitation.companyId();
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return tokenEncoder.encodeToString(bytes);
    }
}
