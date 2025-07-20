package com.bracits.easyJavaPdf.dto;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfResponseTest {

    @Test
    void testPdfResponseBuilder() {
        byte[] testContent = "PDF content".getBytes();
        Map<String, String> headers = new HashMap<>();
        headers.put("Custom-Header", "Custom-Value");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pages", 5);
        metadata.put("size", "A4");

        PdfResponse response = PdfResponse.builder()
                .content(testContent)
                .fileName("test.pdf")
                .contentType("application/pdf")
                .headers(headers)
                .metadata(metadata)
                .contentLength(100L)
                .disposition("attachment")
                .build();

        assertEquals(testContent, response.getContent());
        assertEquals("test.pdf", response.getFileName());
        assertEquals("application/pdf", response.getContentType());
        assertEquals(headers, response.getHeaders());
        assertEquals(metadata, response.getMetadata());
        assertEquals(100L, response.getContentLength());
        assertEquals("attachment", response.getDisposition());
    }

    @Test
    void testPdfResponseBuilderWithDefaults() {
        PdfResponse response = PdfResponse.builder().build();

        assertNull(response.getContent());
        assertEquals("document.pdf", response.getFileName());
        assertEquals("application/pdf", response.getContentType());
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().isEmpty());
        assertNotNull(response.getMetadata());
        assertTrue(response.getMetadata().isEmpty());
        assertNull(response.getContentLength());
        assertEquals("inline", response.getDisposition());
    }

    @Test
    void testNoArgsConstructor() {
        PdfResponse response = new PdfResponse();

        assertNull(response.getContent());
        assertEquals("document.pdf", response.getFileName());
        assertEquals("application/pdf", response.getContentType());
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().isEmpty());
        assertNotNull(response.getMetadata());
        assertTrue(response.getMetadata().isEmpty());
        assertNull(response.getContentLength());
        assertEquals("inline", response.getDisposition());
    }

    @Test
    void testAllArgsConstructor() {
        byte[] testContent = "PDF content".getBytes();
        Map<String, String> headers = new HashMap<>();
        headers.put("Test-Header", "Test-Value");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("test", "value");

        PdfResponse response = new PdfResponse(
                testContent,
                "custom.pdf",
                "application/pdf",
                headers,
                metadata,
                200L,
                "attachment"
        );

        assertEquals(testContent, response.getContent());
        assertEquals("custom.pdf", response.getFileName());
        assertEquals("application/pdf", response.getContentType());
        assertEquals(headers, response.getHeaders());
        assertEquals(metadata, response.getMetadata());
        assertEquals(200L, response.getContentLength());
        assertEquals("attachment", response.getDisposition());
    }

    @Test
    void testAddHeader() {
        PdfResponse response = new PdfResponse();
        
        PdfResponse result = response.addHeader("Authorization", "Bearer token");
        

        assertSame(response, result);
        assertEquals("Bearer token", response.getHeaders().get("Authorization"));
    }

    @Test
    void testAddHeaderWithNullHeaders() {
        PdfResponse response = new PdfResponse();
        response.setHeaders(null);
        
        response.addHeader("Test", "Value");
        
        assertNotNull(response.getHeaders());
        assertEquals("Value", response.getHeaders().get("Test"));
    }

    @Test
    void testAddMetadata() {
        PdfResponse response = new PdfResponse();
        
        PdfResponse result = response.addMetadata("pages", 10);
        

        assertSame(response, result);
        assertEquals(10, response.getMetadata().get("pages"));
    }

    @Test
    void testAddMetadataWithNullMetadata() {
        PdfResponse response = new PdfResponse();
        response.setMetadata(null);
        
        response.addMetadata("test", "value");
        
        assertNotNull(response.getMetadata());
        assertEquals("value", response.getMetadata().get("test"));
    }

    @Test
    void testSetContentWithLength() {
        PdfResponse response = new PdfResponse();
        byte[] testContent = "Test PDF content".getBytes();
        
        PdfResponse result = response.setContentWithLength(testContent);
        

        assertSame(response, result);
        assertEquals(testContent, response.getContent());
        assertEquals((long) testContent.length, response.getContentLength());
    }

    @Test
    void testSetContentWithLengthNullContent() {
        PdfResponse response = new PdfResponse();
        
        response.setContentWithLength(null);
        
        assertNull(response.getContent());
        assertEquals(0L, response.getContentLength());
    }

    @Test
    void testGetContentDispositionHeader() {
        PdfResponse response = new PdfResponse();
        response.setDisposition("attachment");
        response.setFileName("report.pdf");
        
        String header = response.getContentDispositionHeader();
        
        assertEquals("attachment; filename=report.pdf", header);
    }

    @Test
    void testGetContentDispositionHeaderWithInline() {
        PdfResponse response = new PdfResponse();
        response.setDisposition("inline");
        response.setFileName("document.pdf");
        
        String header = response.getContentDispositionHeader();
        
        assertEquals("inline; filename=document.pdf", header);
    }

    @Test
    void testHasContentWithContent() {
        PdfResponse response = new PdfResponse();
        response.setContent("PDF content".getBytes());
        
        assertTrue(response.hasContent());
    }

    @Test
    void testHasContentWithNullContent() {
        PdfResponse response = new PdfResponse();
        response.setContent(null);
        
        assertFalse(response.hasContent());
    }

    @Test
    void testHasContentWithEmptyContent() {
        PdfResponse response = new PdfResponse();
        response.setContent(new byte[0]);
        
        assertFalse(response.hasContent());
    }

    @Test
    void testGettersAndSetters() {
        PdfResponse response = new PdfResponse();
        byte[] testContent = "Test content".getBytes();
        Map<String, String> headers = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();
        
        response.setContent(testContent);
        assertEquals(testContent, response.getContent());
        
        response.setFileName("test.pdf");
        assertEquals("test.pdf", response.getFileName());
        
        response.setContentType("application/pdf");
        assertEquals("application/pdf", response.getContentType());
        
        response.setHeaders(headers);
        assertEquals(headers, response.getHeaders());
        
        response.setMetadata(metadata);
        assertEquals(metadata, response.getMetadata());
        
        response.setContentLength(500L);
        assertEquals(500L, response.getContentLength());
        
        response.setDisposition("attachment");
        assertEquals("attachment", response.getDisposition());
    }

    @Test
    void testMethodChaining() {
        byte[] testContent = "Test content".getBytes();
        
        PdfResponse response = new PdfResponse()
                .addHeader("Custom-Header", "Value")
                .addMetadata("pages", 5)
                .setContentWithLength(testContent);
        
        assertEquals("Value", response.getHeaders().get("Custom-Header"));
        assertEquals(5, response.getMetadata().get("pages"));
        assertEquals(testContent, response.getContent());
        assertEquals((long) testContent.length, response.getContentLength());
    }
}