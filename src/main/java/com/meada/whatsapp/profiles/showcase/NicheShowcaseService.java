package com.meada.whatsapp.profiles.showcase;

import com.meada.whatsapp.admin.audit.AdminAction;
import com.meada.whatsapp.admin.audit.AdminActionLogger;
import com.meada.whatsapp.profiles.ProfileType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolve e administra a VITRINE de nichos (produtos do Meada). A vitrine é COMPUTADA: itera os
 * nichos HARDCODED ({@link ProfileType}, exceto generic) e sobrepõe as linhas de {@code niche_showcase}
 * (featured + ordem). Ausência de linha = não-destaque, ordem no fim.
 *
 * <p>Consumido por:
 * <ul>
 *   <li>admin (root): {@link #grid()} (todos os nichos + featured/ordem) e {@link #set} (editar).</li>
 *   <li>público: {@link #publicList(boolean)} — home (featured) ou /produtos (todos), na ordem.</li>
 * </ul>
 *
 * <p>O conteúdo do card NÃO vive aqui: vem do próprio nicho (productName, subdomain, paletteId).
 * O frontend resolve a cor a partir do paletteId (palettes.ts). Limite de 6 destaques é regra de
 * negócio neste service.
 */
@Service
public class NicheShowcaseService {

    /** Limite de nichos em destaque (aparecem na home). */
    public static final int MAX_FEATURED = 6;

    /** Uma linha da grade do admin: nicho + featured + ordem. */
    public record NicheRow(String profileId, String productName, String subdomain,
                           String paletteId, boolean featured, int displayOrder) {}

    /** Um card público: dados do nicho pra montar o card (o front resolve a cor pelo paletteId). */
    public record PublicCard(String profileId, String productName, String subdomain, String paletteId) {}

    private final NicheShowcaseRepository repository;
    private final AdminActionLogger actionLogger;

    public NicheShowcaseService(NicheShowcaseRepository repository, AdminActionLogger actionLogger) {
        this.repository = repository;
        this.actionLogger = actionLogger;
    }

    public static class UnknownProfileException extends RuntimeException {}
    public static class TooManyFeaturedException extends RuntimeException {}

    /** Nicho que entra na vitrine? Todos os ProfileType EXCETO o generic (o "Meada"/root não é produto). */
    private static boolean showcasable(ProfileType p) {
        return p != ProfileType.GENERIC;
    }

    /** Sobreposição do banco: profileId → (featured, ordem). */
    private Map<String, NicheShowcaseRepository.Row> overrides() {
        Map<String, NicheShowcaseRepository.Row> m = new LinkedHashMap<>();
        for (NicheShowcaseRepository.Row r : repository.allRows()) {
            m.put(r.profileId(), r);
        }
        return m;
    }

    /** Grade do admin: TODOS os nichos showcasáveis, ordenados por display_order (depois productName). */
    public List<NicheRow> grid() {
        Map<String, NicheShowcaseRepository.Row> ov = overrides();
        List<NicheRow> rows = new ArrayList<>();
        for (ProfileType p : ProfileType.allActive()) {
            if (!showcasable(p)) {
                continue;
            }
            NicheShowcaseRepository.Row r = ov.get(p.id());
            rows.add(new NicheRow(
                p.id(), p.productName(), p.subdomain(), p.defaultPaletteId(),
                r != null && r.featured(),
                r != null ? r.displayOrder() : 0));
        }
        rows.sort(Comparator.comparingInt(NicheRow::displayOrder).thenComparing(NicheRow::productName));
        return rows;
    }

    /**
     * Lista pública pra vitrine. {@code featuredOnly=true} → só os destaques (home, até MAX_FEATURED);
     * false → todos os nichos (/produtos). Sempre na ordem (display_order, depois nome).
     */
    public List<PublicCard> publicList(boolean featuredOnly) {
        List<PublicCard> cards = new ArrayList<>();
        for (NicheRow r : grid()) {
            if (featuredOnly && !r.featured()) {
                continue;
            }
            cards.add(new PublicCard(r.profileId(), r.productName(), r.subdomain(), r.paletteId()));
        }
        return cards;
    }

    /**
     * Edita um nicho na vitrine (featured + ordem). Valida perfil conhecido+showcasável
     * (→ {@link UnknownProfileException}) e o limite de destaques: ao LIGAR featured num nicho que
     * ainda não é destaque, se já houver MAX_FEATURED → {@link TooManyFeaturedException}.
     */
    public void set(String profileId, boolean featured, int displayOrder, UUID rootUserId) {
        ProfileType profile = ProfileType.fromId(profileId).orElse(null);
        if (profile == null || !showcasable(profile)) {
            throw new UnknownProfileException();
        }
        if (featured && !repository.isFeatured(profileId) && repository.featuredCount() >= MAX_FEATURED) {
            throw new TooManyFeaturedException();
        }
        repository.upsert(profileId, featured, displayOrder, rootUserId);
        actionLogger.log(rootUserId, AdminAction.NICHE_SHOWCASE_UPDATED, AdminAction.TARGET_NICHE_SHOWCASE,
            null, Map.of("profile_id", profileId, "featured", featured, "display_order", displayOrder));
    }
}
