package com.bracits.easyJavaPdf.service.strategy;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.bracits.easyJavaPdf.service.PdfGenerator;
import com.bracits.easyJavaPdf.service.ReportTemplateService;
import com.bracits.easyJavaPdf.service.TemplateRenderingService;
import com.bracits.easyJavaPdf.util.TempFileManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WelcomeLetterReportStrategyTest {

    @Mock
    private PdfGenerator pdfGenerator;

    @Mock
    private TempFileManager tempFileManager;

    @TempDir
    Path tempDir;

    private TemplateRenderingService templateRenderingService;
    private ReportTemplateService reportTemplateService;
    private ReportBasedGenerationStrategy strategy;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        templateRenderingService = new TemplateRenderingService(templateEngine, objectMapper);
        reportTemplateService = new ReportTemplateService(new DefaultResourceLoader());

        PdfGenerationHelper helper = new PdfGenerationHelper(tempFileManager, templateRenderingService);
        strategy = new ReportBasedGenerationStrategy(pdfGenerator, helper, reportTemplateService);

        doNothing().when(tempFileManager).registerForCleanup(any(Path.class));
    }

    @Test
    void generateWelcomeLetterPdfUsingRealTemplate() throws Exception {
        Path workingDir = tempDir.resolve("welcome-letter-workdir");
        Files.createDirectories(workingDir);

        when(tempFileManager.createTempDirectory(startsWith("pdf-report"))).thenReturn(workingDir);

        byte[] pdfBytes = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
        when(pdfGenerator.generatePdf(any(Path.class), any(Path.class), any(), any(), any(), anyList(), any(), anyString(), eq(PageOrientation.PORTRAIT), eq(false)))
                .thenReturn(pdfBytes);

        WelcomeLetterData dataModel = new WelcomeLetterData(
                new Company(
                        "Skyline Ventures",
                        "Empowering Bold Ideas",
                        "410 Innovation Drive, Suite 900, Seattle, WA 98101",
                        "people@skylineventures.com",
                        "+1 (206) 555-2841"
                ),
                new Recipient("Taylor Reed", "Senior Product Strategist"),
                new Partner("Jordan Blake", "jordan.blake@skylineventures.com"),
                new Signature("Alex Morgan", "Director of People & Culture"),
                List.of(
                        new Highlight("Week 1 Orientation", "Complete your onboarding checklist in Nimbus."),
                        new Highlight("Discovery Sprint", "Facilitate stakeholder interviews across teams."),
                        new Highlight("Strategy Summit", "Present your roadmap draft for feedback.")
                ),
                "November 10, 2025",
                "We are thrilled to welcome you to Skyline Ventures!"
        );

        String dataJson = objectMapper.writeValueAsString(dataModel);

        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setReport("welcome-letter");
        request.setData(dataJson);

        PdfResponse response = strategy.generate(request, PageOrientation.PORTRAIT);

        assertNotNull(response);
        assertArrayEquals(pdfBytes, response.getContent());
        assertEquals("welcome-letter.pdf", response.getFileName());
        assertEquals((long) pdfBytes.length, response.getContentLength());

        ArgumentCaptor<Path> htmlCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<Path> cssCaptor = ArgumentCaptor.forClass(Path.class);

        verify(pdfGenerator).generatePdf(htmlCaptor.capture(), cssCaptor.capture(), any(), any(), any(), anyList(), any(), anyString(), eq(PageOrientation.PORTRAIT), eq(false));

        Path renderedHtmlPath = htmlCaptor.getValue();
        assertNotNull(renderedHtmlPath);
        assertTrue(Files.exists(renderedHtmlPath));

        String renderedHtml = Files.readString(renderedHtmlPath, StandardCharsets.UTF_8);
        assertTrue(renderedHtml.contains("Taylor Reed"));
        assertTrue(renderedHtml.contains("Skyline Ventures"));
        assertTrue(renderedHtml.contains("Week 1 Orientation"));
        assertTrue(renderedHtml.contains("Director of People &amp; Culture"));

        Path copiedCssPath = cssCaptor.getValue();
        assertNotNull(copiedCssPath);
        assertTrue(Files.exists(copiedCssPath));
        String cssContent = Files.readString(copiedCssPath, StandardCharsets.UTF_8);
        assertTrue(cssContent.contains("letter-header"));

        try (var paths = Files.walk(workingDir)) {
            long assetCount = paths
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".html") || name.endsWith(".css");
                    })
                    .count();
            assertTrue(assetCount >= 2);
        }
    }

    private record WelcomeLetterData(
            Company company,
            Recipient recipient,
            Partner partner,
            Signature signature,
            List<Highlight> highlights,
            String issuedOn,
            String message
    ) {
    }

    private record Company(String name, String tagline, String address, String email, String phone) {
    }

    private record Recipient(String name, String position) {
    }

    private record Partner(String name, String email) {
    }

    private record Signature(String name, String title) {
    }

    private record Highlight(String title, String description) {
    }
}
