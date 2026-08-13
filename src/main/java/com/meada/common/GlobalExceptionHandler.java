package com.meada.common;

import com.meada.common.ValidationErrorResponse.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Set;

/**
 * Tratamento global de erros de validação de entrada. Resolve a tensão
 * diagnóstico-vs-opacidade da decisão 6:
 *
 * <ul>
 *   <li><b>Log do servidor</b>: SEMPRE completo (campos violados + regras + path +
 *       remote_addr). O operador precisa diagnosticar — em prod é a única via.
 *       NUNCA loga VALORES dos campos (podem ser PII: telefone, conteúdo).
 *   <li><b>Corpo HTTP</b>: depende do profile. DEV revela os campos violados
 *       (somos nós depurando). PROD retorna opaco — a Evolution é cliente externo,
 *       e nomear "data.key.id é obrigatório" vaza estrutura interna a quem manda
 *       o webhook (inclusive um atacante que passou pelo secret filter).
 * </ul>
 *
 * <p>Global (@RestControllerAdvice sem basePackages): validação de campo é
 * semântica universal — quando o painel chegar, herda o mesmo tratamento.
 *
 * <p>NÃO trata IllegalArgument/StateException: o service não as lança no fluxo
 * normal (instância desconhecida vira outcome, não exception). Se vazarem, viram
 * 500 — desejado, é bug nosso, não erro do cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final boolean prod;

    public GlobalExceptionHandler(Environment environment) {
        this.prod = Set.of(environment.getActiveProfiles()).contains("prod");
    }

    /** @Valid em @RequestBody falhou: um ou mais campos violam as constraints. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
            .toList();

        // Log SEMPRE completo (campos + regras), nunca os valores (PII). String
        // dos campos para o operador diagnosticar mesmo quando o corpo é opaco.
        String fields = violations.stream().map(FieldViolation::field).toList().toString();
        log.warn("validation failed path={} remote_addr={} violated_fields={}",
            request.getRequestURI(), request.getRemoteAddr(), fields);

        ValidationErrorResponse body = prod
            ? ValidationErrorResponse.opaque()
            : ValidationErrorResponse.detailed(violations);
        return ResponseEntity.badRequest().body(body);
    }

    /** JSON malformado / corpo ilegível (acontece após o secret filter). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        // Não logamos a mensagem da exception nem o corpo — podem conter PII de um
        // JSON parcialmente parseado. Só o fato e o path.
        log.warn("unreadable request body path={} remote_addr={}",
            request.getRequestURI(), request.getRemoteAddr());

        // Mesmo código de erro; sem violations (não há campos identificados num
        // JSON que nem parseou). Opaco em ambos os profiles aqui.
        return ResponseEntity.badRequest().body(ValidationErrorResponse.opaque());
    }
}
