package com.meada.profiles.cursos.certificates;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Verificação PÚBLICA do certificado (onda Cursos 1, backlog #1) — fora da allowlist do
 * JwtAuthenticationFilter (rotas /public/** passam sem auth, espelho do CMS público). Renderiza
 * o certificado em HTML A4 imprimível; código inexistente → 404 com página de "não encontrado".
 */
@RestController
public class CursosCertificatePublicController {

    private final CursosCertificateService service;

    public CursosCertificatePublicController(CursosCertificateService service) {
        this.service = service;
    }

    @GetMapping(value = "/public/cursos/certificados/{code}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verify(@PathVariable String code) {
        Optional<Map<String, Object>> cert = service.findByCode(code.trim().toUpperCase());
        if (cert.isEmpty()) {
            return ResponseEntity.status(404).body(
                "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                    + "<title>Certificado não encontrado</title></head>"
                    + "<body style=\"font-family:sans-serif;text-align:center;padding:4rem\">"
                    + "<h1>Certificado não encontrado</h1>"
                    + "<p>O código informado não corresponde a nenhum certificado válido.</p>"
                    + "</body></html>");
        }
        Map<String, Object> c = cert.get();
        String issued = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .format(java.time.Instant.parse((String) c.get("issuedAt"))
                .atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate());
        String school = (String) c.get("schoolName");
        String html = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
            + "<title>Certificado — " + escape((String) c.get("studentName")) + "</title>"
            + "<style>@page{size:A4 landscape}body{font-family:Georgia,serif;text-align:center;"
            + "padding:6rem 4rem;border:12px double #334;margin:2rem}h1{font-size:2.2rem;margin:0}"
            + ".nome{font-size:1.8rem;margin:1.5rem 0 .5rem}.curso{font-size:1.3rem}"
            + ".code{margin-top:3rem;color:#667;font-size:.9rem;letter-spacing:.1em}</style></head><body>"
            + (school.isBlank() ? "" : "<p style=\"letter-spacing:.2em\">" + escape(school) + "</p>")
            + "<h1>CERTIFICADO DE CONCLUSÃO</h1>"
            + "<p>Certificamos que</p><p class=\"nome\"><strong>" + escape((String) c.get("studentName"))
            + "</strong></p><p>concluiu o curso</p><p class=\"curso\"><strong>"
            + escape((String) c.get("courseTitle")) + "</strong></p>"
            + "<p>em " + issued + "</p>"
            + "<p class=\"code\">Código de verificação: " + escape((String) c.get("code"))
            + " · autenticidade verificada nesta página</p>"
            + "</body></html>";
        return ResponseEntity.ok(html);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
