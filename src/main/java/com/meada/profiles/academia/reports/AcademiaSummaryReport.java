package com.meada.profiles.academia.reports;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resumo financeiro/gerencial do tenant academia (docs #15 — Relatórios).
 *
 * <ul>
 *   <li>{@code mrr_cents}: soma de {@code plan_monthly_cents} das matrículas ATIVAS (receita mensal
 *       recorrente).</li>
 *   <li>{@code active_count}/{@code suspended_count}/{@code canceled_count}: contagem de matrículas
 *       por status.</li>
 * </ul>
 *
 * <p>Somente leitura — não altera vaga, matrícula, ou qualquer estado. A regra de vaga (suspensa
 * MANTÉM a vaga; só cancelada libera) NÃO é tocada por relatório.
 */
public record AcademiaSummaryReport(
    @JsonProperty("mrr_cents") long mrrCents,
    @JsonProperty("active_count") long activeCount,
    @JsonProperty("suspended_count") long suspendedCount,
    @JsonProperty("canceled_count") long canceledCount) {}
