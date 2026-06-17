package com.meada.whatsapp.profiles.legal;

import java.math.BigInteger;

/**
 * Validador do Número Único do CNJ (Resolução CNJ 65/2008) — módulo 97 (camada 7.2).
 *
 * <p>Formato: {@code NNNNNNN-DD.AAAA.J.TR.OOOO} (20 dígitos; hífen/pontos são cosméticos).
 * <ul>
 *   <li>NNNNNNN (7) — sequencial do processo na origem</li>
 *   <li>DD (2) — dígito verificador (mód 97)</li>
 *   <li>AAAA (4) — ano do ajuizamento</li>
 *   <li>J (1) — órgão/segmento do Judiciário</li>
 *   <li>TR (2) — tribunal</li>
 *   <li>OOOO (4) — unidade de origem</li>
 * </ul>
 *
 * <p><b>Algoritmo (mód 97 base 10000, ISO 7064):</b> rearranja os campos como
 * {@code NNNNNNN AAAA J TR OOOO DD} (o DV ao final) e o número de 20 dígitos é válido sse
 * {@code (esse número) mod 97 == 1}. Para CALCULAR o DV: {@code DD = 98 - ((NNNNNNN AAAA J TR
 * OOOO) * 100 mod 97)}. Regex NÃO basta — o DV depende dos demais campos.
 */
public final class LegalCnjValidator {

    private LegalCnjValidator() {}

    /** Remove tudo que não é dígito. (CNJ é só numérico; lowercase é no-op mas mantido por contrato.) */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase().replaceAll("[^0-9]", "");
    }

    /** Formata 20 dígitos como NNNNNNN-DD.AAAA.J.TR.OOOO. Retorna o input se não tiver 20 dígitos. */
    public static String format(String normalized) {
        String n = normalize(normalized);
        if (n.length() != 20) {
            return normalized;
        }
        return n.substring(0, 7) + "-" + n.substring(7, 9) + "." + n.substring(9, 13) + "."
            + n.substring(13, 14) + "." + n.substring(14, 16) + "." + n.substring(16, 20);
    }

    /**
     * Valida o número (com ou sem máscara) pelo mód 97. Reordena para
     * {@code NNNNNNN AAAA J TR OOOO DD} e verifica {@code mod 97 == 1}.
     */
    public static boolean isValid(String raw) {
        String n = normalize(raw);
        if (n.length() != 20) {
            return false;
        }
        String sequencial = n.substring(0, 7);   // NNNNNNN
        String dv = n.substring(7, 9);            // DD
        String resto = n.substring(9);            // AAAA J TR OOOO (11 dígitos)
        // Rearranjo ISO 7064: campos significativos + DV ao final.
        String rearranged = sequencial + resto + dv;
        try {
            return new BigInteger(rearranged).mod(BigInteger.valueOf(97))
                .equals(BigInteger.ONE);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Calcula o DV (2 dígitos) para os 18 dígitos significativos (NNNNNNN AAAA J TR OOOO, nesta
     * ordem rearranjada). Útil para gerar números válidos (seed). Entrada: 18 dígitos.
     */
    public static String computeCheckDigits(String eighteenDigits) {
        String d = normalize(eighteenDigits);
        if (d.length() != 18) {
            throw new IllegalArgumentException("esperados 18 dígitos significativos");
        }
        BigInteger r = new BigInteger(d + "00").mod(BigInteger.valueOf(97));
        BigInteger dv = BigInteger.valueOf(98).subtract(r);
        return String.format("%02d", dv.intValue());
    }
}
