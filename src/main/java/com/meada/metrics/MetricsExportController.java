package com.meada.metrics;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Export PDF das métricas do tenant (camada 5.23 #65). TENANT-ADMIN ONLY. Gera um A4 retrato
 * em memória (PDFBox 3.0.3, a mesma dependência usada no RAG) com as métricas em TEXTO —
 * decisão cravada: "métricas em texto", sem gráficos rasterizados. Reusa a mesma lógica de
 * contas do {@link MetricsQueryService} (#66), uma fonte de verdade só.
 *
 * <p>Conteúdo: título "Relatório de Métricas", nome da empresa, data de geração e a comparação
 * mês a mês (atual, anterior, delta) por métrica. Retorna application/pdf com Content-Disposition
 * attachment. Sob /admin/** (JwtAuthenticationFilter autentica).
 *
 * <p>Autorização por role no método (padrão da camada 4): super-admin não tem company
 * (companyId null) → 403 forbidden_not_tenant_admin (JSON).
 */
@RestController
public class MetricsExportController {

    private static final Logger log = LoggerFactory.getLogger(MetricsExportController.class);

    private static final DateTimeFormatter GEN_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MetricsQueryService metricsQueryService;

    public MetricsExportController(MetricsQueryService metricsQueryService) {
        this.metricsQueryService = metricsQueryService;
    }

    /** GET /admin/metrics/export.pdf → PDF A4 com as métricas em texto. */
    @GetMapping("/admin/metrics/export.pdf")
    public ResponseEntity<Object> export(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
        }

        String companyName = metricsQueryService.companyName(user.companyId());
        Map<String, Object> comparison = metricsQueryService.comparison(user.companyId());

        byte[] pdf;
        try {
            pdf = renderPdf(companyName, comparison);
        } catch (IOException e) {
            log.warn("metrics pdf render failed for company_id={}: {}",
                user.companyId(), e.getMessage());
            return ResponseEntity.status(500)
                .body(Map.of("error", "Internal Server Error", "reason", "pdf_render_failed"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition
            .attachment().filename("metricas.pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    /**
     * Monta o PDF em memória: uma página A4 retrato, texto linha a linha de cima para baixo.
     * Helvetica (Standard14, sem embed de fonte). Layout simples: título, empresa, data e a
     * tabela de comparação (Métrica / Mês atual / Mês anterior / Variação).
     */
    @SuppressWarnings("unchecked")
    private byte[] renderPdf(String companyName, Map<String, Object> comparison) throws IOException {
        Map<String, Long> current = (Map<String, Long>) comparison.get("current");
        Map<String, Long> previous = (Map<String, Long>) comparison.get("previous");
        Map<String, Long> deltas = (Map<String, Long>) comparison.get("deltas");

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margin = 56f;            // ~2cm
            float top = PDRectangle.A4.getHeight() - margin;
            float leading = 20f;           // espaçamento entre linhas

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setLeading(leading);
                cs.newLineAtOffset(margin, top);

                // título
                cs.setFont(bold, 18);
                cs.showText("Relatório de Métricas");
                cs.newLine();
                cs.newLine();

                // empresa + data de geração
                cs.setFont(regular, 12);
                cs.showText("Empresa: " + (companyName != null ? companyName : "—"));
                cs.newLine();
                cs.showText("Gerado em: " + LocalDateTime.now().format(GEN_FMT));
                cs.newLine();
                cs.newLine();

                // subtítulo da seção
                cs.setFont(bold, 14);
                cs.showText("Comparação mês a mês");
                cs.newLine();
                cs.newLine();

                // cabeçalho da "tabela" (texto alinhado por colunas de largura fixa)
                cs.setFont(bold, 11);
                cs.showText(row("Métrica", "Mês atual", "Mês anterior", "Variação"));
                cs.newLine();

                // linhas (uma por métrica, na ordem do LinkedHashMap)
                cs.setFont(regular, 11);
                for (Map.Entry<String, Long> entry : current.entrySet()) {
                    String key = entry.getKey();
                    long cur = entry.getValue();
                    long prev = previous.getOrDefault(key, 0L);
                    long delta = deltas.getOrDefault(key, 0L);
                    cs.showText(row(label(key), String.valueOf(cur), String.valueOf(prev),
                        signed(delta)));
                    cs.newLine();
                }

                cs.endText();
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Rótulo legível (pt-BR) de cada chave camelCase da comparação. */
    private static String label(String key) {
        return switch (key) {
            case "conversations" -> "Conversas iniciadas";
            case "messagesInbound" -> "Mensagens recebidas";
            case "messagesOutbound" -> "Mensagens enviadas";
            case "activeContacts" -> "Contatos ativos";
            default -> key;
        };
    }

    /** Delta com sinal explícito (+/-) para leitura rápida no PDF. */
    private static String signed(long delta) {
        return delta > 0 ? "+" + delta : String.valueOf(delta);
    }

    /** Quatro colunas de largura fixa (padding com espaços) — alinhamento monoespaçado simples. */
    private static String row(String c1, String c2, String c3, String c4) {
        return pad(c1, 26) + pad(c2, 14) + pad(c3, 16) + c4;
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) {
            return s + " ";
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
