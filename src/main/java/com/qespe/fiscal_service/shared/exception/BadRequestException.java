package com.qespe.fiscal_service.shared.exception;

/**
 * Thrown for invalid client input. Mapped to HTTP 400 by
 * {@code FiscalExceptionHandler} (or whichever {@code @ControllerAdvice}
 * is active).
 *
 * <p>Use friendly Spanish messages — they're surfaced to the user as-is by
 * the frontend toast system.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
