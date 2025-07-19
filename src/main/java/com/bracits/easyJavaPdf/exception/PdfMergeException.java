package com.bracits.easyJavaPdf.exception;

/**
 * Exception thrown when PDF merging operations fail.
 * This includes document merging, page range processing, and file combination errors.
 */
public class PdfMergeException extends PdfProcessingException {

    /**
     * Constructs a new PDF merge exception with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public PdfMergeException(String message) {
        super(message);
    }

    /**
     * Constructs a new PDF merge exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the cause of the exception
     */
    public PdfMergeException(String message, Throwable cause) {
        super(message, cause);
    }
}