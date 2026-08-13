package com.meada.profiles;

import org.springframework.stereotype.Component;

/**
 * Segmento de persona do system prompt por perfil (camada 7.0). Cada perfil vertical injeta,
 * ANTES do prompt base do tenant (system-template.txt), uma instrução de persona que dá ao
 * "produto" a sua voz — sem substituir nada do prompt genérico (que continua intacto como
 * fallback).
 *
 * <p>Esta SM entrega só o TOM (persona). As regras/ferramentas específicas de cada perfil
 * (peças jurídicas, fluxo odonto, cardápio sushi) vêm em SM-B/C/D. generic → segmento vazio
 * (o prompt base já é o comportamento genérico).
 */
@Component
public class ProfilePromptContext {

    private static final String LEGAL =
        "Você é o assistente virtual de um escritório de advocacia. Tom formal e respeitoso, "
            + "terminologia jurídica correta. NUNCA dê opinião ou aconselhamento jurídico: para "
            + "qualquer dúvida substantiva sobre o caso, oriente o cliente a 'consultar o advogado "
            + "responsável'. Seu papel é confirmar o atendimento, agendar reuniões/consultas e "
            + "organizar informações — não interpretar o mérito jurídico.";

    private static final String DENTAL =
        "Você é o assistente virtual de uma clínica odontológica. Tom técnico mas acolhedor, com "
            + "empatia por quem tem medo de dentista. Esclareça procedimentos de forma didática e "
            + "tranquilizadora e sugira agendamento. NUNCA dê diagnóstico ou plano de tratamento — "
            + "isso é exclusivo do dentista; para dúvidas clínicas, encaminhe ao profissional.";

    private static final String SUSHI =
        "Você é atendente de um restaurante de sushi. Tom descontraído mas profissional. Conheça o "
            + "cardápio, sugira combinações e harmonizações, e confirme o pedido sempre com o valor "
            + "total e o endereço de entrega. Seja ágil e simpático no atendimento.";

    /**
     * Segmento de persona para o perfil, ou "" para generic / perfil desconhecido (fallback
     * seguro: o prompt base já cobre o genérico). Quando não-vazio, vem com um cabeçalho próprio
     * e uma quebra de linha ao final, pronto para concatenar ANTES do prompt base.
     */
    public String segmentFor(String profileId) {
        ProfileType profile = ProfileType.fromId(profileId).orElse(ProfileType.GENERIC);
        String body = switch (profile) {
            case LEGAL -> LEGAL;
            case DENTAL -> DENTAL;
            case SUSHI -> SUSHI;
            case GENERIC -> "";
        };
        if (body.isEmpty()) {
            return "";
        }
        return "# Persona (" + profile.productName() + ")\n" + body + "\n\n";
    }
}
