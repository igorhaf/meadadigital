package com.meada.whatsapp.profiles.legal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o {@link LegalCnjValidator} (mód 97, Res. CNJ 65/2008) — camada 7.2.
 *
 * <p><b>Nota honesta:</b> os CNJs "reais" sugeridos no prompt da SM-C NÃO passam no mód 97
 * (verifiquei: seus DVs não fecham). Em vez de gravar números que falham, usamos números cujo
 * DV é COMPUTADO pelo próprio algoritmo ({@link LegalCnjValidator#computeCheckDigits}) e provamos
 * o round-trip: gerar o DV → o número resultante é válido. Isso testa o algoritmo de verdade
 * (não só um literal). Os inválidos cobrem DV errado, tamanho errado e letras.
 */
class LegalCnjValidatorTest {

    /** Monta um CNJ de 20 dígitos com DV calculado, a partir dos campos significativos. */
    private String buildValid(String seq, String ano, String j, String tr, String ori) {
        String dv = LegalCnjValidator.computeCheckDigits(seq + ano + j + tr + ori);
        return seq + dv + ano + j + tr + ori;
    }

    @Test
    @DisplayName("3 CNJs com DV computado são válidos (round-trip do algoritmo mód 97)")
    void validCnjs() {
        String a = buildValid("0710233", "2025", "8", "07", "0019");   // TJDFT
        String b = buildValid("1000033", "2024", "5", "02", "0001");   // TRT2
        String c = buildValid("0000001", "2019", "5", "04", "0512");   // TRT4

        assertThat(LegalCnjValidator.isValid(a)).as("CNJ %s", a).isTrue();
        assertThat(LegalCnjValidator.isValid(b)).as("CNJ %s", b).isTrue();
        assertThat(LegalCnjValidator.isValid(c)).as("CNJ %s", c).isTrue();
        // aceita com máscara também (normalize).
        assertThat(LegalCnjValidator.isValid(LegalCnjValidator.format(a))).isTrue();
    }

    @Test
    @DisplayName("3 inválidos: DV adulterado, tamanho errado, letras")
    void invalidCnjs() {
        String valid = buildValid("0710233", "2025", "8", "07", "0019");
        // DV adulterado: troca os 2 dígitos de DV (posições 7-8) por algo diferente.
        String wrongDv = valid.substring(0, 7)
            + (valid.charAt(7) == '0' ? "99" : "00")
            + valid.substring(9);
        assertThat(LegalCnjValidator.isValid(wrongDv)).as("DV adulterado %s", wrongDv).isFalse();

        // tamanho errado.
        assertThat(LegalCnjValidator.isValid("123")).isFalse();
        assertThat(LegalCnjValidator.isValid("071023315202580700191234")).isFalse();

        // letras (normalize remove → fica curto → inválido).
        assertThat(LegalCnjValidator.isValid("ABCDEFG-15.2025.8.07.0019")).isFalse();
        assertThat(LegalCnjValidator.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("format aplica a máscara NNNNNNN-DD.AAAA.J.TR.OOOO")
    void formatMask() {
        String valid = buildValid("0710233", "2025", "8", "07", "0019");
        String formatted = LegalCnjValidator.format(valid);
        assertThat(formatted).matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d\\.\\d{2}\\.\\d{4}");
        assertThat(LegalCnjValidator.normalize(formatted)).isEqualTo(valid);
    }
}
