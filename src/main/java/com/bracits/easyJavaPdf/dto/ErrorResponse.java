package com.bracits.easyJavaPdf.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standardized error response DTO for consistent error handling across the API.
 * This class provides a uniform structure for all error responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /**
     * Human-readable error message describing what went wrong.
     */
    private String message;

    /**
     * Error code for programmatic error identification.
     */
    private String code;

    /**
     * Timestamp when the error occurred.
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * The request path where the error occurred.
     */
    private String path;

    /**
     * HTTP status code associated with the error.
     */
    private int status;

    /**
     * Creates an ErrorResponse with current timestamp.
     *
     * @param message the error message
     * @param code the error code
     * @param path the request path
     * @param status the HTTP status code
     * @return ErrorResponse instance
     */
    public static ErrorResponse of(String message, String code, String path, int status) {
        return ErrorResponse.builder()
                .message(message)
                .code(code)
                .path(path)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
    }
}