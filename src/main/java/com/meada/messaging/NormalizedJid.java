package com.meada.messaging;

/**
 * Resultado da classificação de um {@code remoteJid} da Evolution/Baileys.
 *
 * <p>O normalizer é TOTAL: toda entrada (inclusive null ou malformada) mapeia
 * para um {@code NormalizedJid} — nunca lança exceção. Quem consome decide o que
 * fazer com cada {@link JidType}.
 *
 * @param type        classificação do JID
 * @param phoneNumber número em E.164 com prefixo {@code +} — SÓ preenchido quando
 *                    {@code type == USER}; null nos demais casos
 * @param rawJid      o JID exatamente como veio no payload (pode ser null se a
 *                    entrada foi null). Sempre preservado para rastreabilidade no
 *                    log — o serviço loga isto ao ignorar GROUP/BROADCAST/UNKNOWN.
 */
public record NormalizedJid(JidType type, String phoneNumber, String rawJid) {

    /**
     * Tipo de um JID do WhatsApp.
     *
     * <ul>
     *   <li>{@code USER} — conversa 1:1 ({@code @s.whatsapp.net}); tem phoneNumber.
     *   <li>{@code GROUP} — grupo ({@code @g.us}); sem phoneNumber. Ignorado no MVP.
     *   <li>{@code BROADCAST} — lista de transmissão/status ({@code @broadcast});
     *       sem phoneNumber. Ignorado no MVP.
     *   <li>{@code UNKNOWN} — qualquer outra coisa: sufixo não-suportado (ex.
     *       {@code @lid}, {@code @newsletter}), JID sem {@code @}, número inválido
     *       (não-numérico ou comprimento fora de 8–15), ou entrada null. Ignorado
     *       no MVP, com o rawJid logado para observabilidade.
     * </ul>
     *
     * <p>Switch sobre este enum em Java 17+ é checado na compilação — um tipo novo
     * obriga o consumidor a tratá-lo.
     */
    public enum JidType {
        USER,
        GROUP,
        BROADCAST,
        UNKNOWN
    }
}
