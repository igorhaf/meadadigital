package com.meada.whatsapp.admin.invitations;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test do {@link InvitationService} contra PostgreSQL real (Testcontainers).
 * Cobre criação (token/validade/email) e o accept em todos os ramos de erro + os caminhos
 * felizes (cria user, marca usado, transfere de empresa). Roda como service_role (igual
 * produção no fluxo de accept).
 */
class InvitationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InvitationService service;
    @Autowired
    private TenantInvitationRepository repository;

    private UUID seedCompany(String slug) {
        UUID companyId = UUID.randomUUID();
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            companyId, "Empresa " + slug, slug + "-" + companyId);
        return companyId;
    }

    /** Insere a linha em auth.users (FK de public.users) para o convidado. */
    private UUID seedAuthUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", userId);
        return userId;
    }

    // ---- createInvitation ---------------------------------------------------

    @Test
    @DisplayName("createInvitation: persiste convite ativo, token ~43 chars, expira ~7d")
    void createInvitation_persistsActiveInvitation() {
        UUID company = seedCompany("alpha");
        UUID inviter = seedAuthUser();

        TenantInvitation inv = service.createInvitation(company, inviter, "novo@empresa.com");

        assertThat(inv.id()).isNotNull();
        assertThat(inv.companyId()).isEqualTo(company);
        assertThat(inv.email()).isEqualTo("novo@empresa.com");
        assertThat(inv.token()).hasSize(43);            // 32 bytes base64-url sem padding
        assertThat(inv.usedAt()).isNull();
        // expira ~7 dias no futuro (tolerância de 1 min).
        long secondsToExpiry = ChronoUnit.SECONDS.between(Instant.now(), inv.expiresAt());
        assertThat(secondsToExpiry).isBetween(7L * 86400 - 60, 7L * 86400 + 60);

        // está persistido e ativo.
        assertThat(repository.findActiveByToken(inv.token())).isPresent();
    }

    @Test
    @DisplayName("createInvitation: email inválido → InvalidInvitationEmailException")
    void createInvitation_invalidEmail_throws() {
        UUID company = seedCompany("beta");
        assertThatThrownBy(() -> service.createInvitation(company, null, "naoehemail"))
            .isInstanceOf(InvalidInvitationEmailException.class);
    }

    @Test
    @DisplayName("createInvitation: max_admins atingido → PlanLimitExceededException (camada 5.20 #77)")
    void createInvitation_planLimitReached_throws() {
        UUID company = seedCompany("limit");
        // limite de 1 admin; já existe 1 usuário → o total (1) >= max_admins (1) → estoura.
        jdbcTemplate.update("update companies set max_admins = 1 where id = ?", company);
        UUID existing = seedAuthUser();
        jdbcTemplate.update(
            "insert into users (id, company_id, email, role) values (?, ?, ?, 'admin')",
            existing, company, "ja-existe@x.com");

        assertThatThrownBy(() -> service.createInvitation(company, null, "novo@x.com"))
            .isInstanceOf(PlanLimitExceededException.class);
    }

    // ---- acceptInvitation: caminho feliz ------------------------------------

    @Test
    @DisplayName("acceptInvitation válido: cria linha em users (role admin) + marca usado")
    void acceptInvitation_valid_createsUserAndMarksUsed() {
        UUID company = seedCompany("gamma");
        UUID inviter = seedAuthUser();
        TenantInvitation inv = service.createInvitation(company, inviter, "convidado@x.com");
        UUID invitee = seedAuthUser();

        UUID resolvedCompany = service.acceptInvitation(inv.token(), invitee, "convidado@x.com");

        assertThat(resolvedCompany).isEqualTo(company);
        // linha em users criada com role admin + company do convite.
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select company_id, email, role from users where id = ?", invitee);
        assertThat(row.get("company_id")).isEqualTo(company);
        assertThat(row.get("email")).isEqualTo("convidado@x.com");
        assertThat(row.get("role")).isEqualTo("admin");
        // convite marcado usado.
        Map<String, Object> usedRow = jdbcTemplate.queryForMap(
            "select used_at, used_by from tenant_invitations where token = ?", inv.token());
        assertThat(usedRow.get("used_at")).isNotNull();
        assertThat(usedRow.get("used_by")).isEqualTo(invitee);
        // não está mais ativo.
        assertThat(repository.findActiveByToken(inv.token())).isEmpty();
    }

    @Test
    @DisplayName("acceptInvitation: email case-insensitive (JWT 'Convidado@X.com' bate com 'convidado@x.com')")
    void acceptInvitation_emailCaseInsensitive_ok() {
        UUID company = seedCompany("delta");
        TenantInvitation inv = service.createInvitation(company, null, "convidado@x.com");
        UUID invitee = seedAuthUser();

        UUID resolved = service.acceptInvitation(inv.token(), invitee, "  Convidado@X.com  ");
        assertThat(resolved).isEqualTo(company);
    }

    // ---- acceptInvitation: ramos de erro ------------------------------------

    @Test
    @DisplayName("acceptInvitation: token inexistente → InvitationNotFoundException")
    void acceptInvitation_notFound_throws() {
        UUID invitee = seedAuthUser();
        assertThatThrownBy(() -> service.acceptInvitation("token-que-nao-existe", invitee, "a@b.com"))
            .isInstanceOf(InvitationNotFoundException.class);
    }

    @Test
    @DisplayName("acceptInvitation: convite expirado → InvitationExpiredException")
    void acceptInvitation_expired_throws() {
        UUID company = seedCompany("epsilon");
        TenantInvitation inv = service.createInvitation(company, null, "convidado@x.com");
        // força expiração no passado.
        jdbcTemplate.update("update tenant_invitations set expires_at = ? where token = ?",
            Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), inv.token());
        UUID invitee = seedAuthUser();

        assertThatThrownBy(() -> service.acceptInvitation(inv.token(), invitee, "convidado@x.com"))
            .isInstanceOf(InvitationExpiredException.class);
    }

    @Test
    @DisplayName("acceptInvitation: convite já usado → InvitationAlreadyUsedException")
    void acceptInvitation_alreadyUsed_throws() {
        UUID company = seedCompany("zeta");
        TenantInvitation inv = service.createInvitation(company, null, "convidado@x.com");
        UUID first = seedAuthUser();
        service.acceptInvitation(inv.token(), first, "convidado@x.com");
        UUID second = seedAuthUser();

        assertThatThrownBy(() -> service.acceptInvitation(inv.token(), second, "convidado@x.com"))
            .isInstanceOf(InvitationAlreadyUsedException.class);
    }

    @Test
    @DisplayName("acceptInvitation: email do JWT ≠ email do convite → InvitationEmailMismatchException")
    void acceptInvitation_emailMismatch_throws() {
        UUID company = seedCompany("eta");
        TenantInvitation inv = service.createInvitation(company, null, "convidado@x.com");
        UUID invitee = seedAuthUser();

        assertThatThrownBy(() -> service.acceptInvitation(inv.token(), invitee, "outro@y.com"))
            .isInstanceOf(InvitationEmailMismatchException.class);
    }

    @Test
    @DisplayName("acceptInvitation: convidado já era admin de outra empresa → transfere company+role")
    void acceptInvitation_existingUserDifferentCompany_transfers() {
        UUID companyA = seedCompany("theta");
        UUID companyB = seedCompany("iota");
        UUID invitee = seedAuthUser();
        // convidado já existe vinculado à companyA.
        jdbcTemplate.update(
            "insert into users (id, company_id, email, role) values (?, ?, ?, 'admin')",
            invitee, companyA, "movido@x.com");

        // convite da companyB para o mesmo email.
        TenantInvitation inv = service.createInvitation(companyB, null, "movido@x.com");
        UUID resolved = service.acceptInvitation(inv.token(), invitee, "movido@x.com");

        assertThat(resolved).isEqualTo(companyB);
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select company_id, role from users where id = ?", invitee);
        assertThat(row.get("company_id")).isEqualTo(companyB);   // transferido
        assertThat(row.get("role")).isEqualTo("admin");
    }
}
