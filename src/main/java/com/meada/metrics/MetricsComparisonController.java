package com.meada.metrics;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Comparação de métricas mês a mês do tenant (camada 5.23 #66). TENANT-ADMIN ONLY. Endpoint
 * próprio (em vez de alterar a RPC public.get_tenant_metrics) — mais seguro, não mexe no
 * payload existente do dashboard. Sob /admin/** (JwtAuthenticationFilter autentica).
 *
 * <p>Retorna {current, previous, deltas}, cada um com conversas criadas, mensagens recebidas,
 * mensagens enviadas e contatos ativos distintos do mês calendário (atual vs anterior). As
 * contas vivem em {@link MetricsQueryService}, compartilhadas com o export PDF (#65).
 *
 * <p>Autorização por role no método (padrão da camada 4): super-admin não tem company
 * (companyId null) → 403 forbidden_not_tenant_admin.
 */
@RestController
public class MetricsComparisonController {

    private final MetricsQueryService metricsQueryService;

    public MetricsComparisonController(MetricsQueryService metricsQueryService) {
        this.metricsQueryService = metricsQueryService;
    }

    /** GET /admin/metrics/comparison → {current, previous, deltas}. */
    @GetMapping("/admin/metrics/comparison")
    public ResponseEntity<Object> comparison(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        return ResponseEntity.ok(metricsQueryService.comparison(user.companyId()));
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }
}
