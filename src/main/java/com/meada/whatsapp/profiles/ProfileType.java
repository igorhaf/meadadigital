package com.meada.whatsapp.profiles;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo MATERIALIZADO de perfis verticais (camada 7.0). Meada é um SaaS de atendimento que se
 * apresenta como N produtos verticais ("perfis"/nichos); cada perfil habilita features (CMS, CRM,
 * agenda, etc.) conforme a demanda do nicho. O atendimento por IA ("bot") é apenas UMA das features —
 * o produto não é "um bot", por isso o label do nicho NÃO carrega o sufixo "Bot".
 *
 * <p>Os perfis são HARDCODED — esta enum é a fonte de verdade no backend, espelhada 1:1 por
 * {@code frontend/lib/profiles/profile-type.ts}. O {@code ProfileTypeParityTest} garante que
 * os dois nunca divergem. Adicionar um perfil = editar os 2 arquivos + a CHECK constraint da
 * migration + rodar os testes de paridade. NÃO existe tabela de perfis.
 *
 * <p>Campos:
 * <ul>
 *   <li>{@code id} — string estável (persistida em companies.profile_id; nunca renomear em uso).
 *   <li>{@code productName} — label do nicho exibido ao cliente (ex.: "Legal", "Restaurante").
 *   <li>{@code subdomain} — subdomínio (sem o domínio base) que mapeia o perfil.
 *   <li>{@code defaultPaletteId} — paleta padrão (referência a lib/themes/palettes.ts).
 * </ul>
 */
public enum ProfileType {
    GENERIC("generic", "Meada", "meada", "meada-default"),
    LEGAL("legal", "Legal", "juridico", "indigo"),
    DENTAL("dental", "Dental", "dental", "celeste"),
    SUSHI("sushi", "Sushi", "sushi", "tijolo"),
    RESTAURANT("restaurant", "Restaurante", "mesa", "tijolo"),
    SALON("salon", "Salão", "salao", "orquidea"),
    POUSADA("pousada", "Pousada", "pousada", "oceano"),
    ACADEMIA("academia", "Academia", "academia", "pinheiro"),
    PET("pet", "Pet", "pet", "coral"),
    OFICINA("oficina", "Oficina", "oficina", "aco"),
    NUTRI("nutri", "Nutri", "nutri", "salvia"),
    BARBEARIA("barbearia", "Barbearia", "barbearia", "grafite"),
    EVENTOS("eventos", "Eventos", "eventos", "ambar"),
    ESTETICA("estetica", "Estética", "estetica", "rosa-po"),
    COMIDA("comida", "Comida", "comida", "terracota"),
    FLORICULTURA("floricultura", "Floricultura", "floricultura", "rosa-po"),
    PIZZARIA("pizzaria", "Pizzaria", "pizzaria", "carmim"),
    ADEGA("adega", "Adega", "adega", "beringela"),
    ESCOLA("escola", "Escola", "escola", "mostarda");

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

    /** Resolve um perfil pelo subdomínio (ex.: "juridico" → LEGAL). Optional vazio se inválido. */
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

    /**
     * True se o slug colide com um subdomínio RESERVADO de nicho (ex.: "sushi", "comida").
     * Usado na criação/edição de empresa: o slug do tenant É o subdomínio dele, então nenhuma
     * empresa pode usar um slug igual ao subdomínio de um perfil — senão a desambiguação
     * nicho-vs-empresa do middleware ficaria indeterminada. Case-insensitive, trim.
     */
    public static boolean isReservedSubdomain(String slug) {
        if (slug == null) {
            return false;
        }
        return bySubdomain(slug.trim().toLowerCase()).isPresent();
    }
}
