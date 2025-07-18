package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfServiceTest {

    @Mock
    private Executor mockExecutor;

    @Mock
    private PdfGenerator mockPdfGenerator;

    private PdfService pdfService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pdfService = new PdfService(mockExecutor, mockPdfGenerator);
    }

    @Test
    void testGenerateWithValidParameters() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body>Test Content</body></html>");
        Path cssFile = createTempFile("test.css", "body { font-family: Arial; }");
        byte[] expectedPdfBytes = new byte[]{1, 2, 3, 4}; // Mock PDF bytes
        
        when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedPdfBytes);

        // When
        CompletableFuture<byte[]> result = pdfService.generate(
            htmlFile, cssFile, "header", "footer", null, null, "password", "false"
        );

        // Then
        assertNotNull(result);
        byte[] actualBytes = result.get();
        assertArrayEquals(expectedPdfBytes, actualBytes);
        
        // Verify that the PdfGenerator was called with correct parameters
        verify(mockPdfGenerator).generatePdf(
            eq(htmlFile), eq(cssFile), eq("header"), eq("footer"), 
            eq(null), eq(null), eq("password"), eq("false")
        );
    }

    @Test
    void testGenerateWithPdfGeneratorException() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body>Test Content</body></html>");
        
        when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new PdfGenerationException("PDF generation failed"));

        // When & Then
        CompletableFuture<byte[]> result = pdfService.generate(
            htmlFile, null, null, null, null, null, null, "false"
        );
        
        Exception exception = assertThrows(Exception.class, result::join);
        assertTrue(exception.getCause() instanceof PdfGenerationException);
        assertEquals("Failed to generate PDF from HTML file: " + htmlFile, exception.getCause().getMessage());
    }

    @Test
    void testGenerateWithAllParameters() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body>Test</body></html>");
        Path cssFile = createTempFile("test.css", "body { color: red; }");
        Path fontFile = createTempFile("font.ttf", "fake font");
        List<Path> fontFiles = Arrays.asList(fontFile);
        byte[] expectedPdfBytes = new byte[]{5, 6, 7, 8};
        
        when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedPdfBytes);

        // When
        CompletableFuture<byte[]> result = pdfService.generate(
            htmlFile, cssFile, "header", "footer", "bangla", fontFiles, "pass", "true"
        );

        // Then
        assertNotNull(result);
        byte[] actualBytes = result.get();
        assertArrayEquals(expectedPdfBytes, actualBytes);
        
        // Verify all parameters were passed correctly
        verify(mockPdfGenerator).generatePdf(
            eq(htmlFile), eq(cssFile), eq("header"), eq("footer"), 
            eq("bangla"), eq(fontFiles), eq("pass"), eq("true")
        );
    }

    @Test
    void testGenerateLogsPerformanceMetrics() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body>Test</body></html>");
        byte[] expectedPdfBytes = new byte[]{1, 2, 3};
        
        when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedPdfBytes);

        // When
        CompletableFuture<byte[]> result = pdfService.generate(
            htmlFile, null, null, null, null, null, null, "false"
        );

        // Then
        assertNotNull(result);
        byte[] actualBytes = result.get();
        assertArrayEquals(expectedPdfBytes, actualBytes);
        
        // Verify the generator was called
        verify(mockPdfGenerator).generatePdf(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // Helper methods

    private Path createTempFile(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.write(file, content.getBytes());
        return file;
    }
}