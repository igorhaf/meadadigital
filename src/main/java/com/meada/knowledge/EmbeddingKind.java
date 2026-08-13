package com.meada.knowledge;

/**
 * Tipo de texto sendo embedado — o modelo E5 exige um prefixo distinto para a CONSULTA
 * do usuário ("query: ") e para os TRECHOS indexados ("passage: "). O sidecar aplica o
 * prefixo a partir deste {@code value}.
 */
public enum EmbeddingKind {
    PASSAGE("passage"),
    QUERY("query");

    private final String value;

    EmbeddingKind(String value) {
        this.value = value;
    }

    /** String enviada no corpo do request ao sidecar ({@code kind}). */
    public String value() {
        return value;
    }
}
