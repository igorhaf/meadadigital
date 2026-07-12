package com.meada.profiles;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Safety net da armadilha nº 1 do CLAUDE.md: a CHECK de {@code companies.profile_id} é
 * REESCRITA a cada perfil novo e a clonagem por sed já trocou o id NA LISTA (removendo os
 * demais perfis) em regressão real. Este teste lê a definição REAL da constraint no banco
 * (pós-migrations) e exige que ela contenha TODOS os ids do {@link ProfileType} — se um
 * perfil sumir da lista, o build quebra aqui, não em produção.
 */
class ProfileIdCheckConstraintIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("CHECK de companies.profile_id contém TODOS os ids do ProfileType (nenhum perfil removido por clonagem)")
    void checkConstraintListsEveryProfile() {
        List<String> defs = jdbcTemplate.queryForList(
            "select pg_get_constraintdef(oid) from pg_constraint "
                + "where conrelid = 'public.companies'::regclass and contype = 'c'",
            String.class);
        String profileCheck = defs.stream()
            .filter(d -> d.contains("profile_id"))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "CHECK de profile_id não encontrada em companies — constraints: " + defs));

        for (ProfileType profile : ProfileType.values()) {
            assertThat(profileCheck)
                .as("perfil '%s' precisa estar na CHECK de companies.profile_id (armadilha de clonagem: "
                    + "a reescrita da CHECK deve listar TODOS os perfis)", profile.id())
                .contains("'" + profile.id() + "'");
        }
    }
}
