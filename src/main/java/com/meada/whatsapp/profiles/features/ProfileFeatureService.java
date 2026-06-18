package com.meada.whatsapp.profiles.features;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.admin.audit.AdminAction;
import com.meada.whatsapp.admin.audit.AdminActionLogger;
import com.meada.whatsapp.profiles.ProfileFeature;
import com.meada.whatsapp.profiles.ProfileType;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolve e administra as feature flags por nicho (camada 9.0).
 *
 * <p>O default de toda feature é OFF: {@code profile_features} só guarda os DESVIOS, e o resolver
 * trata AUSÊNCIA de linha como false. A grade é COMPUTADA: itera as listas HARDCODED de perfis
 * ({@link ProfileType}) × features ({@link ProfileFeature}) e sobrepõe as linhas do banco.
 *
 * <p>{@link #enabledFor} é cacheado (Caffeine TTL 20s, keyed por profileId) e invalidado em todo
 * {@link #setFlag} — mesmo padrão dos context caches dos perfis. O {@code /admin/me} consome esse
 * resolvido pro tenant; o {@link ProfileFeatureGuard} idem.
 */
@Service
public class ProfileFeatureService {

    /** Uma feature na resposta da grade: key + label. */
    public record FeatureView(String key, String label) {}

    /** Uma linha da grade: o nicho + o estado de cada feature. */
    public record NicheRow(String profileId, String label, Map<String, Boolean> flags) {}

    /** A grade completa: as colunas (features) + as linhas (nichos). */
    public record Grid(List<FeatureView> features, List<NicheRow> niches) {}

    private final ProfileFeatureRepository repository;
    private final AdminActionLogger actionLogger;
    private final Cache<String, Set<ProfileFeature>> cache;

    public ProfileFeatureService(ProfileFeatureRepository repository, AdminActionLogger actionLogger) {
        this.repository = repository;
        this.actionLogger = actionLogger;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(20))
            .maximumSize(1000)
            .build();
    }

    public static class UnknownProfileException extends RuntimeException {}
    public static class UnknownFeatureException extends RuntimeException {}

    /**
     * Grade COMPUTADA: nichos (todos os {@link ProfileType}) × features (todos os
     * {@link ProfileFeature}), com as flags do banco sobrepostas. Ausência de linha = false.
     */
    public Grid grid() {
        // Sobreposição do banco: (profileId, featureKey) → enabled. Só os desvios existem.
        Map<String, Map<String, Boolean>> overrides = new LinkedHashMap<>();
        for (ProfileFeatureRepository.Row r : repository.allRows()) {
            overrides.computeIfAbsent(r.profileId(), k -> new LinkedHashMap<>())
                .put(r.featureKey(), r.enabled());
        }

        List<FeatureView> features = new ArrayList<>();
        for (ProfileFeature f : ProfileFeature.allActive()) {
            features.add(new FeatureView(f.key(), f.label()));
        }

        List<NicheRow> niches = new ArrayList<>();
        for (ProfileType p : ProfileType.allActive()) {
            Map<String, Boolean> flags = new LinkedHashMap<>();
            Map<String, Boolean> ov = overrides.getOrDefault(p.id(), Map.of());
            for (ProfileFeature f : ProfileFeature.allActive()) {
                flags.put(f.key(), ov.getOrDefault(f.key(), false)); // ausência = OFF
            }
            niches.add(new NicheRow(p.id(), p.productName(), flags));
        }
        return new Grid(features, niches);
    }

    /**
     * Liga/desliga uma feature de um nicho. Valida que ambos os ids são conhecidos
     * (→ {@link UnknownProfileException}/{@link UnknownFeatureException}), faz o upsert, audita
     * ({@code PROFILE_FEATURE_TOGGLED}) e invalida o cache do perfil.
     */
    public void setFlag(String profileId, String featureKey, boolean enabled, UUID rootUserId) {
        if (ProfileType.fromId(profileId).isEmpty()) {
            throw new UnknownProfileException();
        }
        if (ProfileFeature.fromKey(featureKey).isEmpty()) {
            throw new UnknownFeatureException();
        }
        repository.upsert(profileId, featureKey, enabled, rootUserId);
        actionLogger.log(rootUserId, AdminAction.PROFILE_FEATURE_TOGGLED, AdminAction.TARGET_PROFILE_FEATURE,
            null, Map.of("profile_id", profileId, "feature_key", featureKey, "enabled", enabled));
        cache.invalidate(profileId);
    }

    /**
     * Set das features LIGADAS para um nicho (resolvido: banco sobreposto ao default OFF). Cacheado
     * (TTL 20s). profileId null/desconhecido → set vazio (super-admin sem empresa, ou perfil inválido).
     */
    public Set<ProfileFeature> enabledFor(String profileId) {
        if (profileId == null || ProfileType.fromId(profileId).isEmpty()) {
            return EnumSet.noneOf(ProfileFeature.class);
        }
        return cache.get(profileId, this::resolveEnabled);
    }

    private Set<ProfileFeature> resolveEnabled(String profileId) {
        Set<String> keys = repository.enabledKeysFor(profileId);
        Set<ProfileFeature> result = EnumSet.noneOf(ProfileFeature.class);
        for (String k : keys) {
            ProfileFeature.fromKey(k).ifPresent(result::add); // ignora keys órfãs (feature removida do enum)
        }
        return result;
    }

    /** Mapa de TODAS as features → estado resolvido pro nicho (pro /admin/me). Ausência = false. */
    public Map<String, Boolean> resolvedMap(String profileId) {
        Set<ProfileFeature> on = enabledFor(profileId);
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (ProfileFeature f : ProfileFeature.allActive()) {
            map.put(f.key(), on.contains(f));
        }
        return map;
    }
}
