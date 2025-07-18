package com.bracits.easyJavaPdf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Standardized response DTO for PDF operations containing content, headers, and metadata.
 * Uses builder pattern for easy construction and flexible response creation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfResponse {

    /**
     * The PDF content as byte array
     */
    private byte[] content;

    /**
     * The filename for the PDF document
     */
    @Builder.Default
    private String fileName = "document.pdf";

    /**
     * The content type for the response
     */
    @Builder.Default
    private String contentType = "application/pdf";

    /**
     * HTTP headers to be included in the response
     */
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * Additional metadata about the PDF response
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Size of the PDF content in bytes
     */
    private Long contentLength;

    /**
     * Content disposition (inline or attachment)
     */
    @Builder.Default
    private String disposition = "inline";

    /**
     * Convenience method to add a header
     * @param name header name
     * @param value header value
     * @return this PdfResponse for method chaining
     */
    public PdfResponse addHeader(String name, String value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(name, value);
        return this;
    }

    /**
     * Convenience method to add metadata
     * @param key metadata key
     * @param value metadata value
     * @return this PdfResponse for method chaining
     */
    public PdfResponse addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Sets the content and automatically calculates content length
     * @param content the PDF content as byte array
     * @return this PdfResponse for method chaining
     */
    public PdfResponse setContentWithLength(byte[] content) {
        this.content = content;
        this.contentLength = content != null ? (long) content.length : 0L;
        return this;
    }

    /**
     * Creates a Content-Disposition header value based on disposition and filename
     * @return formatted Content-Disposition header value
     */
    public String getContentDispositionHeader() {
        return disposition + "; filename=" + fileName;
    }

    /**
     * Checks if the response has content
     * @return true if content is not null and not empty
     */
    public boolean hasContent() {
        return content != null && content.length > 0;
    }
}