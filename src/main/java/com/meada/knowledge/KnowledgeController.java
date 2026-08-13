package com.meada.knowledge;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints da base de conhecimento do tenant (camada 5.13.c). Autenticado pelo
 * JwtAuthenticationFilter; o tenant opera só os PRÓPRIOS documentos (company_id do JWT).
 *
 * <p>SÓ tenant-admin: super-admin não tem company_id (não pertence a tenant) — bloqueado
 * com 403 forbidden_not_tenant. Espelha o check manual de papel dos outros controllers.
 *
 * <p>Upload é SÍNCRONO: o POST retorna só quando o documento foi processado (ready) ou
 * falhou (failed). PDFs de FAQ/manual processam em segundos a dezenas de segundos.
 */
@RestController
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private static final long MAX_BYTES = 5L * 1024 * 1024;   // 5MB (espelha o yml)

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeDocumentRepository documentRepository;

    public KnowledgeController(KnowledgeIngestionService ingestionService,
                              KnowledgeDocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    @PostMapping("/admin/knowledge/documents")
    public ResponseEntity<Object> upload(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title) {
        ResponseEntity<Object> denied = requireTenant(user);
        if (denied != null) {
            return denied;
        }
        if (title == null || title.isBlank()) {
            return error(400, "title_required");
        }
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            return error(400, "invalid_file_size");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return error(400, "not_a_pdf");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            return error(400, "file_read_error");
        }

        // Cria o documento (commit) e processa. Falha de processamento deixa o documento
        // como 'failed' (visível na lista) e responde 422 — o tenant vê o que deu errado.
        KnowledgeDocument doc = ingestionService.createProcessing(user.companyId(), title.strip());
        try {
            ingestionService.process(doc, bytes);
        } catch (RuntimeException e) {
            log.warn("knowledge upload failed for document_id={}: {}", doc.id(), e.getMessage());
            return ResponseEntity.status(422).body(Map.of(
                "error", "Unprocessable", "reason", "ingestion_failed",
                "documentId", doc.id().toString()));
        }
        return documentRepository.findById(doc.id())
            .<ResponseEntity<Object>>map(d -> ResponseEntity.status(201).body(d))
            .orElseGet(() -> error(500, "document_vanished"));
    }

    @GetMapping("/admin/knowledge/documents")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        ResponseEntity<Object> denied = requireTenant(user);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(documentRepository.findByCompany(user.companyId()));
    }

    @DeleteMapping("/admin/knowledge/documents/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        ResponseEntity<Object> denied = requireTenant(user);
        if (denied != null) {
            return denied;
        }
        boolean deleted = documentRepository.softDelete(id, user.companyId());
        return deleted ? ResponseEntity.noContent().build() : error(404, "not_found");
    }

    @PatchMapping("/admin/knowledge/documents/{id}/active")
    public ResponseEntity<Object> setActive(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {
        ResponseEntity<Object> denied = requireTenant(user);
        if (denied != null) {
            return denied;
        }
        Boolean active = body.get("active");
        if (active == null) {
            return error(400, "active_required");
        }
        boolean updated = documentRepository.setActive(id, user.companyId(), active);
        return updated ? ResponseEntity.noContent().build() : error(404, "not_found");
    }

    /** 403 se não for tenant-admin (super-admin não tem company_id). null se ok. */
    private ResponseEntity<Object> requireTenant(AuthenticatedUser user) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return error(403, "forbidden_not_tenant");
        }
        return null;
    }

    private ResponseEntity<Object> error(int status, String reason) {
        String text = status == 403 ? "Forbidden" : status == 404 ? "Not Found" : "Bad Request";
        return ResponseEntity.status(status).body(Map.of("error", text, "reason", reason));
    }
}
