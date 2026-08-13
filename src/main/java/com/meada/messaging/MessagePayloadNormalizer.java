package com.meada.messaging;

import com.meada.messaging.NormalizedJid.JidType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Classifica e normaliza o {@code remoteJid} cru da Evolution/Baileys.
 *
 * <p>Função pura e TOTAL: {@link #normalize(String)} aceita qualquer entrada
 * (inclusive null/malformada) e SEMPRE retorna um {@link NormalizedJid} — nunca
 * lança exceção. JID estranho é entrada PREVISTA de um sistema externo, não um
 * erro; classificar como {@link JidType#UNKNOWN} é mais honesto e mais simples
 * de consumir (switch exaustivo, sem try/catch) do que lançar.
 *
 * <p><b>Normalização E.164:</b> validação de FORMATO simples — após remover o
 * sufixo {@code @s.whatsapp.net}, exige 8–15 dígitos e prefixa {@code +}. NÃO
 * valida DDI/DDD por país: o WhatsApp já valida o número upstream (não entrega
 * mensagem de número inexistente); nosso papel é só guardar em formato canônico.
 * <b>Hardening possível:</b> libphonenumber entra se surgir caso real de negócio
 * (validar país/DDD, distinguir móvel/fixo) — não antes, seria cargo cult.
 *
 * <p>{@code @Component} (não static) para que o serviço consumidor possa
 * substituí-lo em teste se necessário. É stateless/thread-safe (sem campo mutável;
 * o Pattern é static final).
 */
@Component
public class MessagePayloadNormalizer {

    private static final String SUFFIX_USER = "@s.whatsapp.net";
    private static final String SUFFIX_GROUP = "@g.us";
    private static final String SUFFIX_BROADCAST = "@broadcast";

    /** 8 a 15 dígitos (faixa E.164), nada além de dígitos. */
    private static final Pattern E164_DIGITS = Pattern.compile("^[0-9]{8,15}$");

    public NormalizedJid normalize(String rawJid) {
        if (rawJid == null || rawJid.isBlank()) {
            return new NormalizedJid(JidType.UNKNOWN, null, rawJid);
        }

        if (rawJid.endsWith(SUFFIX_GROUP)) {
            return new NormalizedJid(JidType.GROUP, null, rawJid);
        }
        if (rawJid.endsWith(SUFFIX_BROADCAST)) {
            return new NormalizedJid(JidType.BROADCAST, null, rawJid);
        }
        if (rawJid.endsWith(SUFFIX_USER)) {
            return classifyUser(rawJid);
        }

        // Sem @ reconhecido: @lid, @newsletter, sufixo futuro, ou string sem '@'.
        return new NormalizedJid(JidType.UNKNOWN, null, rawJid);
    }

    /**
     * Extrai e valida o número de um JID de usuário. O número pode vir com um
     * sufixo de device/agente após ':' (ex. "5511999990001:12@s.whatsapp.net"
     * no formato Baileys) — descartamos a parte após ':' e o '+' opcional do
     * início antes de validar os dígitos.
     */
    private NormalizedJid classifyUser(String rawJid) {
        String local = rawJid.substring(0, rawJid.length() - SUFFIX_USER.length());

        // descarta sufixo de device/agente (":N") se presente
        int colon = local.indexOf(':');
        if (colon >= 0) {
            local = local.substring(0, colon);
        }
        // remove um '+' inicial se o JID já vier com ele (idempotência do prefixo)
        if (local.startsWith("+")) {
            local = local.substring(1);
        }

        if (!E164_DIGITS.matcher(local).matches()) {
            // número não-numérico ou comprimento fora de 8–15: não sabemos tratar
            return new NormalizedJid(JidType.UNKNOWN, null, rawJid);
        }

        return new NormalizedJid(JidType.USER, "+" + local, rawJid);
    }
}
