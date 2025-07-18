package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.FileProcessingException;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ITextPdfGeneratorTest {

    private ITextPdfGenerator pdfGenerator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pdfGenerator = new ITextPdfGenerator();
    }

    @Test
    void testGeneratePdfWithValidHtmlFile() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body><h1>Test Content</h1></body></html>");
        Path cssFile = createTempFile("test.css", "h1 { color: blue; }");

        // When
        byte[] result = pdfGenerator.generatePdf(
            htmlFile, cssFile, null, null, null, null, null, "false"
        );

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithNullCssFile() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body><p>Simple content</p></body></html>");

        // When
        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, null, null, null, null, null, "false"
        );

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithPassword() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body><p>Protected content</p></body></html>");
        String password = "testPassword123";

        // When
        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, null, null, null, null, password, "false"
        );

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithHeaderAndFooter() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body><p>Content with header and footer</p></body></html>");
        String headerHtml = "<div>Header Content</div>";
        String footerHtml = "<div>Footer Content</div>";

        // When
        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, headerHtml, footerHtml, null, null, null, "false"
        );

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithBengaliFooter() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body><p>Content with Bengali footer</p></body></html>");
        String banglaFooterHtml = "<div>বাংলা ফুটার</div>";

        // When
        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, null, null, banglaFooterHtml, null, null, "false"
        );

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithFonts() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body><p>Content with custom fonts</p></body></html>");
        Path fontFile = createTempFile("test.ttf", "fake font content");
        List<Path> fontFiles = Arrays.asList(fontFile);

        // When
        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, null, null, null, fontFiles, null, "false"
        );

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithNonExistentHtmlFile() {
        // Given
        Path nonExistentFile = tempDir.resolve("nonexistent.html");

        // When & Then
        assertThrows(FileProcessingException.class, () -> 
            pdfGenerator.generatePdf(nonExistentFile, null, null, null, null, null, null, "false"));
    }

    @Test
    void testGeneratePdfWithNonExistentCssFile() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body><p>Test content</p></body></html>");
        Path nonExistentCssFile = tempDir.resolve("nonexistent.css");

        // When & Then
        assertThrows(FileProcessingException.class, () -> 
            pdfGenerator.generatePdf(htmlFile, nonExistentCssFile, null, null, null, null, null, "false"));
    }

    @Test
    void testGeneratePdfFromContentWithValidInput() {
        // Given
        String htmlContent = "<html><body><h1>Test Content</h1></body></html>";
        String cssContent = "h1 { color: red; }";

        // When
        byte[] result = pdfGenerator.generatePdfFromContent(htmlContent, cssContent, null, null);

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithNullCss() {
        // Given
        String htmlContent = "<html><body><p>Simple content</p></body></html>";

        // When
        byte[] result = pdfGenerator.generatePdfFromContent(htmlContent, null, null, null);

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithPassword() {
        // Given
        String htmlContent = "<html><body><p>Protected content</p></body></html>";
        String password = "testPassword123";

        // When
        byte[] result = pdfGenerator.generatePdfFromContent(htmlContent, null, null, password);

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithFonts() throws Exception {
        // Given
        String htmlContent = "<html><body><p>Content with fonts</p></body></html>";
        Path fontFile = createTempFile("test.ttf", "fake font content");
        List<Path> fontFiles = Arrays.asList(fontFile);

        // When
        byte[] result = pdfGenerator.generatePdfFromContent(htmlContent, null, fontFiles, null);

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithInvalidHtml() {
        // Given
        String invalidHtmlContent = "<html><body><unclosed-tag>Invalid HTML";

        // When
        byte[] result = pdfGenerator.generatePdfFromContent(invalidHtmlContent, null, null, null);

        // Then
        // iText should handle invalid HTML gracefully and still produce a PDF
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithEmptyContent() {
        // Given
        String emptyContent = "";

        // When
        byte[] result = pdfGenerator.generatePdfFromContent(emptyContent, null, null, null);

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Verify it's a PDF by checking the header
        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    // Helper methods

    private Path createTempFile(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.write(file, content.getBytes());
        return file;
    }
}