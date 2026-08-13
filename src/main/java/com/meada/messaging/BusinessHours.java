package com.meada.messaging;

import java.time.LocalTime;

/**
 * Janela de atendimento de um dia da semana — domínio da tabela
 * {@code business_hours}.
 *
 * <p>Pode haver MÚLTIPLAS janelas por weekday (ex. 09:00-12:00 e 14:00-18:00 — a
 * pausa de almoço; o schema permite N linhas por dia via UNIQUE(company_id, weekday,
 * opens_at)). Um dia fechado é {@code closed=true} com horas null. Sem polimorfismo
 * (dia fechado vs aberto): é um record só, com opensAt/closesAt nullable — o
 * PromptBuilder formata ("fechado" vs "HH:mm–HH:mm").
 *
 * <p>Wrap pós-meia-noite é possível ({@code opensAt > closesAt} significa que a
 * janela cruza a meia-noite) — o domínio carrega os valores crus; a interpretação
 * é de quem formata/consulta.
 *
 * @param weekday  0=domingo .. 6=sábado
 * @param closed   dia fechado (sem janelas)
 * @param opensAt  abertura; null quando closed
 * @param closesAt fechamento; null quando closed
 */
public record BusinessHours(int weekday, boolean closed, LocalTime opensAt, LocalTime closesAt) {
}
