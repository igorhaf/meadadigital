package com.meada.knowledge;

/** PDF inválido/corrompido/protegido — não foi possível extrair texto. */
public class PdfExtractionException extends RuntimeException {
    public PdfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
