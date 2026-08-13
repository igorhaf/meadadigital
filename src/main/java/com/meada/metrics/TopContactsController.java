package com.meada.metrics;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Top contatos mais ativos do tenant (camada 5.23 #68). TENANT-ADMIN ONLY: retorna os 10
 * contatos da PRÓPRIA empresa com mais mensagens, em ordem decrescente de contagem. Optei
 * pelo backend (e não pelo SDK) porque a agregação count-group-by-order é correta e direta
 * em SQL e fica desajeitada via PostgREST. Sob /admin/** (JwtAuthenticationFilter autentica).
 *
 * <p>Autorização por role no método (padrão da camada 4, igual SearchController):
 * super-admin não tem company (companyId null) → 403 forbidden_not_tenant_admin. Isolamento
 * por empresa vem do companyId do próprio authenticatedUser (nunca de input do cliente).
 */
@RestController
public class TopContactsController {

    private final MetricsQueryService metricsQueryService;

    public TopContactsController(MetricsQueryService metricsQueryService) {
        this.metricsQueryService = metricsQueryService;
    }

    /** GET /admin/contacts/top → [{contactId, name, phoneNumber, messageCount}] top 10. */
    @GetMapping("/admin/contacts/top")
    public ResponseEntity<Object> top(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        List<Map<String, Object>> body = metricsQueryService.topContacts(user.companyId());
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }
}
