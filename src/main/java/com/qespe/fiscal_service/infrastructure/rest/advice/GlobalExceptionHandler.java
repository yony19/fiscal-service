package com.qespe.fiscal_service.infrastructure.rest.advice;

import com.qespe.fiscal_service.core.dto.common.ApiError;
import com.qespe.fiscal_service.shared.exception.AccessDeniedException;
import com.qespe.fiscal_service.shared.exception.BadRequestException;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralised HTTP error mapping.
 *
 * <p><b>Logging contract:</b> every handler logs the underlying exception at
 * an appropriate level. Earlier {@link #handleGeneric} just returned 500 with
 * no log — debugging endpoint failures meant attaching a remote debugger
 * because nothing surfaced in the container logs. Now {@code ERROR} with full
 * stack trace, so a {@code docker logs pos-fiscal-service | grep ERROR}
 * actually finds the cause.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        // Expected during normal browsing — DEBUG, not ERROR, to avoid noise.
        log.debug("Not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.warn("Business rule violation: {}", ex.getMessage());
        // 422 (semantic violation) rather than 400 — frontend treats 400 as
        // "your form is malformed" and 422 as "your form is valid but we
        // can't accept it". Different UX surfaces.
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        // 400-class is operator error; WARN gives ops visibility without
        // crying wolf.
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    /**
     * JWT expired / malformed / signature invalid — these bubble up through the
     * permission aspect when {@link io.jsonwebtoken} parses the token.
     * Map to 401 so the frontend's axios interceptor can redirect to /login.
     * Without this, the user sees an opaque 500 and stays stuck on a page
     * that can't recover until they manually clear cookies.
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiError> handleExpiredJwt(ExpiredJwtException ex, HttpServletRequest req) {
        log.info("JWT expired: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "JWT expirado. Vuelve a iniciar sesión.", req.getRequestURI());
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiError> handleJwt(JwtException ex, HttpServletRequest req) {
        log.warn("JWT invalid: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "JWT inválido. Vuelve a iniciar sesión.", req.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied: {}", ex.getMessage());
        // 401 if the cause looks like "no token / bad token", else 403.
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        HttpStatus status = (msg.contains("ausente") || msg.contains("inválido") || msg.contains("expir"))
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.FORBIDDEN;
        return build(status, ex.getMessage() != null ? ex.getMessage() : "Acceso denegado", req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        log.warn("Validation failed on body: {}", ex.getMessage());
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldErrorMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, req.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    /**
     * Violacion de integridad en BD (NOT NULL, longitud, unique, FK). Devuelve
     * un mensaje limpio al cliente — NUNCA el SQL ni el nombre de la tabla, que
     * filtraban detalle interno. El detalle completo queda en el log del server.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.error("Data integrity violation: {}", ex.getMostSpecificCause().getMessage(), ex);
        return build(HttpStatus.UNPROCESSABLE_ENTITY,
                "No se pudo completar la operación: los datos no cumplen una restricción.",
                req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        // Sorpresa real: el detalle completo (clase, mensaje, stack) va SOLO al
        // log del server. Al cliente le devolvemos un mensaje generico: antes se
        // filtraba ex.getMessage() (incluido el SQL/nombre de tabla) al usuario.
        log.error("Unhandled exception in controller: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error interno. Intenta de nuevo; si persiste, contacta soporte.",
                req.getRequestURI());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        ));
    }

    private String fieldErrorMessage(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
