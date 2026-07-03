package com.meada.profiles.barbearia.reports;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.barbearia.BarberProfileGuard;
import com.meada.profiles.barbearia.BarberProfileGuard.WrongProfileException;
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
 * Relatório da barbearia (onda 1, backlog #15). TENANT + perfil 'barbearia' only. Janela em MESES
 * (default 6, clamp 1..24), do dia 01 do mês mais antigo (America/Sao_Paulo). Faturamento = SÓ
 * realizados, líquido (preço − desconto); inclui faltas/cancelados p/ taxa de no-show.
 */
@RestController
public class BarberReportsController {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final BarberReportsRepository repository;
    private final BarberProfileGuard profileGuard;

    public BarberReportsController(BarberReportsRepository repository, BarberProfileGuard profileGuard) {
        this.repository = repository;
        this.profileGuard = profileGuard;
    }

    @GetMapping("/api/barbearia/reports/summary")
    public ResponseEntity<Object> summary(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "6") int months) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden", "reason", "forbidden_wrong_profile"));
        }
        int window = Math.min(Math.max(months, 1), 24);
        Instant since = LocalDate.now(TENANT_ZONE).withDayOfMonth(1).minusMonths(window - 1L)
            .atStartOfDay(TENANT_ZONE).toInstant();
        BarberReportsRepository.Totals totals = repository.totals(companyId, since);
        return ResponseEntity.ok(Map.of(
            "months", window,
            "realizedCount", totals.realized(),
            "noShowCount", totals.noShows(),
            "cancelledCount", totals.cancelled(),
            "totalCents", totals.totalCents(),
            "byMonth", repository.byMonth(companyId, since),
            "byBarber", repository.byBarber(companyId, since),
            "byService", repository.byService(companyId, since)));
    }
}
