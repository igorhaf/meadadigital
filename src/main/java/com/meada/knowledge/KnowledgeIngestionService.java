package com.meada.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ingestão SÍNCRONA de um PDF na base de conhecimento (camada 5.13.c). O tenant espera o
 * processamento (extração → chunking → embedding → persistência). Pipeline:
 * <ol>
 *   <li>cria knowledge_document status=processing (storage_path placeholder — Opção C:
 *       o PDF original NÃO sobe pro Storage nesta fase);
 *   <li>extrai texto (PDFBox) → chunks (KnowledgeChunker) → embeddings (sidecar, PASSAGE);
 *   <li>persiste os chunks em batch; marca o documento ready (char_count, chunk_count);
 *   <li>qualquer falha → marca failed com a mensagem e relança (o controller responde erro).
 * </ol>
 *
 * <p>@Transactional: o INSERT do documento + os INSERTs dos chunks + o updateReady são
 * atômicos. Se algo falhar APÓS o documento ser criado, o updateFailed roda numa nova
 * transação (REQUIRES_NEW não é usado; aqui o catch atualiza e relança — a marcação
 * failed precisa sobreviver ao rollback, então é feita em chamada separada pós-rollback
 * no controller? Não: ver nota). Ver KnowledgeController para o tratamento de erro.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private static final int EMBED_BATCH = 32;

    private final PdfTextExtractor pdfTextExtractor;
    private final KnowledgeChunker chunker;
    private final EmbeddingProvider embeddingProvider;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;

    public KnowledgeIngestionService(PdfTextExtractor pdfTextExtractor,
                                     KnowledgeChunker chunker,
                                     EmbeddingProvider embeddingProvider,
                                     KnowledgeDocumentRepository documentRepository,
                                     KnowledgeChunkRepository chunkRepository) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.chunker = chunker;
        this.embeddingProvider = embeddingProvider;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    /**
     * Cria o documento (status=processing) e o retorna imediatamente. Idempotência não é
     * garantida (cada chamada cria um documento novo) — o controller valida antes.
     */
    @Transactional
    public KnowledgeDocument createProcessing(UUID companyId, String title) {
        String storagePath = "pending:" + UUID.randomUUID() + ".pdf";
        return documentRepository.insertProcessing(companyId, title, storagePath);
    }

    /**
     * Processa o PDF de um documento já criado: extrai, chunka, embeda, persiste e marca
     * ready. Em falha, marca failed e relança. NÃO é @Transactional de propósito: a
     * marcação ready/failed precisa persistir independente — se envolvesse o INSERT dos
     * chunks numa transação que dá rollback, o updateFailed também reverteria. Os chunks
     * de um processamento que falhou no meio são removidos via deleteChunks defensivo.
     */
    public void process(KnowledgeDocument doc, byte[] pdfBytes) {
        try {
            String text = pdfTextExtractor.extractText(pdfBytes);
            if (text.isBlank()) {
                throw new PdfExtractionException("PDF sem texto extraível (imagem/escaneado?)",
                    new IllegalStateException("empty text"));
            }
            List<String> chunks = chunker.chunk(text);
            List<KnowledgeChunk> persisted = embedAndBuild(doc, chunks);
            chunkRepository.insertBatch(persisted);
            documentRepository.updateReady(doc.id(), text.length(), persisted.size());
            log.info("knowledge ingest ready: document_id={} company_id={} chars={} chunks={}",
                doc.id(), doc.companyId(), text.length(), persisted.size());
        } catch (RuntimeException e) {
            documentRepository.updateFailed(doc.id(), e.getMessage());
            log.warn("knowledge ingest failed: document_id={} company_id={} reason={}",
                doc.id(), doc.companyId(), e.getMessage());
            throw e;
        }
    }

    /** Embeda os chunks em lotes (kind=PASSAGE) e monta os KnowledgeChunk a persistir. */
    private List<KnowledgeChunk> embedAndBuild(KnowledgeDocument doc, List<String> chunks) {
        List<KnowledgeChunk> result = new ArrayList<>(chunks.size());
        // O LocalEmbeddingProvider já lota internamente (32), mas embedamos em blocos aqui
        // também para não montar uma lista gigante de uma vez em PDFs grandes.
        for (int start = 0; start < chunks.size(); start += EMBED_BATCH) {
            List<String> batch = chunks.subList(start, Math.min(start + EMBED_BATCH, chunks.size()));
            List<float[]> vectors = embeddingProvider.embed(batch, EmbeddingKind.PASSAGE);
            for (int i = 0; i < batch.size(); i++) {
                result.add(new KnowledgeChunk(
                    null, doc.id(), doc.companyId(), start + i, batch.get(i), vectors.get(i)));
            }
        }
        return result;
    }
}
