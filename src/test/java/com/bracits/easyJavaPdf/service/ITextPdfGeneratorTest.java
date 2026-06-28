package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.FileProcessingException;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.model.PageOrientation;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ITextPdfGeneratorTest {

    @Mock
    private CssProcessor mockCssProcessor;

    @Mock
    private com.bracits.easyJavaPdf.service.renderer.ChromiumPdfRenderer mockChromiumPdfRenderer;

    private ITextPdfGenerator pdfGenerator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Mock the CSS processor to return safe CSS
        when(mockCssProcessor.processCss(anyString(), any(PageOrientation.class))).thenReturn(
            "@page { size: A4; margin: 2cm; } body { margin: 0; padding: 1em; }"
        );
        
        pdfGenerator = new ITextPdfGenerator(mockCssProcessor, mockChromiumPdfRenderer);
    }

    @Test
    void testGeneratePdfWithValidHtmlFile() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body><h1>Test Content</h1></body></html>");
        Path cssFile = createTempFile("test.css", "h1 { color: blue; }");


        byte[] result = pdfGenerator.generatePdf(
            htmlFile, cssFile, null, null, null, null, null, "false", PageOrientation.PORTRAIT, false
        );


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithNullCssFile() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body><p>Simple content</p></body></html>");


        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, null, null, null, null, null, "false", PageOrientation.PORTRAIT, false
        );


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithPassword() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body><p>Protected content</p></body></html>");
        String password = "testPassword123";


        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, null, null, null, null, password, "false", PageOrientation.PORTRAIT, false
        );


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithHeaderAndFooter() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body><p>Content with header and footer</p></body></html>");
        String headerHtml = "<div>Header Content</div>";
        String footerHtml = "<div>Footer Content</div>";


        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, headerHtml, footerHtml, null, null, null, "false", PageOrientation.PORTRAIT, false
        );


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithBengaliFooter() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body><p>Content with Bengali footer</p></body></html>");
        String banglaFooterHtml = "<div>বাংলা ফুটার</div>";


        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, null, null, banglaFooterHtml, null, null, "false", PageOrientation.PORTRAIT, false
        );


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithFonts() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body><p>Content with custom fonts</p></body></html>");
        Path fontFile = createTempFile("test.ttf", "fake font content");
        List<Path> fontFiles = Arrays.asList(fontFile);


        byte[] result = pdfGenerator.generatePdf(
            htmlFile, null, null, null, null, fontFiles, null, "false", PageOrientation.PORTRAIT, false
        );


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfWithNonExistentHtmlFile() {

        Path nonExistentFile = tempDir.resolve("nonexistent.html");


        assertThrows(FileProcessingException.class, () -> 
            pdfGenerator.generatePdf(nonExistentFile, null, null, null, null, null, null, "false", PageOrientation.PORTRAIT, false));
    }

    @Test
    void testGeneratePdfWithNonExistentCssFile() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body><p>Test content</p></body></html>");
        Path nonExistentCssFile = tempDir.resolve("nonexistent.css");


        assertThrows(FileProcessingException.class, () -> 
            pdfGenerator.generatePdf(htmlFile, nonExistentCssFile, null, null, null, null, null, "false", PageOrientation.PORTRAIT, false));
    }

    @Test
    void testGeneratePdfFromContentWithValidInput() {

        String htmlContent = "<html><body><h1>Test Content</h1></body></html>";
        String cssContent = "h1 { color: red; }";


    byte[] result = pdfGenerator.generatePdfFromContent(htmlContent, cssContent, null, null, PageOrientation.PORTRAIT, false);


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithNullCss() {

        String htmlContent = "<html><body><p>Simple content</p></body></html>";


    byte[] result = pdfGenerator.generatePdfFromContent(htmlContent, null, null, null, PageOrientation.PORTRAIT, false);


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithPassword() {

        String htmlContent = "<html><body><p>Protected content</p></body></html>";
        String password = "testPassword123";


    byte[] result = pdfGenerator.generatePdfFromContent(htmlContent, null, null, password, PageOrientation.PORTRAIT, false);


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithFonts() throws Exception {

        String htmlContent = "<html><body><p>Content with fonts</p></body></html>";
        Path fontFile = createTempFile("test.ttf", "fake font content");
        List<Path> fontFiles = Arrays.asList(fontFile);


    byte[] result = pdfGenerator.generatePdfFromContent(htmlContent, null, fontFiles, null, PageOrientation.PORTRAIT, false);


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithInvalidHtml() {

        String invalidHtmlContent = "<html><body><unclosed-tag>Invalid HTML";


    byte[] result = pdfGenerator.generatePdfFromContent(invalidHtmlContent, null, null, null, PageOrientation.PORTRAIT, false);



        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }

    @Test
    void testGeneratePdfFromContentWithEmptyContent() {

        String emptyContent = "";


    byte[] result = pdfGenerator.generatePdfFromContent(emptyContent, null, null, null, PageOrientation.PORTRAIT, false);


        assertNotNull(result);
        assertTrue(result.length > 0);

        assertTrue(result[0] == '%' && result[1] == 'P' && result[2] == 'D' && result[3] == 'F');
    }



    private Path createTempFile(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.write(file, content.getBytes());
        return file;
    }
}