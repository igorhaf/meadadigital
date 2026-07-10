package com.meada.profiles.cursos.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.cursos.enrollments.CursosEnrollmentNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Nudge anti-abandono (onda Cursos 1, backlog #2): matrícula ATIVA parada há nudge_days no mesmo
 * módulo (última entrega em enrollment_progress; sem entrega = desde a matrícula) E com próximo
 * módulo existente → 1 toque motivador por episódio (nudge_sent_at re-armado quando o progresso
 * avança). Funil ativo (aluno matriculado), não é disparo à base → default ON. Best-effort.
 */
@Component
public class CursosNudgeJob {

    private static final Logger log = LoggerFactory.getLogger(CursosNudgeJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final CursosEnrollmentNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public CursosNudgeJob(JdbcTemplate jdbcTemplate, CursosEnrollmentNotifier notifier,
                          ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 12h30). Delega ao público p/ os testes. */
    @Scheduled(cron = "${cursos.nudge-cron:0 30 12 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("CursosNudgeJob");
        try {
            runNudges();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** Cutuca as matrículas paradas. Público e direto para os testes. */
    public int runNudges() {
        record Due(UUID id, UUID companyId, UUID conversationId, String studentName,
                   String nextModule, long done, long total) {}
        List<Due> due = jdbcTemplate.query(
            "select e.id, e.company_id, e.conversation_id, e.student_name, "
                + "(select m.title from cursos_modules m where m.course_id = e.course_id "
                + "  and m.id not in (select p2.module_id from cursos_enrollment_progress p2 "
                + "    where p2.enrollment_id = e.id) order by m.position limit 1) as next_module, "
                + "(select count(*) from cursos_enrollment_progress p3 where p3.enrollment_id = e.id) as done, "
                + "(select count(*) from cursos_modules m2 where m2.course_id = e.course_id) as total "
                + "from cursos_enrollments e "
                + "join companies co on co.id = e.company_id and co.profile_id = 'cursos' "
                + "left join cursos_config cfg on cfg.company_id = e.company_id "
                + "where coalesce(cfg.nudge_enabled, true) "
                + "and e.status = 'ativa' "
                + "and e.conversation_id is not null "
                + "and coalesce((select max(p.completed_at) from cursos_enrollment_progress p "
                + "     where p.enrollment_id = e.id), e.created_at) "
                + "    < now() - make_interval(days => coalesce(cfg.nudge_days, 7)) "
                + "and (e.nudge_sent_at is null or e.nudge_sent_at < "
                + "  coalesce((select max(p4.completed_at) from cursos_enrollment_progress p4 "
                + "     where p4.enrollment_id = e.id), e.created_at)) "
                + "order by e.company_id",
            (rs, rn) -> new Due((UUID) rs.getObject("id"), (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"), rs.getString("student_name"),
                rs.getString("next_module"), rs.getLong("done"), rs.getLong("total")));
        int touched = 0;
        for (Due d : due) {
            if (d.nextModule() == null) {
                continue;   // trilha concluída — nada a cutucar (a conclusão é outra transição).
            }
            try {
                notifier.notifyStatus(d.companyId(), d.conversationId(),
                    "Oi, " + d.studentName() + "! Você está em " + d.done() + "/" + d.total()
                        + " módulos — o próximo é \"" + d.nextModule() + "\". Bora continuar? "
                        + "É só responder por aqui que eu te mando! 📚");
                jdbcTemplate.update(
                    "update cursos_enrollments set nudge_sent_at = now() where id = ?", d.id());
                touched++;
            } catch (Exception e) {
                log.warn("cursos-nudge: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }
}
