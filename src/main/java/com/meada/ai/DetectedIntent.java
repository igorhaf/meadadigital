package com.meada.ai;

import java.time.Instant;

/**
 * Intent genérico detectado pela IA (camada 5.18) — usado por cancelamento (#51) e
 * reclamação (#52). Mesma forma que SchedulingIntent mas sem os campos específicos de
 * agendamento. detectedAt é fato do servidor (Instant.now() no parse).
 *
 * @param detectedAt quando o backend parseou a detecção
 * @param summary    resumo curto do que o modelo detectou (ex.: "quer cancelar o corte de amanhã")
 * @param rawExcerpt trecho exato da mensagem que disparou a detecção
 */
public record DetectedIntent(Instant detectedAt, String summary, String rawExcerpt) {
}
