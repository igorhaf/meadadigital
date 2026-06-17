package com.meada.whatsapp.profiles;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo MATERIALIZADO de perfis verticais (camada 7.0). Meada é um monolito que se
 * apresenta como N produtos verticais ("perfis"); cada perfil parece um produto distinto
 * para o cliente final.
 *
 * <p>Os perfis são HARDCODED — esta enum é a fonte de verdade no backend, espelhada 1:1 por
 * {@code frontend/lib/profiles/profile-type.ts}. O {@code ProfileTypeParityTest} garante que
 * os dois nunca divergem. Adicionar um perfil = editar os 2 arquivos + a CHECK constraint da
 * migration + rodar os testes de paridade. NÃO existe tabela de perfis.
 *
 * <p>Campos:
 * <ul>
 *   <li>{@code id} — string estável (persistida em companies.profile_id; nunca renomear em uso).
 *   <li>{@code productName} — label do "produto" exibido ao cliente (ex.: "ProcessoBot").
 *   <li>{@code subdomain} — subdomínio (sem o domínio base) que mapeia o perfil.
 *   <li>{@code defaultPaletteId} — paleta padrão (referência a lib/themes/palettes.ts).
 * </ul>
 */
public enum ProfileType {
    GENERIC("generic", "Meada", "meada", "meada-default"),
    LEGAL("legal", "ProcessoBot", "processo", "indigo"),
    DENTAL("dental", "DentalBot", "dental", "celeste"),
    SUSHI("sushi", "SushiBot", "sushi", "tijolo"),
    RESTAURANT("restaurant", "MesaBot", "mesa", "tijolo"),
    SALON("salon", "SalãoBot", "salao", "orquidea"),
    POUSADA("pousada", "PousadaBot", "pousada", "oceano"),
    ACADEMIA("academia", "AcademiaBot", "academia", "pinheiro"),
    PET("pet", "PetBot", "pet", "coral");

    private final String id;
    private final String productName;
    private final String subdomain;
    private final String defaultPaletteId;

    ProfileType(String id, String productName, String subdomain, String defaultPaletteId) {
        this.id = id;
        this.productName = productName;
        this.subdomain = subdomain;
        this.defaultPaletteId = defaultPaletteId;
    }

    public String id() {
        return id;
    }

    public String productName() {
        return productName;
    }

    public String subdomain() {
        return subdomain;
    }

    public String defaultPaletteId() {
        return defaultPaletteId;
    }

    /** Resolve um perfil pelo id estável (companies.profile_id). Optional vazio se inválido. */
    public static Optional<ProfileType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst();
    }

    /** Resolve um perfil pelo subdomínio (ex.: "processo" → LEGAL). Optional vazio se inválido. */
    public static Optional<ProfileType> bySubdomain(String subdomain) {
        if (subdomain == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(p -> p.subdomain.equals(subdomain)).findFirst();
    }

    /** Todos os perfis ativos (no MVP, todos os do enum). Ordem de declaração. */
    public static List<ProfileType> allActive() {
        return List.of(values());
    }
}
