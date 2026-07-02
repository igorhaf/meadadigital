package com.meada.profiles.academia.reports;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Time;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test dos RELATÓRIOS do academia (docs #15) contra PostgreSQL real. Semeia matrículas
 * com status variados + aulas/junction e verifica o MRR, as contagens por status e a ocupação por
 * aula. Somente leitura — nenhum estado é mutado.
 */
class AcademiaReportsIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-0000000000b5");

    @Autowired
    private AcademiaReportsService service;

    private UUID planA;   // 15000
    private UUID planB;   // 20000
    private UUID classId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia R", "academia-r");
        planA = UUID.randomUUID();
        planB = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_plans (id, company_id, name, monthly_cents) values (?, ?, 'Mensal', 15000)",
            planA, COMPANY);
        jdbcTemplate.update("insert into academia_plans (id, company_id, name, monthly_cents) values (?, ?, 'Trimestral', 20000)",
            planB, COMPANY);
        classId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into academia_classes (id, company_id, name, modality, day_of_week, start_time, "
                + "duration_minutes, capacity) values (?, ?, 'Spinning', 'bike', 1, ?, 60, 10)",
            classId, COMPANY, Time.valueOf("07:00:00"));
    }

    /** Cria matrícula com o status/plano dado; se enrollInClass, adiciona à aula (junction). */
    private UUID seedMembership(UUID planId, int monthlyCents, String status, boolean enrollInClass) {
        UUID membership = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into academia_memberships (id, company_id, plan_id, student_name, plan_name, "
                + "plan_monthly_cents, status) values (?, ?, ?, 'Aluno', 'Plano', ?, ?)",
            membership, COMPANY, planId, monthlyCents, status);
        if (enrollInClass) {
            jdbcTemplate.update(
                "insert into academia_membership_classes (membership_id, class_id, class_name_snapshot, "
                    + "class_day_of_week_snapshot, class_start_time_snapshot, class_duration_minutes_snapshot, "
                    + "class_modality_snapshot) values (?, ?, 'Spinning', 1, ?, 60, 'bike')",
                membership, classId, Time.valueOf("07:00:00"));
        }
        return membership;
    }

    @Test
    @DisplayName("summary: MRR = soma dos planos das ATIVAS; contagem por status")
    void summary_mrrAndCounts() {
        // 2 ativas (15000 + 20000 = 35000 MRR), 1 suspensa (não entra no MRR), 1 cancelada.
        seedMembership(planA, 15000, "ativa", false);
        seedMembership(planB, 20000, "ativa", false);
        seedMembership(planA, 15000, "suspensa", false);
        seedMembership(planB, 20000, "cancelada", false);

        AcademiaSummaryReport r = service.summary(COMPANY);

        assertThat(r.mrrCents()).isEqualTo(35000L);
        assertThat(r.activeCount()).isEqualTo(2L);
        assertThat(r.suspendedCount()).isEqualTo(1L);
        assertThat(r.canceledCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("summary: tenant sem matrículas → tudo zero (MRR 0, sem NPE)")
    void summary_empty() {
        AcademiaSummaryReport r = service.summary(COMPANY);

        assertThat(r.mrrCents()).isZero();
        assertThat(r.activeCount()).isZero();
        assertThat(r.suspendedCount()).isZero();
        assertThat(r.canceledCount()).isZero();
    }

    @Test
    @DisplayName("occupancy: por aula, conta só matrículas ATIVAS x capacity")
    void occupancy_activeOnly() {
        seedMembership(planA, 15000, "ativa", true);      // ocupa
        seedMembership(planB, 20000, "ativa", true);      // ocupa
        seedMembership(planA, 15000, "suspensa", true);   // NÃO conta (só 'ativa' no relatório)
        seedMembership(planB, 20000, "cancelada", true);  // NÃO conta
        seedMembership(planA, 15000, "ativa", false);     // ativa mas fora da aula

        List<AcademiaOccupancyRow> rows = service.occupancy(COMPANY);

        assertThat(rows).hasSize(1);
        AcademiaOccupancyRow row = rows.get(0);
        assertThat(row.classId()).isEqualTo(classId);
        assertThat(row.className()).isEqualTo("Spinning");
        assertThat(row.capacity()).isEqualTo(10);
        assertThat(row.activeCount()).isEqualTo(2L);
    }
}
