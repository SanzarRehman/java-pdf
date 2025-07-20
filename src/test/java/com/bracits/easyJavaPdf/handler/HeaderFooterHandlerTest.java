package com.bracits.easyJavaPdf.handler;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class HeaderFooterHandlerTest {

    @TempDir
    Path tempDir;

    @Mock
    PdfDocumentEvent mockEvent;

    @Mock
    PdfDocument mockPdfDocument;

    @Mock
    PdfPage mockPage;

    @Mock
    PdfCanvas mockCanvas;

    private ByteArrayOutputStream outputStream;
    private PdfDocument pdfDocument;
    private Document document;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        outputStream = new ByteArrayOutputStream();
    }

    @Test
    void testHeaderRendering() {

        String headerHtml = "<div style='text-align: center;'>Test Header</div>";
        Header header = new Header(headerHtml);


        assertDoesNotThrow(() -> {
            createTestDocument(header, null);
        });
    }

    @Test
    void testFooterRendering() {

        String footerHtml = "<div style='text-align: center;'>Page {{pageNumber}}</div>";
        Footer footer = new Footer(footerHtml);


        assertDoesNotThrow(() -> {
            createTestDocument(null, footer);
        });
    }

    @Test
    void testHeaderAndFooterTogether() {

        String headerHtml = "<div style='text-align: center;'>Test Header</div>";
        String footerHtml = "<div style='text-align: center;'>Page {{pageNumber}}</div>";
        Header header = new Header(headerHtml);
        Footer footer = new Footer(footerHtml);


        assertDoesNotThrow(() -> {
            createTestDocument(header, footer);
        });
    }

    @Test
    void testFooterPageNumberReplacement() throws IOException {

        String footerHtml = "<div>Page {{pageNumber}}</div>";
        Footer footer = new Footer(footerHtml);
        

        when(mockEvent.getDocument()).thenReturn(mockPdfDocument);
        when(mockEvent.getPage()).thenReturn(mockPage);
        when(mockPage.getPageSize()).thenReturn(PageSize.A4);
        when(mockPdfDocument.getPageNumber(mockPage)).thenReturn(5);
        

        String processedHtml = footer.processHtmlContent(mockPdfDocument, mockPage);
        

        assertTrue(processedHtml.contains("Page 5"), "Footer should replace page number placeholder");
    }

    private void createTestDocument(Header header, Footer footer) throws IOException {
        File outputFile = tempDir.resolve("test_document.pdf").toFile();
        PdfWriter writer = new PdfWriter(outputFile);
        pdfDocument = new PdfDocument(writer);
        document = new Document(pdfDocument);

        if (header != null) {
            pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, header);
        }

        if (footer != null) {
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, footer);
        }


        document.add(new Paragraph("Test content page 1"));
        document.add(new Paragraph("This is a test document"));
        

        document.add(new Paragraph("Test content page 2").setFixedPosition(100, 100, 400));

        document.close();
        assertTrue(outputFile.exists(), "PDF file should be created");
        assertTrue(outputFile.length() > 0, "PDF file should not be empty");
    }
}