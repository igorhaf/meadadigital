package com.meada.admin.companies;

import java.util.UUID;

/**
 * Nota interna não encontrada (na empresa indicada) num PATCH/DELETE de nota (camada
 * 6.1). O controller mapeia para 404 {error, reason: note_not_found}.
 */
public class NoteNotFoundException extends RuntimeException {
    public NoteNotFoundException(UUID noteId) {
        super("note " + noteId + " not found");
    }
}
