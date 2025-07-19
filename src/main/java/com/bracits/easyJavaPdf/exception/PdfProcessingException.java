package com.bracits.easyJavaPdf.exception;

/**
 * Base exception class for all PDF processing related errors.
 * This serves as the parent class for more specific PDF processing exceptions.
 */
public class PdfProcessingException extends RuntimeException {

    /**
     * Constructs a new PDF processing exception with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public PdfProcessingException(String message) {
        super(message);
    }

    /**
     * Constructs a new PDF processing exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the cause of the exception
     */
    public PdfProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}