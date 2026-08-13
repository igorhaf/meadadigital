package com.meada.knowledge;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Extrai texto de um PDF via PDFBox 3.0.3 (Loader.loadPDF + PDFTextStripper).
 *
 * <p>Limite duro de {@code max-chars-per-document} (default 500k): um PDF gigante (livro
 * inteiro) geraria milhares de chunks/embeddings e estouraria custo/latência; truncamos
 * com WARN. PDFs digitalizados sem camada de texto (só imagem) retornam vazio — o caller
 * trata como documento sem conteúdo extraível (FAILED).
 */
@Component
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    private final int maxChars;

    public PdfTextExtractor(@Value("${knowledge.max-chars-per-document:500000}") int maxChars) {
        this.maxChars = maxChars;
    }

    /**
     * @param pdfBytes conteúdo bruto do PDF.
     * @return o texto extraído (trim); pode ser vazio se o PDF não tem camada de texto.
     * @throws PdfExtractionException se o PDF for inválido/corrompido.
     */
    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).strip();
            if (text.length() > maxChars) {
                log.warn("pdf text truncated: {} chars -> {} (max-chars-per-document)",
                    text.length(), maxChars);
                text = text.substring(0, maxChars);
            }
            return text;
        } catch (IOException e) {
            throw new PdfExtractionException("failed to read PDF: " + e.getMessage(), e);
        }
    }
}
