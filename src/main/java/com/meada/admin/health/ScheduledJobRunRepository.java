package com.meada.admin.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Registro de execução dos jobs @Scheduled (camada 6.4). Cada execução grava uma row:
 * {@link #start} ao iniciar (status='running') e {@link #finishSuccess}/{@link #finishFailed}
 * ao terminar (preenche finished_at + status). Os jobs usam try/finally para garantir o update.
 *
 * <p>Instrumentamos o método @Scheduled (não o método público testável dos jobs) — os testes
 * chamam o público direto e não devem poluir esta tabela.
 */
@Repository
public class ScheduledJobRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public ScheduledJobRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inicia o registro de uma execução (status='running'); devolve o id para o finish. */
    public UUID start(String jobName) {
        return jdbcTemplate.queryForObject(
            "insert into scheduled_job_runs (job_name, status) values (?, 'running') returning id",
            UUID.class, jobName);
    }

    /** Marca a execução como bem-sucedida (finished_at=now()). */
    public void finishSuccess(UUID runId) {
        jdbcTemplate.update(
            "update scheduled_job_runs set status = 'success', finished_at = now() where id = ?",
            runId);
    }

    /** Marca a execução como falha (finished_at=now() + error_message truncado a 2000 chars). */
    public void finishFailed(UUID runId, String errorMessage) {
        String trimmed = errorMessage == null ? null
            : errorMessage.substring(0, Math.min(errorMessage.length(), 2000));
        jdbcTemplate.update(
            "update scheduled_job_runs set status = 'failed', finished_at = now(), error_message = ? "
                + "where id = ?",
            trimmed, runId);
    }
}
