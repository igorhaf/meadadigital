package com.meada.admin.companies;

import java.util.List;

/**
 * Página de empresas para GET /admin/companies (camada 6.1). items mantém o shape de
 * {@link CompanyResponse} por item; total é a contagem total que casa com os filtros
 * (sem paginação), para o frontend renderizar o paginador.
 *
 * @param items    empresas da página corrente
 * @param total    total de empresas que casam com os filtros (ignora page/pageSize)
 * @param page     índice da página (0-based)
 * @param pageSize tamanho da página
 */
public record CompanyPage(
    List<CompanyResponse> items, long total, int page, int pageSize) {
}
