package com.meada.whatsapp.outbound;

import com.meada.whatsapp.messaging.BusinessHours;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * Decisão PURA "o instante atual cai dentro do horário de atendimento do tenant?".
 * Separada do OutboundService de propósito: a regra tem edge cases (weekday, múltiplas
 * janelas por dia, dia fechado, lista vazia) que merecem teste unitário sem subir Spring.
 * O OutboundService orquestra; esta classe só decide.
 *
 * <p><b>Fallback ABERTO</b> (decisão de produto): se o tenant NÃO configurou horários
 * ({@code hours} vazio), {@link #isInsideHours} retorna {@code true} — "não saber o
 * horário" ≠ "estar fechado". Sem config, a IA responde normalmente a qualquer hora.
 *
 * <p><b>Limitação conhecida (TODO):</b> janela que atravessa a meia-noite (ex.: abre
 * 22:00, fecha 02:00) NÃO é suportada — o teste {@code opens_at < closes_at} a trata
 * como inválida e o instante nunca cai "dentro". Tenants brasileiros típicos fecham
 * antes da meia-noite; suportar overnight é fase futura se aparecer demanda real.
 */
@Component
public class BusinessHoursGate {

    /**
     * O instante (weekday + horário) cai dentro de alguma janela ABERTA do tenant?
     *
     * @param hours   todas as linhas business_hours do tenant (dias fechados inclusos);
     *                lista vazia → fallback aberto (true).
     * @param weekday 0=domingo .. 6=sábado (mesma convenção de BusinessHours.weekday).
     * @param now     horário local a avaliar (no fuso do tenant — resolvido pelo caller).
     * @return true se dentro do horário OU sem config (fallback aberto); false se fora.
     */
    public boolean isInsideHours(List<BusinessHours> hours, int weekday, LocalTime now) {
        if (hours.isEmpty()) {
            return true;   // fallback aberto: sem config, não bloqueia
        }
        for (BusinessHours bh : hours) {
            if (bh.weekday() != weekday || bh.closed()) {
                continue;
            }
            LocalTime opens = bh.opensAt();
            LocalTime closes = bh.closesAt();
            // Defesa: janela aberta deve ter ambos os horários e opens < closes (sem
            // overnight). Linha incoerente é ignorada (não conta como janela válida).
            if (opens == null || closes == null || !opens.isBefore(closes)) {
                continue;
            }
            // dentro = opens <= now < closes (fecha no instante de fechamento).
            if (!now.isBefore(opens) && now.isBefore(closes)) {
                return true;
            }
        }
        return false;   // há config pra esse dia, mas nenhuma janela aberta contém 'now'
    }
}
