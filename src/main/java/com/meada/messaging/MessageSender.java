package com.meada.messaging;

/**
 * Remetente de uma mensagem. Espelha o CHECK {@code sender in ('contact','ai','human')}
 * da tabela messages.
 *
 * <p>Valor de banco em {@link #dbValue()} (não derivado de name()), pelo mesmo
 * motivo de {@link MessageDirection}: mapeamento Java↔SQL explícito e estável a
 * refactors.
 *
 * <p>Combinações válidas (CHECK chk_messages_direction_sender):
 *   inbound→CONTACT; outbound→AI ou HUMAN. O repositório não valida a combinação
 *   (o CHECK do banco é a rede de segurança); o WebhookService monta o par correto.
 */
public enum MessageSender {
    CONTACT("contact"),
    AI("ai"),
    HUMAN("human");

    private final String dbValue;

    MessageSender(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
