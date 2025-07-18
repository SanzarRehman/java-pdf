package com.bracits.easyJavaPdf.exception;

/**
 * Exception thrown when validation of input data fails.
 * This includes request validation, business rule validation, and data integrity checks.
 */
public class ValidationException extends PdfProcessingException {

    /**
     * Constructs a new validation exception with the specified detail message.
     *
     * @param message the detail message explaining the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new validation exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the validation failure
     * @param cause the cause of the exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}