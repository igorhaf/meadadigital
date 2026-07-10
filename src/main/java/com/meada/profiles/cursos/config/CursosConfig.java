package com.meada.profiles.cursos.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do tenant cursos (camada 8.20 / perfil cursos) — espelha cursos_config. Horário de
 * atendimento INFORMATIVO + notas institucionais. SEM agenda/slot (o curso é assíncrono). Ausente →
 * defaults (08:00–22:00). Análogo ao AcademiaConfig (camada 7.7) com o campo extra {@code notes}.
 */
public record CursosConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    String notes,
    boolean nudgeEnabled,
    int nudgeDays,
    String certificateBaseUrl) {

    public static CursosConfig defaultFor(UUID companyId) {
        return new CursosConfig(companyId, LocalTime.of(8, 0), LocalTime.of(22, 0), null, true, 7, null);
    }
}
