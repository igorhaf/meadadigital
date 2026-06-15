package com.meada.whatsapp.admin.companies;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo do POST /admin/companies (criar empresa). Super-admin fornece name + slug
 * explícitos (decisão 4.2: slug não é gerado no backend — o frontend sugere derivando
 * do name, mas o valor final é do super-admin).
 *
 * <p>Validação via Bean Validation (@Valid no controller). Falha → 400 tratado pelo
 * {@link com.meada.whatsapp.common.GlobalExceptionHandler} (shape ValidationErrorResponse,
 * distinto do {error, reason} de autorização). status não vem aqui: default 'active' no
 * banco. id/created_at/updated_at também são do banco.
 *
 * @param name      nome da empresa, obrigatório.
 * @param slug      identificador único; minúsculas, números e hífens (sem hífen no início/fim
 *                  nem duplo). A unicidade é garantida pelo UNIQUE em companies.slug — colisão
 *                  vira 409 slug_already_exists no controller (não é checada aqui).
 * @param paletteId id da paleta de tema da empresa (camada 5.1.a). @NotBlank apenas — NÃO
 *                  validado contra o catálogo das 30 paletas: o catálogo vive só no frontend
 *                  (lib/themes/palettes.ts); duplicar a lista aqui acoplaria o banco à
 *                  curadoria visual e divergiria a cada paleta nova. Defesa em profundidade:
 *                  o PaletteSelect só deixa escolher dos 30 ids válidos, e o getPalette() do
 *                  frontend cai em 'meada-default' se um id desconhecido chegar ao banco.
 */
public record CreateCompanyRequest(
    @NotBlank(message = "name é obrigatório")
    String name,

    @NotBlank(message = "slug é obrigatório")
    @Pattern(
        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
        message = "slug inválido: use minúsculas, números e hífens (ex.: acme-corp)")
    String slug,

    @NotBlank(message = "paletteId é obrigatório")
    String paletteId
) {
}
