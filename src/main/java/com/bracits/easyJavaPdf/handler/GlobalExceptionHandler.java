package com.bracits.easyJavaPdf.handler;

import com.bracits.easyJavaPdf.dto.ErrorResponse;
import com.bracits.easyJavaPdf.exception.FileProcessingException;
import com.bracits.easyJavaPdf.exception.PdfProcessingException;
import com.bracits.easyJavaPdf.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Global exception handler that provides centralized error handling across the application.
 * This class handles all exceptions and converts them to standardized error responses.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation exceptions with HTTP 400 Bad Request status.
     *
     * @param ex the validation exception
     * @param request the web request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex, WebRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getMessage(),
                "VALIDATION_ERROR",
                getPath(request),
                HttpStatus.BAD_REQUEST.value()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles file processing exceptions with HTTP 422 Unprocessable Entity status.
     *
     * @param ex the file processing exception
     * @param request the web request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ErrorResponse> handleFileProcessingException(FileProcessingException ex, WebRequest request) {
        log.error("File processing error: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getMessage(),
                "FILE_PROCESSING_ERROR",
                getPath(request),
                HttpStatus.UNPROCESSABLE_ENTITY.value()
        );
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    /**
     * Handles general PDF processing exceptions with HTTP 500 Internal Server Error status.
     *
     * @param ex the PDF processing exception
     * @param request the web request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePdfProcessingException(PdfProcessingException ex, WebRequest request) {
        log.error("PDF processing error: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getMessage(),
                "PDF_PROCESSING_ERROR",
                getPath(request),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handles file upload size exceeded exceptions with HTTP 413 Payload Too Large status.
     *
     * @param ex the max upload size exceeded exception
     * @param request the web request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, WebRequest request) {
        log.warn("File upload size exceeded: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.of(
                "File size exceeds the maximum allowed limit",
                "FILE_SIZE_EXCEEDED",
                getPath(request),
                HttpStatus.PAYLOAD_TOO_LARGE.value()
        );
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
    }

    /**
     * Handles all other unexpected exceptions with HTTP 500 Internal Server Error status.
     *
     * @param ex the unexpected exception
     * @param request the web request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.of(
                "An unexpected error occurred. Please try again later.",
                "INTERNAL_SERVER_ERROR",
                getPath(request),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Extracts the request path from the WebRequest.
     *
     * @param request the web request
     * @return the request path
     */
    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}