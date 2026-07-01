package com.meada.knowledge;

import com.meada.AbstractIntegrationTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test da ingestão (PDFBox real + chunker real + repos reais contra
 * Postgres/pgvector via Testcontainers). O EmbeddingProvider é um FAKE determinístico
 * (vetor de 384 dims) — não dependemos do sidecar Python nos testes.
 */
@Import(KnowledgeIngestionServiceIntegrationTest.TestConfig.class)
class KnowledgeIngestionServiceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired
    private KnowledgeIngestionService ingestionService;
    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    private void seedCompany() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY, "Empresa K", "empresa-k");
    }

    /** PDF mínimo de 1 página com o texto dado, gerado em memória via PDFBox. */
    private static byte[] makePdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                // PDFBox não quebra linha sozinho; uma linha basta para o teste de texto.
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private long countChunks(UUID documentId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from knowledge_chunks where document_id = ?", Long.class, documentId);
    }

    @Test
    @DisplayName("PDF simples → documento ready, chunks persistidos com embedding 384-dim")
    void ingest_simplePdf_persistsDocumentAndChunks() throws Exception {
        seedCompany();
        byte[] pdf = makePdf("Atendemos de segunda a sexta das nove as dezoito horas.");

        KnowledgeDocument doc = ingestionService.createProcessing(COMPANY, "Horarios");
        assertThat(doc.status()).isEqualTo("processing");

        ingestionService.process(doc, pdf);

        KnowledgeDocument after = documentRepository.findById(doc.id(), COMPANY).orElseThrow();
        assertThat(after.status()).isEqualTo("ready");
        // Anti-IDOR (auditoria de segurança M2): findById scopado por company — buscar o MESMO id
        // com OUTRO company_id não retorna o documento (impede ler doc de outro tenant via UUID).
        assertThat(documentRepository.findById(doc.id(), UUID.randomUUID())).isEmpty();
        assertThat(after.charCount()).isGreaterThan(0);
        assertThat(after.chunkCount()).isGreaterThanOrEqualTo(1);
        assertThat(countChunks(doc.id())).isEqualTo(after.chunkCount());

        // embedding persistido como vector(384): valida a dimensão via vector_dims.
        Integer dims = jdbcTemplate.queryForObject(
            "select vector_dims(embedding) from knowledge_chunks where document_id = ? limit 1",
            Integer.class, doc.id());
        assertThat(dims).isEqualTo(384);
    }

    @Test
    @DisplayName("PDF sem texto extraível → documento failed com mensagem, sem chunks")
    void ingest_emptyPdf_failsCleanly() throws Exception {
        seedCompany();
        // PDF de 1 página em branco (sem showText) → texto vazio → PdfExtractionException.
        byte[] blankPdf;
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            d.save(out);
            blankPdf = out.toByteArray();
        }

        KnowledgeDocument doc = ingestionService.createProcessing(COMPANY, "Vazio");
        assertThatThrownBy(() -> ingestionService.process(doc, blankPdf))
            .isInstanceOf(PdfExtractionException.class);

        KnowledgeDocument after = documentRepository.findById(doc.id(), COMPANY).orElseThrow();
        assertThat(after.status()).isEqualTo("failed");
        assertThat(after.errorMessage()).isNotBlank();
        assertThat(countChunks(doc.id())).isZero();
    }

    @TestConfiguration
    static class TestConfig {
        /** Fake determinístico: cada texto vira um vetor 384-dim com 1.0 na posição 0. */
        @Bean
        @Primary
        EmbeddingProvider fakeEmbeddingProvider() {
            return (texts, kind) -> {
                List<float[]> out = new ArrayList<>(texts.size());
                for (int i = 0; i < texts.size(); i++) {
                    float[] v = new float[384];
                    v[0] = 1.0f;
                    out.add(v);
                }
                return out;
            };
        }
    }
}
