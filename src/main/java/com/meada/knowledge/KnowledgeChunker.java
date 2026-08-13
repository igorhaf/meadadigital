package com.meada.knowledge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Divide o texto extraído de um PDF em chunks para embedding (camada 5.13.c).
 *
 * <p>Estratégia: respeitar fronteiras semânticas, não cortar no meio de frase.
 * <ol>
 *   <li>Quebra em PARÁGRAFOS (linhas em branco). Parágrafo é a unidade natural.
 *   <li>Parágrafo maior que {@code chunkSize} → sub-quebra por SENTENÇA (BreakIterator
 *       pt-BR), acumulando sentenças até ~chunkSize.
 *   <li>Parágrafos curtos são AGRUPADOS até atingir o alvo (evita chunks minúsculos).
 *   <li>Entre chunks consecutivos, um OVERLAP de {@code overlap} chars (cauda do chunk
 *       anterior prefixada ao próximo) preserva contexto na fronteira — importante para
 *       o retrieval não perder informação que ficou partida.
 * </ol>
 */
@Component
public class KnowledgeChunker {

    private final int chunkSize;
    private final int overlap;

    public KnowledgeChunker(
            @Value("${knowledge.chunk-size-chars:800}") int chunkSize,
            @Value("${knowledge.chunk-overlap-chars:100}") int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /** @return os chunks na ordem do texto; lista vazia se o texto for vazio/branco. */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // 1. unidades base: parágrafos, e parágrafos grandes sub-quebrados por sentença.
        List<String> units = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= chunkSize) {
                units.add(p);
            } else {
                units.addAll(splitBySentence(p));
            }
        }

        // 2. agrupa unidades até ~chunkSize; aplica overlap entre chunks emitidos.
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (current.length() > 0 && current.length() + 1 + unit.length() > chunkSize) {
                chunks.add(current.toString());
                String tail = overlapTail(current.toString());
                current = new StringBuilder(tail);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(unit);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /** Quebra um parágrafo grande em pedaços <= chunkSize por fronteira de sentença (pt-BR). */
    private List<String> splitBySentence(String paragraph) {
        List<String> parts = new ArrayList<>();
        BreakIterator it = BreakIterator.getSentenceInstance(Locale.forLanguageTag("pt-BR"));
        it.setText(paragraph);
        StringBuilder buf = new StringBuilder();
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            String sentence = paragraph.substring(start, end);
            if (buf.length() > 0 && buf.length() + sentence.length() > chunkSize) {
                parts.add(buf.toString().strip());
                buf = new StringBuilder();
            }
            // Sentença sozinha maior que chunkSize (frase patológica): corta por chars.
            if (sentence.length() > chunkSize) {
                if (buf.length() > 0) {
                    parts.add(buf.toString().strip());
                    buf = new StringBuilder();
                }
                for (int i = 0; i < sentence.length(); i += chunkSize) {
                    parts.add(sentence.substring(i, Math.min(i + chunkSize, sentence.length())).strip());
                }
            } else {
                buf.append(sentence);
            }
        }
        if (buf.length() > 0) {
            parts.add(buf.toString().strip());
        }
        return parts;
    }

    /** Cauda do chunk anterior (últimos {@code overlap} chars) para prefixar o próximo. */
    private String overlapTail(String chunk) {
        if (overlap <= 0 || chunk.length() <= overlap) {
            return "";
        }
        return chunk.substring(chunk.length() - overlap);
    }
}
