package com.meada.whatsapp.profiles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do catálogo de perfis (camada 7.0): resolução por id/subdomínio e listagem.
 */
class ProfileTypeTest {

    @Test
    @DisplayName("fromId resolve id válido")
    void fromId_valid() {
        assertThat(ProfileType.fromId("legal")).contains(ProfileType.LEGAL);
        assertThat(ProfileType.fromId("generic")).contains(ProfileType.GENERIC);
    }

    @Test
    @DisplayName("fromId vazio para id inválido ou null")
    void fromId_invalid() {
        assertThat(ProfileType.fromId("nope")).isEmpty();
        assertThat(ProfileType.fromId(null)).isEmpty();
    }

    @Test
    @DisplayName("allActive lista os 15 perfis")
    void allActive() {
        List<ProfileType> all = ProfileType.allActive();
        assertThat(all).containsExactly(
            ProfileType.GENERIC, ProfileType.LEGAL, ProfileType.DENTAL, ProfileType.SUSHI,
            ProfileType.RESTAURANT, ProfileType.SALON, ProfileType.POUSADA, ProfileType.ACADEMIA,
            ProfileType.PET, ProfileType.OFICINA, ProfileType.NUTRI, ProfileType.BARBEARIA,
            ProfileType.EVENTOS, ProfileType.ESTETICA, ProfileType.COMIDA);
    }

    @Test
    @DisplayName("bySubdomain resolve subdomínio válido e vazio para inválido")
    void bySubdomain() {
        assertThat(ProfileType.bySubdomain("juridico")).contains(ProfileType.LEGAL);
        assertThat(ProfileType.bySubdomain("dental")).contains(ProfileType.DENTAL);
        assertThat(ProfileType.bySubdomain("inexistente")).isEmpty();
        assertThat(ProfileType.bySubdomain(null)).isEmpty();
    }
}
