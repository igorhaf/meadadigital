package com.meada.profiles.nutri.appointments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.nutri.patients.NutriPatientService;
import com.meada.profiles.nutri.plans.NutriPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test do contrato BEST-EFFORT do EntregaPlanoHandler (chassi F): falha TRANSITÓRIA de
 * repositório (RuntimeException no lookup) NÃO pode propagar — propagando, ela derrubaria o
 * envio da resposta da IA ao cliente no OutboundService. Regressão real: os try/catch cobriam
 * só o parse; os lookups rodavam descobertos. (Cenários felizes/barreira de contato: ver
 * EntregaPlanoHandlerTest, de integração.)
 */
class EntregaPlanoHandlerBestEffortTest {

    @Test
    @DisplayName("RuntimeException no lookup do paciente → Optional.empty, sem propagar")
    void runtimeExceptionOnLookup_returnsEmpty() {
        NutriPatientService patients = mock(NutriPatientService.class);
        when(patients.get(any(), any())).thenThrow(new RuntimeException("db fora do ar"));
        EntregaPlanoHandler handler = new EntregaPlanoHandler(new ObjectMapper(), patients,
            mock(NutriPlanService.class), mock(NutriAppointmentNotifier.class));

        Optional<String> delivered = handler.parseAndDeliver(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(),
            "Segue seu plano: <entrega_plano>{\"patient_id\":\"" + UUID.randomUUID() + "\"}</entrega_plano>");

        assertThat(delivered).isEmpty();
    }
}
