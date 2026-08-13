package com.meada.admin.me;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /admin/me — identidade do usuário logado. É a fonte de verdade do papel para o
 * frontend decidir a UI (super-admin vs tenant-admin).
 *
 * <p>Único endpoint /admin que QUALQUER admin autenticado (super ou tenant) acessa: o
 * JwtAuthenticationFilter já fez a autenticação e populou o {@code authenticatedUser};
 * aqui não há autorização adicional por papel. Controller "burro": só mapeia o
 * AuthenticatedUser (do filtro) para o MeResponse.
 */
@RestController
public class MeController {

    @GetMapping("/admin/me")
    public MeResponse me(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        return MeResponse.from(user);
    }
}
