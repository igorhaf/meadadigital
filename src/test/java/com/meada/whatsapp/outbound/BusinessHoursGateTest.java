package com.meada.whatsapp.outbound;

import com.meada.whatsapp.messaging.BusinessHours;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste unitário PURO do {@link BusinessHoursGate} — sem Spring, sem banco. A lógica é
 * determinística (recebe a lista, o weekday e o horário), então cada edge case é uma
 * asserção direta. Convenção de weekday: 0=domingo .. 6=sábado.
 */
class BusinessHoursGateTest {

    private final BusinessHoursGate gate = new BusinessHoursGate();

    /** Janela aberta: closed=false, opens/closes preenchidos. */
    private static BusinessHours open(int weekday, String opens, String closes) {
        return new BusinessHours(weekday, false, LocalTime.parse(opens), LocalTime.parse(closes));
    }

    /** Dia fechado: closed=true, horas null. */
    private static BusinessHours closed(int weekday) {
        return new BusinessHours(weekday, true, null, null);
    }

    @Test
    @DisplayName("dentro da janela → true")
    void insideWindow() {
        var hours = List.of(open(1, "09:00", "18:00"));   // segunda
        assertThat(gate.isInsideHours(hours, 1, LocalTime.parse("10:00"))).isTrue();
    }

    @Test
    @DisplayName("antes da abertura → false")
    void beforeOpen() {
        var hours = List.of(open(1, "09:00", "18:00"));
        assertThat(gate.isInsideHours(hours, 1, LocalTime.parse("08:00"))).isFalse();
    }

    @Test
    @DisplayName("depois do fechamento → false")
    void afterClose() {
        var hours = List.of(open(1, "09:00", "18:00"));
        assertThat(gate.isInsideHours(hours, 1, LocalTime.parse("18:30"))).isFalse();
    }

    @Test
    @DisplayName("instante exato de fechamento → false (now < closes, exclusivo)")
    void exactCloseIsOutside() {
        var hours = List.of(open(1, "09:00", "18:00"));
        assertThat(gate.isInsideHours(hours, 1, LocalTime.parse("18:00"))).isFalse();
    }

    @Test
    @DisplayName("instante exato de abertura → true (opens <= now, inclusivo)")
    void exactOpenIsInside() {
        var hours = List.of(open(1, "09:00", "18:00"));
        assertThat(gate.isInsideHours(hours, 1, LocalTime.parse("09:00"))).isTrue();
    }

    @Test
    @DisplayName("múltiplas janelas (pausa de almoço): no intervalo → false; nas janelas → true")
    void lunchBreak() {
        var hours = List.of(open(1, "09:00", "12:00"), open(1, "14:00", "18:00"));
        assertThat(gate.isInsideHours(hours, 1, LocalTime.parse("13:00"))).isFalse();  // no almoço
        assertThat(gate.isInsideHours(hours, 1, LocalTime.parse("11:00"))).isTrue();   // janela 1
        assertThat(gate.isInsideHours(hours, 1, LocalTime.parse("15:00"))).isTrue();   // janela 2
    }

    @Test
    @DisplayName("dia closed=true → sempre false, qualquer hora")
    void closedDay() {
        var hours = List.of(closed(0));   // domingo fechado
        assertThat(gate.isInsideHours(hours, 0, LocalTime.parse("12:00"))).isFalse();
    }

    @Test
    @DisplayName("weekday diferente do configurado → false")
    void differentWeekday() {
        var hours = List.of(open(1, "09:00", "18:00"));   // só segunda
        assertThat(gate.isInsideHours(hours, 2, LocalTime.parse("10:00"))).isFalse();  // terça
    }

    @Test
    @DisplayName("lista vazia (sem config) → true (fallback aberto)")
    void emptyListFallbackOpen() {
        assertThat(gate.isInsideHours(List.of(), 3, LocalTime.parse("03:00"))).isTrue();
    }

    @Test
    @DisplayName("janela incoerente (overnight opens >= closes) → ignorada → false")
    void overnightWindowIgnored() {
        // 22:00-02:00 é overnight: opens não é before closes → linha ignorada.
        var hours = List.of(open(1, "22:00", "02:00"));
        assertThat(gate.isInsideHours(hours, 1, LocalTime.parse("23:00"))).isFalse();
    }
}
