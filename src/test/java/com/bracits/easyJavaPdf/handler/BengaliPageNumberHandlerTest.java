package com.bracits.easyJavaPdf.handler;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BengaliPageNumberHandlerTest {

    @TempDir
    Path tempDir;

    @Mock
    PdfDocumentEvent mockEvent;

    @Mock
    PdfDocument mockPdfDocument;

    @Mock
    PdfPage mockPage;

    private BengaliPageNumberHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new BengaliPageNumberHandler("Test Header");
    }

    @Test
    void testConvertToBengali() {
        // Given
        int number = 123;
        
        // When
        String bengaliNumber = handler.convertToBengali(number);
        
        // Then
        assertEquals("১২৩", bengaliNumber, "Number should be converted to Bengali numerals");
    }

    @Test
    void testConvertToBengaliWithZero() {
        // Given
        int number = 0;
        
        // When
        String bengaliNumber = handler.convertToBengali(number);
        
        // Then
        assertEquals("০", bengaliNumber, "Zero should be converted to Bengali numeral");
    }

    @Test
    void testBengaliPageNumberHandlerRendering() throws IOException {
        // Given
        BengaliPageNumberHandler handler = new BengaliPageNumberHandler("Test Header");
        
        // When & Then
        assertDoesNotThrow(() -> {
            createTestDocument(handler);
        });
    }

    @Test
    void testBengaliPageNumberHandlerWithInvalidFont() {
        // Given
        String nonExistentFontPath = "non-existent-font.ttf";
        
        // When & Then
        assertThrows(Exception.class, () -> {
            new BengaliPageNumberHandler("Test Header", nonExistentFontPath);
        });
    }

    private void createTestDocument(BengaliPageNumberHandler handler) throws IOException {
        File outputFile = tempDir.resolve("bengali_test_document.pdf").toFile();
        PdfWriter writer = new PdfWriter(outputFile);
        PdfDocument pdfDocument = new PdfDocument(writer);
        Document document = new Document(pdfDocument);

        pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, handler);

        // Add some content
        document.add(new Paragraph("Test content page 1"));
        document.add(new Paragraph("This is a test document"));
        
        // Add a second page
        document.add(new Paragraph("Test content page 2").setFixedPosition(100, 100, 400));

        document.close();
        assertTrue(outputFile.exists(), "PDF file should be created");
        assertTrue(outputFile.length() > 0, "PDF file should not be empty");
    }
}