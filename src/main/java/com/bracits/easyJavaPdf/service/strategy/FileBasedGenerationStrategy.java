package com.bracits.easyJavaPdf.service.strategy;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.bracits.easyJavaPdf.model.PaperSize;
import com.bracits.easyJavaPdf.service.PdfGenerator;
import com.bracits.easyJavaPdf.service.renderer.RendererTuning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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
 * Strategy for generating PDFs from uploaded HTML/CSS files.
 * Handles scenarios where users upload HTML files with optional CSS and assets.
 */
@Component
public class FileBasedGenerationStrategy implements PdfGenerationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(FileBasedGenerationStrategy.class);

    private final PdfGenerator pdfGenerator;
    private final PdfGenerationHelper helper;

    public FileBasedGenerationStrategy(PdfGenerator pdfGenerator, PdfGenerationHelper helper) {
        this.pdfGenerator = pdfGenerator;
        this.helper = helper;
    }

    private RendererTuning buildTuning(com.bracits.easyJavaPdf.dto.PdfGenerationRequest request) {
        PaperSize pageSize = PaperSize.parse(request.getPageSize()).orElse(null);
        if (request.getChunkSizeMb() == null && request.getParallelism() == null
                && request.getFitToWidth() == null && request.getScale() == null
                && pageSize == null) {
            return null;
        }
        return RendererTuning.builder()
                .chunkSizeMb(request.getChunkSizeMb())
                .parallelism(request.getParallelism())
                .fitToWidth(request.getFitToWidth())
                .scale(request.getScale())
                .pageSize(pageSize)
                .build();
    }

    @Override
    public boolean supports(PdfGenerationRequest request) {
        // Supports requests with uploaded HTML files (not report-based or pure content-based)
        return !StringUtils.hasText(request.getReport()) &&
               (request.getHtml() != null && !request.getHtml().isEmpty()) &&
               request.getHtmlContent() == null;
    }

    @Override
    public int getPriority() {
        return 20; // Medium priority
    }

    @Override
    public PdfResponse generate(PdfGenerationRequest request, PageOrientation orientation) 
            throws IOException, InterruptedException, ExecutionException {
        
        logger.debug("Using file-based PDF generation with dedicated temp folder");
        boolean forceBrowserMode = request.isForceBrowserMode();

        // Create a dedicated temporary folder for this request
        Path requestTempDir = helper.getTempFileManager().createTempDirectory("pdf-request");
        logger.debug("Created request temp directory: {}", requestTempDir);

        try {
            List<Path> fontFiles = new ArrayList<>();
            Path htmlFile;
            Path cssFile = null;

            // Handle HTML file - save uploaded file to temp directory
            htmlFile = helper.saveMultipartFileToDirectory(request.getHtml(), requestTempDir, "index.html");

            // Handle CSS file
            if (request.getStyle() != null) {
                String originalName = request.getStyle().getOriginalFilename();
                String cssFileName = (originalName != null && !originalName.isEmpty()) 
                        ? originalName : "styles.css";
                cssFile = helper.saveMultipartFileToDirectory(request.getStyle(), requestTempDir, cssFileName);
            }

            // Create placeholder CSS if not provided
            if (cssFile == null) {
                cssFile = helper.createPlaceholderCss(requestTempDir);
            }

            // Resolve model and render Thymeleaf only when explicitly enabled.
            if (request.isThymeleafTemplate()) {
                Map<String, Object> templateModel = helper.getTemplateRenderingService().resolveModel(request);
                helper.renderThymeleafTemplateToFile(htmlFile, request, templateModel);
            }
            
                helper.logRenderedHtmlFile("file", helper.resolveHtmlIdentifier(htmlFile, request), htmlFile);

            // Handle asset files (fonts, images, etc.)
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

            // Handle header/footer content
            String headerHtml = extractFileContent(request.getHeaderFile());
            String footerHtml = extractFileContent(request.getFooterFile());
            String banglaFooterHtml = extractFileContent(request.getBanglaFooter());

            // Generate PDF using file-based method
            byte[] pdfBytes = generatePdfAsync(
                    htmlFile,
                    cssFile,
                    headerHtml,
                    footerHtml,
                    banglaFooterHtml,
                    fontFiles,
                    request.getPassword(),
                    (request.isJsEnable() || forceBrowserMode) ? "true" : "false",
                    orientation,
                    forceBrowserMode,
                    request.getRenderer(),
                    buildTuning(request)
            );

            return PdfResponse.builder()
                    .content(pdfBytes)
                    .contentLength((long) pdfBytes.length)
                    .fileName("generated.pdf")
                    .disposition("attachment")
                    .build();

        } finally {
            logger.debug("File-based request processing completed, temp directory will be cleaned up: {}", 
                    requestTempDir);
        }
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
                                   boolean forceBrowserMode, String requestRenderer,
                                   RendererTuning tuning) 
            throws InterruptedException, ExecutionException {
        
        logger.info("Starting async PDF generation for file: {} with renderer: {}", 
                htmlFile, requestRenderer != null ? requestRenderer : "default");
        long startTime = System.currentTimeMillis();

        CompletableFuture<byte[]> pdfFuture = CompletableFuture.supplyAsync(() -> {
            try {
                // Check if pdfGenerator is ITextPdfGenerator to use renderer-aware method
                if (pdfGenerator instanceof com.bracits.easyJavaPdf.service.ITextPdfGenerator) {
                    com.bracits.easyJavaPdf.service.ITextPdfGenerator itextGen = 
                        (com.bracits.easyJavaPdf.service.ITextPdfGenerator) pdfGenerator;
                    return itextGen.generatePdf(htmlFile, cssFile, headerHtml, footerHtml,
                        banglaFooterHtml, fontFiles, password, jsEnable, orientation, 
                        forceBrowserMode, requestRenderer, tuning);
                } else {
                    // Fallback to standard interface method
                    return pdfGenerator.generatePdf(htmlFile, cssFile, headerHtml, footerHtml,
                            banglaFooterHtml, fontFiles, password, jsEnable, orientation, forceBrowserMode);
                }
            } catch (Exception e) {
                logger.error("PDF generation failed for file: {}", htmlFile, e);
                throw new RuntimeException("Failed to generate PDF from HTML file: " + htmlFile, e);
            }
        });

        byte[] pdfBytes = pdfFuture.get();
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("PDF generation completed successfully in {} ms, size: {} bytes", 
                duration, pdfBytes.length);
        
        return pdfBytes;
    }
}
