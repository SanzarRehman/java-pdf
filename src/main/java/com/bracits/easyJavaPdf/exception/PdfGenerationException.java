package com.bracits.easyJavaPdf.exception;

/**
 * Exception thrown when PDF generation operations fail.
 * This includes HTML to PDF conversion, font processing, and document creation errors.
 */
public class PdfGenerationException extends PdfProcessingException {

    /**
     * Constructs a new PDF generation exception with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public PdfGenerationException(String message) {
        super(message);
    }

    /**
     * Constructs a new PDF generation exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the cause of the exception
     */
    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}