package com.meada.profiles.comida.reports;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.comida.ComidaProfileGuard;
import com.meada.profiles.comida.ComidaProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Relatório de vendas do tenant comida (onda 1, backlog #15). TENANT + perfil 'comida' only.
 * Janela em MESES (default 3, clamp 1..24). Faturamento/ticket/top itens sobre os ENTREGUES;
 * horário de pico sobre a demanda (todos os pedidos criados).
 */
@RestController
public class ComidaReportsController {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ComidaReportsRepository repository;
    private final ComidaProfileGuard profileGuard;

    public ComidaReportsController(ComidaReportsRepository repository, ComidaProfileGuard profileGuard) {
        this.repository = repository;
        this.profileGuard = profileGuard;
    }

    @GetMapping("/api/comida/reports/summary")
    public ResponseEntity<Object> summary(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "3") int months) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden", "reason", "forbidden_wrong_profile"));
        }
        int window = Math.min(Math.max(months, 1), 24);
        Instant since = LocalDate.now(TENANT_ZONE).withDayOfMonth(1).minusMonths(window - 1L)
            .atStartOfDay(TENANT_ZONE).toInstant();
        ComidaReportsRepository.Totals totals = repository.totals(companyId, since);
        return ResponseEntity.ok(Map.of(
            "months", window,
            "totalCount", totals.count(),
            "totalCents", totals.totalCents(),
            "avgTicketCents", totals.avgTicketCents(),
            "byMonth", repository.byMonth(companyId, since),
            "topItems", repository.topItems(companyId, since, 10),
            "byHour", repository.byHour(companyId, since)));
    }
}
