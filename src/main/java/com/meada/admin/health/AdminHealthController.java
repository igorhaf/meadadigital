package com.meada.admin.health;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saúde / jobs / erros da plataforma (camada 6.4). SUPER-ADMIN ONLY. READ-only.
 *
 * <ul>
 *   <li>GET /admin/health — resumo: webhook on/off (dry-run), último heartbeat, contadores 1h.
 *   <li>GET /admin/jobs — últimas 20 execuções por job_name (group by, latest each).
 *   <li>GET /admin/errors — últimos 50 erros, filtros source/createdAfter.
 * </ul>
 *
 * <p>Webhook está OFF no MVP (dry-run) → webhook_heartbeats vazio → a tela mostra "sem heartbeat".
 */
@RestController
public class AdminHealthController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final boolean webhookOff;

    public AdminHealthController(JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${evolution.dry-run:false}") boolean dryRun) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        // dry-run = não envia real = canal de saída desligado; tratamos como "webhook off" para a
        // tela de saúde (o inbound também fica desligado conscientemente — ver RISKS.md/incidente).
        this.webhookOff = dryRun;
    }

    private static boolean notSuper(AuthenticatedUser u) {
        return u.role() != AdminRole.SUPER_ADMIN;
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_super_admin"));
    }

    private long count(String sql) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }

    private Object parseJson(String raw) {
        if (raw == null) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    // -------------------------------------------------------------------------
    // GET /admin/health
    // -------------------------------------------------------------------------
    @GetMapping("/admin/health")
    public ResponseEntity<Object> health(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (notSuper(user)) return forbidden();

        Timestamp lastHb = jdbcTemplate.queryForObject(
            "select max(received_at) from webhook_heartbeats", Timestamp.class);

        Map<String, Object> body = new HashMap<>();
        body.put("webhookOff", webhookOff);
        body.put("lastHeartbeatAt", lastHb != null ? lastHb.toInstant().toString() : null);
        body.put("heartbeatsLastHour",
            count("select count(*) from webhook_heartbeats where received_at >= now() - interval '1 hour'"));
        body.put("jobsLastHour",
            count("select count(*) from scheduled_job_runs where started_at >= now() - interval '1 hour'"));
        body.put("jobsFailedLastHour",
            count("select count(*) from scheduled_job_runs where status = 'failed' "
                + "and started_at >= now() - interval '1 hour'"));
        body.put("errorsLastHour",
            count("select count(*) from error_log where created_at >= now() - interval '1 hour'"));
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // GET /admin/jobs — últimas 20 execuções por job_name
    // -------------------------------------------------------------------------
    @GetMapping("/admin/jobs")
    public ResponseEntity<Object> jobs(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String jobName) {
        if (notSuper(user)) return forbidden();

        // Window function: top 20 execuções MAIS RECENTES por job_name (não 20 globais — assim
        // um job que roda raramente não some debaixo de um que roda toda hora).
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (jobName != null && !jobName.isBlank()) {
            where.append(" where job_name = ?");
            args.add(jobName.trim());
        }
        List<Map<String, Object>> items = jdbcTemplate.query(
            "select id, job_name, started_at, finished_at, status, error_message from ("
                + "  select *, row_number() over (partition by job_name order by started_at desc) as rn "
                + "  from scheduled_job_runs" + where
                + ") t where rn <= 20 order by started_at desc",
            (rs, rn) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("jobName", rs.getString("job_name"));
                m.put("startedAt", rs.getTimestamp("started_at").toInstant().toString());
                Timestamp fin = rs.getTimestamp("finished_at");
                m.put("finishedAt", fin != null ? fin.toInstant().toString() : null);
                m.put("status", rs.getString("status"));
                m.put("errorMessage", rs.getString("error_message"));
                return m;
            }, args.toArray());
        return ResponseEntity.ok(Map.of("items", items));
    }

    // -------------------------------------------------------------------------
    // GET /admin/errors — últimos 50, filtros source/createdAfter
    // -------------------------------------------------------------------------
    @GetMapping("/admin/errors")
    public ResponseEntity<Object> errors(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String createdAfter) {
        if (notSuper(user)) return forbidden();

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (source != null && !source.isBlank()) { where.append(" and source = ?"); args.add(source.trim()); }
        try {
            if (createdAfter != null && !createdAfter.isBlank()) {
                where.append(" and created_at >= ?");
                args.add(Timestamp.from(Instant.parse(createdAfter)));
            }
        } catch (DateTimeParseException e) {
            return ResponseEntity.status(400).body(Map.of("error", "Bad Request", "reason", "invalid_date"));
        }

        List<Map<String, Object>> items = jdbcTemplate.query(
            "select id, source, message, stack_trace, context, created_at from error_log" + where
                + " order by created_at desc limit 50",
            (rs, rn) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("source", rs.getString("source"));
                m.put("message", rs.getString("message"));
                m.put("stackTrace", rs.getString("stack_trace"));
                m.put("context", parseJson(rs.getString("context")));
                m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                return m;
            }, args.toArray());
        return ResponseEntity.ok(Map.of("items", items));
    }
}
