package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.model.PageOrientation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfServiceTest {

    @Mock
    private Executor mockExecutor;

    @Mock
    private PdfGenerator mockPdfGenerator;

    @Mock
    private com.bracits.easyJavaPdf.util.TempFileManager mockTempFileManager;

    @Mock
    private TemplateRenderingService mockTemplateRenderingService;

    @Mock
    private ReportTemplateService mockReportTemplateService;

    private PdfService pdfService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(mockTemplateRenderingService.resolveModel(any())).thenReturn(Collections.emptyMap());
        lenient().when(mockTemplateRenderingService.resolveModelCollection(any())).thenReturn(List.of(Collections.emptyMap()));
        when(mockTemplateRenderingService.shouldRenderTemplate(any(), any())).thenReturn(false);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockExecutor).execute(any(Runnable.class));
        pdfService = new PdfService(mockExecutor, mockPdfGenerator, mockTempFileManager, mockTemplateRenderingService, mockReportTemplateService);
    }

    @Test
    void testGenerateWithValidParameters() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body>Test Content</body></html>");
        Path cssFile = createTempFile("test.css", "body { font-family: Arial; }");
        byte[] expectedPdfBytes = new byte[]{1, 2, 3, 4};
        
    when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any(), any(PageOrientation.class), anyBoolean()))
            .thenReturn(expectedPdfBytes);


        CompletableFuture<byte[]> result = pdfService.generate(
            htmlFile, cssFile, "header", "footer", null, null, "password", "false",
            PageOrientation.PORTRAIT, false
        );


        assertNotNull(result);
        byte[] actualBytes = result.get();
        assertArrayEquals(expectedPdfBytes, actualBytes);
        

        verify(mockPdfGenerator).generatePdf(
            eq(htmlFile), eq(cssFile), eq("header"), eq("footer"), 
            eq(null), eq(null), eq("password"), eq("false"), eq(PageOrientation.PORTRAIT), eq(false)
        );
    }

    @Test
    void testGenerateWithPdfGeneratorException() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body>Test Content</body></html>");
        
    when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any(), any(PageOrientation.class), anyBoolean()))
            .thenThrow(new PdfGenerationException("PDF generation failed"));


        CompletableFuture<byte[]> result = pdfService.generate(
            htmlFile, null, null, null, null, null, null, "false",
            PageOrientation.PORTRAIT, false
        );
        
        Exception exception = assertThrows(Exception.class, result::join);
        assertTrue(exception.getCause() instanceof PdfGenerationException);
        assertEquals("Failed to generate PDF from HTML file: " + htmlFile, exception.getCause().getMessage());
    }

    @Test
    void testGenerateWithAllParameters() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body>Test</body></html>");
        Path cssFile = createTempFile("test.css", "body { color: red; }");
        Path fontFile = createTempFile("font.ttf", "fake font");
        List<Path> fontFiles = Arrays.asList(fontFile);
        byte[] expectedPdfBytes = new byte[]{5, 6, 7, 8};
        
    when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any(), any(PageOrientation.class), anyBoolean()))
            .thenReturn(expectedPdfBytes);


        CompletableFuture<byte[]> result = pdfService.generate(
            htmlFile, cssFile, "header", "footer", "bangla", fontFiles, "pass", "true",
            PageOrientation.LANDSCAPE, true
        );


        assertNotNull(result);
        byte[] actualBytes = result.get();
        assertArrayEquals(expectedPdfBytes, actualBytes);
        

        verify(mockPdfGenerator).generatePdf(
            eq(htmlFile), eq(cssFile), eq("header"), eq("footer"), 
            eq("bangla"), eq(fontFiles), eq("pass"), eq("true"),
            eq(PageOrientation.LANDSCAPE), eq(true)
        );
    }

    @Test
    void testGenerateLogsPerformanceMetrics() throws Exception {

        Path htmlFile = createTempFile("test.html", "<html><body>Test</body></html>");
        byte[] expectedPdfBytes = new byte[]{1, 2, 3};
        
    when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any(), any(PageOrientation.class), anyBoolean()))
            .thenReturn(expectedPdfBytes);


        CompletableFuture<byte[]> result = pdfService.generate(
            htmlFile, null, null, null, null, null, null, "false",
            PageOrientation.PORTRAIT, false
        );


        assertNotNull(result);
        byte[] actualBytes = result.get();
        assertArrayEquals(expectedPdfBytes, actualBytes);
        

    verify(mockPdfGenerator).generatePdf(any(), any(), any(), any(), any(), any(), any(), any(), any(PageOrientation.class), anyBoolean());
    }



    private Path createTempFile(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.write(file, content.getBytes());
        return file;
    }

    @Test
    void testGeneratePdfWithReportTemplate() throws Exception {
        Path workingDir = tempDir.resolve("reportWork");
        Files.createDirectories(workingDir);

        Path htmlPath = workingDir.resolve("report.html");
        Files.writeString(htmlPath, "<html></html>");

        ReportTemplateDescriptor descriptor = new ReportTemplateDescriptor(
                "report",
                htmlPath,
                List.of(htmlPath),
                List.of()
        );

        when(mockTempFileManager.createTempDirectory(anyString())).thenReturn(workingDir);
        when(mockReportTemplateService.prepareTemplate(eq("report-template"), eq(workingDir))).thenReturn(descriptor);
        when(mockTemplateRenderingService.resolveModelCollection(any())).thenReturn(List.of(Collections.singletonMap("key", "value")));
        when(mockTemplateRenderingService.renderNamedTemplate(eq("report"), anyList()))
                .thenReturn("<html><body>Rendered</body></html>");
        when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any(), any(PageOrientation.class), anyBoolean()))
                .thenReturn(new byte[]{9, 9, 9});

        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setReport("report-template");
        request.setData("{\"key\":\"value\"}");

        PdfResponse response = pdfService.generatePdf(request).get();

        assertNotNull(response);
        assertArrayEquals(new byte[]{9, 9, 9}, response.getContent());
        assertEquals(3L, response.getContentLength());
        assertEquals("report-template.pdf", response.getFileName());
        verify(mockReportTemplateService).prepareTemplate(eq("report-template"), eq(workingDir));
        verify(mockTemplateRenderingService).renderNamedTemplate(eq("report"), anyList());
    }

    @Test
    void testGeneratePdfWithReportTemplateDataSet() throws Exception {
    Path workingDir = tempDir.resolve("transcriptWork");
    Files.createDirectories(workingDir);

    Path htmlPath = workingDir.resolve("transcript/transcript.html");
    Files.createDirectories(htmlPath.getParent());
    Files.writeString(htmlPath, "<html></html>");
    Path cssPath = workingDir.resolve("transcript/style.css");
    Files.writeString(cssPath, "body{}");

    ReportTemplateDescriptor descriptor = new ReportTemplateDescriptor(
        "transcript/transcript",
        htmlPath,
        List.of(htmlPath, cssPath),
        List.of()
    );

    when(mockTempFileManager.createTempDirectory(anyString())).thenReturn(workingDir);
    when(mockReportTemplateService.prepareTemplate(eq("transcript"), eq(workingDir))).thenReturn(descriptor);

    List<Map<String, Object>> models = List.of(
        Collections.singletonMap("portfolioInfo", Collections.singletonMap("name", "Alice")),
        Collections.singletonMap("portfolioInfo", Collections.singletonMap("name", "Bob"))
    );

    when(mockTemplateRenderingService.resolveModelCollection(any())).thenReturn(models);
    when(mockTemplateRenderingService.renderNamedTemplate(eq("transcript/transcript"), anyList()))
        .thenReturn("<html><body>Transcript</body></html>");
    when(mockPdfGenerator.generatePdf(any(), any(), any(), any(), any(), any(), any(), any(), any(PageOrientation.class), anyBoolean()))
        .thenReturn(new byte[]{1, 0, 1});

    PdfGenerationRequest request = new PdfGenerationRequest();
    request.setReport("transcript");
    request.setDataSet("[{\"portfolioInfo\":{\"name\":\"Alice\"}},{\"portfolioInfo\":{\"name\":\"Bob\"}}]");

    PdfResponse response = pdfService.generatePdf(request).get();

    assertNotNull(response);
    assertEquals("transcript.pdf", response.getFileName());
    assertEquals(3L, response.getContentLength());

    ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
    verify(mockTemplateRenderingService).renderNamedTemplate(eq("transcript/transcript"), captor.capture());
    List<Map<String, Object>> capturedModels = captor.getValue();
    assertEquals(2, capturedModels.size());
    assertEquals("Alice", ((Map<?, ?>) capturedModels.get(0).get("portfolioInfo")).get("name"));
    assertEquals("Bob", ((Map<?, ?>) capturedModels.get(1).get("portfolioInfo")).get("name"));
    }
}