package com.meada.whatsapp.profiles.showcase;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Acesso a {@code niche_showcase} (vitrine de nichos). Tabela de PLATAFORMA — opera via service_role
 * (o root mexe fora do RLS). Guarda só os DESVIOS do default (featured + ordem); ausência de linha =
 * não-destaque, ordem 0. O resolver no {@link NicheShowcaseService} materializa a grade iterando
 * {@code ProfileType} e sobrepondo estas linhas.
 */
@Repository
public class NicheShowcaseRepository {

    /** Uma linha de vitrine (desvio do default). */
    public record Row(String profileId, boolean featured, int displayOrder) {}

    private final JdbcTemplate jdbcTemplate;

    public NicheShowcaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Todas as linhas (desvios), ordenadas por display_order — base da grade e do resolvido. */
    public List<Row> allRows() {
        return jdbcTemplate.query(
            "select profile_id, featured, display_order from niche_showcase order by display_order, profile_id",
            (rs, rn) -> new Row(rs.getString("profile_id"), rs.getBoolean("featured"), rs.getInt("display_order")));
    }

    /** Upsert de um nicho (featured + ordem). Carimba updated_at/by. */
    public void upsert(String profileId, boolean featured, int displayOrder, UUID updatedBy) {
        jdbcTemplate.update(
            "insert into niche_showcase (profile_id, featured, display_order, updated_by, updated_at) "
                + "values (?, ?, ?, ?, now()) "
                + "on conflict (profile_id) do update set "
                + "featured = excluded.featured, display_order = excluded.display_order, "
                + "updated_by = excluded.updated_by, updated_at = now()",
            profileId, featured, displayOrder, updatedBy);
    }

    /** Quantos nichos estão marcados como destaque (para o limite de 6). */
    public int featuredCount() {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from niche_showcase where featured = true", Integer.class);
        return n == null ? 0 : n;
    }

    /** Já é destaque? (para não recontar ao re-marcar o mesmo nicho.) */
    public boolean isFeatured(String profileId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from niche_showcase where profile_id = ? and featured = true",
            Integer.class, profileId);
        return n != null && n > 0;
    }
}
