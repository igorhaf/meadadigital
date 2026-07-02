package com.meada.profiles.academia.reports;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Relatórios do tenant academia (docs #15). Somente leitura — delega ao
 * {@link AcademiaReportsRepository}. Sem cache/auditoria (é leitura pura).
 */
@Service
public class AcademiaReportsService {

    private final AcademiaReportsRepository repository;

    public AcademiaReportsService(AcademiaReportsRepository repository) {
        this.repository = repository;
    }

    public AcademiaSummaryReport summary(UUID companyId) {
        return repository.summary(companyId);
    }

    public List<AcademiaOccupancyRow> occupancy(UUID companyId) {
        return repository.occupancy(companyId);
    }
}
