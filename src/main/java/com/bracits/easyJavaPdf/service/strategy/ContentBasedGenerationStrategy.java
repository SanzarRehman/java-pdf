package com.bracits.easyJavaPdf.service.strategy;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.bracits.easyJavaPdf.service.PdfGenerator;
import com.bracits.easyJavaPdf.service.renderer.RendererTuning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Strategy for generating PDFs from inline HTML/CSS content strings.
 * Handles scenarios where HTML and CSS are provided as string content rather than files.
 */
@Component
public class ContentBasedGenerationStrategy implements PdfGenerationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ContentBasedGenerationStrategy.class);

    private final PdfGenerator pdfGenerator;
    private final PdfGenerationHelper helper;

    public ContentBasedGenerationStrategy(PdfGenerator pdfGenerator, PdfGenerationHelper helper) {
        this.pdfGenerator = pdfGenerator;
        this.helper = helper;
    }

    private RendererTuning buildTuning(PdfGenerationRequest request) {
        if (request.getChunkSizeMb() == null && request.getParallelism() == null
                && request.getFitToWidth() == null && request.getScale() == null
                && request.getMode() == null) {
            return null;
        }
        Boolean fast = request.getMode() == null ? null : "fast".equalsIgnoreCase(request.getMode());
        return RendererTuning.builder()
                .chunkSizeMb(request.getChunkSizeMb())
                .parallelism(request.getParallelism())
                .fitToWidth(request.getFitToWidth())
                .scale(request.getScale())
                .fast(fast)
                .build();
    }

    @Override
    public boolean supports(PdfGenerationRequest request) {
        // Supports requests with inline HTML/CSS content (not report-based, no file uploads)
        return !StringUtils.hasText(request.getReport()) &&
               (request.getHtmlContent() != null || request.getCssContent() != null) &&
               (request.getHeaderFile() == null && request.getFooterFile() == null && 
                request.getBanglaFooter() == null);
    }

    @Override
    public int getPriority() {
        return 30; // Lower priority than file-based
    }

    @Override
    public PdfResponse generate(PdfGenerationRequest request, PageOrientation orientation) 
            throws IOException {
        
        logger.debug("Using content-based PDF generation with dedicated temp folder");
        boolean forceBrowserMode = request.isForceBrowserMode();

        // Create a dedicated temporary folder for this request
        Path requestTempDir = helper.getTempFileManager().createTempDirectory("pdf-content-request");
        logger.debug("Created content request temp directory: {}", requestTempDir);

        try {
            // Prepare font files in the temp directory
            List<Path> fontFiles = new ArrayList<>();
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

            // Get HTML content (from htmlContent or html file)
            String htmlContent = request.getHtmlContent();
            if (htmlContent == null && request.getHtml() != null) {
                htmlContent = new String(request.getHtml().getBytes());
            }

            // Get CSS content (from cssContent or style file)
            String cssContent = request.getCssContent();
            if (cssContent == null && request.getStyle() != null) {
                cssContent = new String(request.getStyle().getBytes());
            }

            // Resolve model and render Thymeleaf only when explicitly enabled.
            if (request.isThymeleafTemplate()) {
                Map<String, Object> templateModel = helper.getTemplateRenderingService().resolveModel(request);
                htmlContent = helper.renderThymeleafIfNecessary(htmlContent, request, templateModel);
            }
            helper.logRenderedHtml("content", helper.resolveHtmlIdentifier(null, request), htmlContent);

            // Generate PDF using content-based method
            byte[] pdfBytes;
            // Check if pdfGenerator is ITextPdfGenerator to use renderer-aware method
                if (pdfGenerator instanceof com.bracits.easyJavaPdf.service.ITextPdfGenerator) {
                com.bracits.easyJavaPdf.service.ITextPdfGenerator itextGen = 
                    (com.bracits.easyJavaPdf.service.ITextPdfGenerator) pdfGenerator;
                pdfBytes = itextGen.generatePdfFromContent(htmlContent, cssContent, fontFiles,
                    request.getPassword(), orientation, forceBrowserMode, request.getRenderer(),
                    buildTuning(request));
            } else {
                // Fallback to standard interface method
                pdfBytes = pdfGenerator.generatePdfFromContent(htmlContent, cssContent, fontFiles,
                        request.getPassword(), orientation, forceBrowserMode);
            }

            return PdfResponse.builder()
                    .content(pdfBytes)
                    .contentLength((long) pdfBytes.length)
                    .fileName("generated.pdf")
                    .disposition("attachment")
                    .build();

        } finally {
            logger.debug("Content request processing completed, temp directory will be cleaned up: {}", 
                    requestTempDir);
        }
    }
}
