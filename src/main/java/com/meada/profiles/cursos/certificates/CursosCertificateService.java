package com.meada.profiles.cursos.certificates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Certificados de conclusão (onda Cursos 1, backlog #1). Gerado ao CONCLUIR a matrícula (código
 * único verificável na rota pública). Idempotente por enrollment (UNIQUE). A IA só ENVIA o link/
 * código que o backend gerou. Best-effort.
 */
@Service
public class CursosCertificateService {

    private static final Logger log = LoggerFactory.getLogger(CursosCertificateService.class);
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    public CursosCertificateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Gera (ou retorna o existente) o certificado da matrícula concluída. Devolve o code. */
    public String issue(UUID companyId, UUID enrollmentId, String studentName, String courseTitle,
                        String schoolName) {
        Optional<String> existing = jdbcTemplate.query(
                "select code from cursos_certificates where enrollment_id = ?",
                (rs, rn) -> rs.getString("code"), enrollmentId)
            .stream().findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        String code = generateCode();
        jdbcTemplate.update(
            "insert into cursos_certificates (company_id, enrollment_id, code, student_name, "
                + "course_title, school_name) values (?, ?, ?, ?, ?, ?) on conflict (enrollment_id) do nothing",
            companyId, enrollmentId, code, studentName, courseTitle, schoolName);
        log.info("cursos: certificado {} emitido p/ matrícula {}", code, enrollmentId);
        return code;
    }

    /** Certificado por código (rota pública de verificação). */
    public Optional<Map<String, Object>> findByCode(String code) {
        return jdbcTemplate.query(
                "select code, student_name, course_title, school_name, issued_at "
                    + "from cursos_certificates where code = ?",
                (rs, rn) -> Map.<String, Object>of(
                    "code", rs.getString("code"),
                    "studentName", rs.getString("student_name"),
                    "courseTitle", rs.getString("course_title"),
                    "schoolName", rs.getString("school_name") == null ? "" : rs.getString("school_name"),
                    "issuedAt", rs.getTimestamp("issued_at").toInstant().toString()),
                code)
            .stream().findFirst();
    }

    /** Lista os certificados do tenant (painel). */
    public List<Map<String, Object>> list(UUID companyId) {
        return jdbcTemplate.query(
            "select code, student_name, course_title, issued_at from cursos_certificates "
                + "where company_id = ? order by issued_at desc limit 200",
            (rs, rn) -> Map.<String, Object>of(
                "code", rs.getString("code"),
                "studentName", rs.getString("student_name"),
                "courseTitle", rs.getString("course_title"),
                "issuedAt", rs.getTimestamp("issued_at").toInstant().toString()),
            companyId);
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
