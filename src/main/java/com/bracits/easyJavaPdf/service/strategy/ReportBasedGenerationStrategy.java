package com.bracits.easyJavaPdf.service.strategy;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.bracits.easyJavaPdf.service.PdfGenerator;
import com.bracits.easyJavaPdf.service.ReportTemplateDescriptor;
import com.bracits.easyJavaPdf.service.ReportTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Strategy for generating PDFs from named report templates.
 * Handles scenarios where users specify a template name with data payload.
 */
@Component
public class ReportBasedGenerationStrategy implements PdfGenerationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ReportBasedGenerationStrategy.class);

    private final PdfGenerator pdfGenerator;
    private final PdfGenerationHelper helper;
    private final ReportTemplateService reportTemplateService;

    public ReportBasedGenerationStrategy(PdfGenerator pdfGenerator, 
                                        PdfGenerationHelper helper,
                                        ReportTemplateService reportTemplateService) {
        this.pdfGenerator = pdfGenerator;
        this.helper = helper;
        this.reportTemplateService = reportTemplateService;
    }

    @Override
    public boolean supports(PdfGenerationRequest request) {
        // Supports requests with a report template name
        return StringUtils.hasText(request.getReport());
    }

    @Override
    public int getPriority() {
        return 10; // Highest priority
    }

    @Override
    public PdfResponse generate(PdfGenerationRequest request, PageOrientation orientation) 
            throws IOException, InterruptedException, ExecutionException {
        
        logger.debug("Using report-based PDF generation with template: {}", request.getReport());
        boolean forceBrowserMode = request.isForceBrowserMode();

        Path requestTempDir = helper.getTempFileManager().createTempDirectory("pdf-report");
        logger.debug("Created report temp directory: {}", requestTempDir);

        try {
            // Prepare template and copy assets
            ReportTemplateDescriptor descriptor = reportTemplateService.prepareTemplate(
                    request.getReport(), requestTempDir);
            registerCopiedResources(descriptor);

            // Collect font files
            List<Path> fontFiles = new ArrayList<>(descriptor.fontFiles());

            // Add additional uploaded assets
            if (request.getAsset() != null) {
                for (MultipartFile assetFile : request.getAsset()) {
                    String originalName = assetFile.getOriginalFilename();
                    String assetFileName = (originalName != null && !originalName.isEmpty()) 
                            ? originalName : "asset_" + System.currentTimeMillis();
                    Path assetPath = helper.saveMultipartFileToDirectory(assetFile, requestTempDir, assetFileName);
                    if (assetPath != null && helper.isFontFile(assetFileName)) {
                        fontFiles.add(assetPath);
                    }
                }
            }

            // Resolve model and render template with Thymeleaf
            List<Map<String, Object>> models = helper.getTemplateRenderingService()
                    .resolveModelCollection(request);
            String templateContent = Files.readString(descriptor.htmlFile(), StandardCharsets.UTF_8);
            String renderedHtml = helper.getTemplateRenderingService().renderTemplate(templateContent, models);
            helper.logRenderedHtml("report", descriptor.templateName(), renderedHtml);
            Files.writeString(descriptor.htmlFile(), renderedHtml, StandardCharsets.UTF_8);

            // Resolve CSS file
            Path cssFile = resolveCssFile(descriptor, requestTempDir);

            // Extract header/footer content
            String headerHtml = extractFileContent(request.getHeaderFile());
            String footerHtml = extractFileContent(request.getFooterFile());
            String banglaFooterHtml = extractFileContent(request.getBanglaFooter());

            BufferedImage logoImage = null;
            if (request.getLogoImage() != null && !request.getLogoImage().isEmpty()) {
                logoImage = ImageIO.read(request.getLogoImage().getInputStream());
            }

            // Generate PDF
            byte[] pdfBytes = generatePdfAsync(
                    descriptor.htmlFile(),
                    cssFile,
                    headerHtml,
                    footerHtml,
                    banglaFooterHtml,
                    fontFiles,
                    request.getPassword(),
                    (request.isJsEnable() || forceBrowserMode) ? "true" : "false",
                    orientation,
                    forceBrowserMode,
                    logoImage
            );

            return PdfResponse.builder()
                    .content(pdfBytes)
                    .contentLength((long) pdfBytes.length)
                    .fileName(helper.buildReportFileName(request.getReport()))
                    .disposition("attachment")
                    .build();

        } finally {
            logger.debug("Report request processing completed, temp directory will be cleaned up: {}", 
                    requestTempDir);
        }
    }

    private void registerCopiedResources(ReportTemplateDescriptor descriptor) {
        if (descriptor == null || descriptor.copiedResources() == null) {
            return;
        }

        for (Path path : descriptor.copiedResources()) {
            if (path != null) {
                helper.getTempFileManager().registerForCleanup(path);
            }
        }
    }

    private Path resolveCssFile(ReportTemplateDescriptor descriptor, Path workingDir) throws IOException {
        if (descriptor != null && descriptor.copiedResources() != null) {
            for (Path path : descriptor.copiedResources()) {
                if (helper.isCssFile(path)) {
                    return path;
                }
            }
        }

        return helper.createPlaceholderCss(workingDir);
    }

    private String extractFileContent(MultipartFile file) throws IOException {
        if (file != null && !file.isEmpty()) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
        return null;
    }

    private byte[] generatePdfAsync(Path htmlFile, Path cssFile, String headerHtml,
                                   String footerHtml, String banglaFooterHtml, 
                                   List<Path> fontFiles, String password, 
                                   String jsEnable, PageOrientation orientation, 
                                   boolean forceBrowserMode, BufferedImage logoImage)
            throws InterruptedException, ExecutionException {
        
        logger.info("Starting async PDF generation for report: {}", htmlFile);
        long startTime = System.currentTimeMillis();

        CompletableFuture<byte[]> pdfFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return pdfGenerator.generatePdf(htmlFile, cssFile, headerHtml, footerHtml,
                        banglaFooterHtml, fontFiles, password, jsEnable, orientation, forceBrowserMode, logoImage);
            } catch (Exception e) {
                logger.error("PDF generation failed for report: {}", htmlFile, e);
                throw new RuntimeException("Failed to generate PDF from report: " + htmlFile, e);
            }
        });

        byte[] pdfBytes = pdfFuture.get();
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Report PDF generation completed successfully in {} ms, size: {} bytes", 
                duration, pdfBytes.length);
        
        return pdfBytes;
    }
}
