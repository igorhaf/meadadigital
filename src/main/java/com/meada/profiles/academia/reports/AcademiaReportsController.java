package com.meada.profiles.academia.reports;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.academia.AcademiaProfileGuard;
import com.meada.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Relatórios do tenant academia (docs #15). TENANT + perfil 'academia' only (guard). SOMENTE
 * LEITURA — nenhum endpoint muta estado.
 */
@RestController
public class AcademiaReportsController {

    private final AcademiaReportsService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaReportsController(AcademiaReportsService service, AcademiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Resumo: MRR (matrículas ativas) + contagem por status. */
    @GetMapping("/api/academia/reports/summary")
    public ResponseEntity<Object> summary(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.summary(companyId));
    }

    /** Ocupação por aula: matrículas ativas x capacidade. */
    @GetMapping("/api/academia/reports/occupancy")
    public ResponseEntity<Object> occupancy(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.occupancy(companyId)));
    }
}
