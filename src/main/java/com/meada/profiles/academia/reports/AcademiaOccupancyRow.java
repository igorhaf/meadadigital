package com.meada.profiles.academia.reports;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Ocupação de UMA aula do tenant academia (docs #15 — Relatórios): matrículas ATIVAS x capacidade.
 *
 * <p>{@code active_count} conta as matrículas de status {@code 'ativa'} que ocupam a aula (via
 * junction {@code academia_membership_classes}); {@code capacity} é a capacidade da aula. Somente
 * leitura.
 */
public record AcademiaOccupancyRow(
    @JsonProperty("class_id") UUID classId,
    @JsonProperty("class_name") String className,
    @JsonProperty("day_of_week") int dayOfWeek,
    int capacity,
    @JsonProperty("active_count") long activeCount) {}
