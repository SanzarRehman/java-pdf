package com.bracits.easyJavaPdf.service.strategy;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.bracits.easyJavaPdf.service.PdfGenerator;
import com.bracits.easyJavaPdf.service.ReportTemplateDescriptor;
import com.bracits.easyJavaPdf.service.ReportTemplateService;
import com.bracits.easyJavaPdf.service.TemplateRenderingService;
import com.bracits.easyJavaPdf.util.TempFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportBasedGenerationStrategyTest {

    @Mock
    private PdfGenerator pdfGenerator;

    @Mock
    private ReportTemplateService reportTemplateService;

    @Mock
    private TempFileManager tempFileManager;

    @Mock
    private TemplateRenderingService templateRenderingService;

    @TempDir
    Path tempDir;

    private PdfGenerationHelper helper;
    private ReportBasedGenerationStrategy strategy;

    @BeforeEach
    void setUp() {
        helper = new PdfGenerationHelper(tempFileManager, templateRenderingService);
        strategy = new ReportBasedGenerationStrategy(pdfGenerator, helper, reportTemplateService);
        doNothing().when(tempFileManager).registerForCleanup(any(Path.class));
    }

    @Test
    void generateCreatesPdfUsingTemplateAndAssets() throws Exception {
        Path workingDir = tempDir.resolve("reportWork");
        Files.createDirectories(workingDir);

        Path htmlFile = workingDir.resolve("transcript/transcript.html");
        Files.createDirectories(htmlFile.getParent());
        String templateContent = "<html><body>[[${portfolioInfo.name}]]</body></html>";
        Files.writeString(htmlFile, templateContent, StandardCharsets.UTF_8);

        Path cssFile = workingDir.resolve("transcript/style.css");
        Files.createDirectories(cssFile.getParent());
        Files.writeString(cssFile, "body { font-family: Arial; }", StandardCharsets.UTF_8);

        ReportTemplateDescriptor descriptor = new ReportTemplateDescriptor(
                "transcript/transcript",
                htmlFile,
                List.of(htmlFile, cssFile),
                List.of()
        );

        when(tempFileManager.createTempDirectory(startsWith("pdf-report"))).thenReturn(workingDir);
        when(reportTemplateService.prepareTemplate(eq("transcript"), eq(workingDir))).thenReturn(descriptor);

        List<Map<String, Object>> models = List.of(Map.of("portfolioInfo", Map.of("name", "Alice")));
        when(templateRenderingService.resolveModelCollection(any())).thenReturn(models);
        when(templateRenderingService.renderTemplate(eq(templateContent), eq(models)))
                .thenReturn("<html><body>Alice</body></html>");

        byte[] pdfBytes = new byte[]{1, 2, 3};
        when(pdfGenerator.generatePdf(eq(htmlFile), eq(cssFile), isNull(), isNull(), isNull(), anyList(), isNull(), anyString(), eq(PageOrientation.PORTRAIT), eq(false)))
                .thenReturn(pdfBytes);

        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setReport("transcript");
        request.setData("{\"portfolioInfo\":{\"name\":\"Alice\"}}");

        PdfResponse response = strategy.generate(request, PageOrientation.PORTRAIT);

        assertArrayEquals(pdfBytes, response.getContent());
        assertEquals(3L, response.getContentLength());
        assertEquals("transcript.pdf", response.getFileName());
        assertEquals("attachment", response.getDisposition());

        String renderedContent = Files.readString(htmlFile, StandardCharsets.UTF_8);
        assertEquals("<html><body>Alice</body></html>", renderedContent);

        verify(templateRenderingService).renderTemplate(eq(templateContent), eq(models));
        verify(pdfGenerator).generatePdf(eq(htmlFile), eq(cssFile), isNull(), isNull(), isNull(), anyList(), isNull(), anyString(), eq(PageOrientation.PORTRAIT), eq(false));
    }

    @Test
    void supportsReturnsTrueWhenReportNamePresent() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setReport("certificate");

        assertTrue(strategy.supports(request));
        assertEquals(10, strategy.getPriority());
    }

    @Test
    void generateIncludesUploadedFontAssets() throws Exception {
        Path workingDir = tempDir.resolve("reportFonts");
        Files.createDirectories(workingDir);

        Path htmlFile = workingDir.resolve("letter/letter.html");
        Files.createDirectories(htmlFile.getParent());
        Files.writeString(htmlFile, "<html>Letter</html>", StandardCharsets.UTF_8);

        ReportTemplateDescriptor descriptor = new ReportTemplateDescriptor(
                "letter/letter",
                htmlFile,
                List.of(htmlFile),
                List.of()
        );

        when(tempFileManager.createTempDirectory(startsWith("pdf-report"))).thenReturn(workingDir);
        when(reportTemplateService.prepareTemplate(eq("letter"), eq(workingDir))).thenReturn(descriptor);
        when(templateRenderingService.resolveModelCollection(any())).thenReturn(List.of(Map.of()));
        when(templateRenderingService.renderTemplate(anyString(), anyList())).thenReturn("<html>Letter</html>");

        byte[] pdfBytes = new byte[]{4, 5};
        when(pdfGenerator.generatePdf(any(), any(), any(), any(), any(), anyList(), any(), anyString(), any(PageOrientation.class), anyBoolean()))
                .thenReturn(pdfBytes);

        MultipartFile fontAsset = mock(MultipartFile.class);
        when(fontAsset.isEmpty()).thenReturn(false);
        when(fontAsset.getOriginalFilename()).thenReturn("custom.ttf");
        when(fontAsset.getBytes()).thenReturn("font".getBytes(StandardCharsets.UTF_8));

        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setReport("letter");
        request.setAsset(List.of(fontAsset));

        PdfResponse response = strategy.generate(request, PageOrientation.PORTRAIT);

        assertArrayEquals(pdfBytes, response.getContent());
        verify(pdfGenerator).generatePdf(any(Path.class), any(Path.class), any(), any(), any(), argThat(list -> list.stream().anyMatch(path -> path.getFileName().toString().equals("custom.ttf"))), any(), anyString(), any(PageOrientation.class), anyBoolean());
    }
}
