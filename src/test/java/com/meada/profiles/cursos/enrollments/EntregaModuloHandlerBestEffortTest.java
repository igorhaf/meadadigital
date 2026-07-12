package com.meada.profiles.cursos.enrollments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.cursos.modules.CursosModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test do contrato BEST-EFFORT do EntregaModuloHandler (chassi F): (1) falha transitória
 * no lookup NÃO propaga; (2) módulo JÁ ENVIADO com recordProgress falhando não propaga — a
 * entrega vale, o progresso fica pendente com warn. Propagação derrubaria o envio da resposta
 * da IA ao cliente no OutboundService.
 */
class EntregaModuloHandlerBestEffortTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Test
    @DisplayName("RuntimeException no lookup da matrícula → false, sem propagar")
    void runtimeExceptionOnLookup_returnsFalse() {
        CursosEnrollmentRepository repo = mock(CursosEnrollmentRepository.class);
        when(repo.findById(any(), any())).thenThrow(new RuntimeException("db fora do ar"));
        EntregaModuloHandler handler = new EntregaModuloHandler(new ObjectMapper(), repo,
            mock(CursosEnrollmentNotifier.class));

        boolean delivered = handler.parseAndDeliver(COMPANY, UUID.randomUUID(), UUID.randomUUID(),
            "<entrega_modulo>{\"enrollment_id\":\"" + UUID.randomUUID() + "\"}</entrega_modulo>");

        assertThat(delivered).isFalse();
    }

    @Test
    @DisplayName("módulo ENVIADO mas recordProgress falha → true (entrega vale), sem propagar")
    void recordProgressFailureAfterSend_stillTrue() {
        UUID enrollmentId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CursosEnrollment enrollment = new CursosEnrollment(enrollmentId, courseId, "Curso", 10000, 0,
            null, null, contactId, "Aluno", "+5511999990000", LocalDate.now(), null, "ativa", null,
            Instant.now(), Instant.now());
        CursosModule module = new CursosModule(UUID.randomUUID(), courseId, 1, "Módulo 1",
            "CONTEÚDO DO MÓDULO", Instant.now(), Instant.now());

        CursosEnrollmentRepository repo = mock(CursosEnrollmentRepository.class);
        when(repo.findById(any(), any())).thenReturn(Optional.of(enrollment));
        when(repo.findNextModule(enrollmentId)).thenReturn(Optional.of(module));
        doThrow(new RuntimeException("falha ao gravar progresso")).when(repo).recordProgress(any(), any());
        CursosEnrollmentNotifier notifier = mock(CursosEnrollmentNotifier.class);
        when(notifier.sendText(any(), any(), any())).thenReturn(true);

        EntregaModuloHandler handler = new EntregaModuloHandler(new ObjectMapper(), repo, notifier);
        boolean delivered = handler.parseAndDeliver(COMPANY, UUID.randomUUID(), contactId,
            "<entrega_modulo>{\"enrollment_id\":\"" + enrollmentId + "\"}</entrega_modulo>");

        assertThat(delivered).isTrue();
    }
}
