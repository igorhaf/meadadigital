package com.meada.messaging;

/**
 * Direção de uma mensagem. Espelha o CHECK {@code direction in ('inbound','outbound')}
 * da tabela messages.
 *
 * <p>O valor de banco é guardado em {@link #dbValue()}, NÃO derivado de
 * {@code name().toLowerCase()} — assim o mapeamento Java↔SQL é fonte única e
 * explícita: renomear a constante Java (refactor) não quebra silenciosamente o
 * valor persistido (o compilador não pegaria, o schema não muda). Mesmo princípio
 * de não confiar em transformação implícita entre companyId/company_id.
 */
public enum MessageDirection {
    INBOUND("inbound"),
    OUTBOUND("outbound");

    private final String dbValue;

    MessageDirection(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
