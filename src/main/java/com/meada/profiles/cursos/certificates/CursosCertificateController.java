package com.meada.profiles.cursos.certificates;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.cursos.CursosProfileGuard;
import com.meada.profiles.cursos.CursosProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Certificados emitidos (painel — onda Cursos 1, backlog #1). TENANT + perfil 'cursos'. */
@RestController
public class CursosCertificateController {

    private final CursosCertificateService service;
    private final CursosProfileGuard profileGuard;

    public CursosCertificateController(CursosCertificateService service, CursosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    @GetMapping("/api/cursos/certificates")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden", "reason", "forbidden_wrong_profile"));
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId)));
    }
}
