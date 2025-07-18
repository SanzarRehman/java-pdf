package com.bracits.easyJavaPdf.exception;

/**
 * Exception thrown when file processing operations fail.
 * This includes file reading, writing, validation, and manipulation errors.
 */
public class FileProcessingException extends PdfProcessingException {

    /**
     * Constructs a new file processing exception with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public FileProcessingException(String message) {
        super(message);
    }

    /**
     * Constructs a new file processing exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the cause of the exception
     */
    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}