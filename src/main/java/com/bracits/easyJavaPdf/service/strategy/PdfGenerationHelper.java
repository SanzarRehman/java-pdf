package com.bracits.easyJavaPdf.service.strategy;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.service.TemplateRenderingService;
import com.bracits.easyJavaPdf.util.TempFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Helper class containing common utility methods shared across PDF generation strategies.
 * Provides file operations, validation, and Thymeleaf rendering support.
 */
public class PdfGenerationHelper {

    private static final Logger logger = LoggerFactory.getLogger(PdfGenerationHelper.class);

    private final TempFileManager tempFileManager;
    private final TemplateRenderingService templateRenderingService;

    public PdfGenerationHelper(TempFileManager tempFileManager, 
                               TemplateRenderingService templateRenderingService) {
        this.tempFileManager = tempFileManager;
        this.templateRenderingService = templateRenderingService;
    }

    /**
     * Saves a MultipartFile to a specific directory with a given filename.
     */
    public Path saveMultipartFileToDirectory(MultipartFile file, Path directory, String filename) 
            throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        Path targetFile = directory.resolve(filename);
        Files.write(targetFile, file.getBytes());
        tempFileManager.registerForCleanup(targetFile);

        logger.debug("Saved file {} to directory {}", filename, directory);
        return targetFile;
    }

    /**
     * Determines if a file is a font file based on its extension.
     */
    public boolean isFontFile(String filename) {
        if (filename == null) {
            return false;
        }

        String lowerName = filename.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".ttf")
                || lowerName.endsWith(".otf")
                || lowerName.endsWith(".woff")
                || lowerName.endsWith(".woff2")
                || lowerName.endsWith(".ttc");
    }

    /**
     * Checks if a file is a CSS file based on its extension.
     */
    public boolean isCssFile(Path path) {
        if (path == null) {
            return false;
        }
        return Files.isRegularFile(path) && 
               path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".css");
    }

    /**
     * Creates a placeholder CSS file if none exists.
     */
    public Path createPlaceholderCss(Path workingDir) throws IOException {
        Path cssFile = workingDir.resolve("styles.css");
        if (!Files.exists(cssFile)) {
            Files.writeString(cssFile, "");
        }
        tempFileManager.registerForCleanup(cssFile);
        return cssFile;
    }

    /**
     * Renders HTML content through Thymeleaf if necessary.
     */
    public String renderThymeleafIfNecessary(String htmlContent, 
                                            PdfGenerationRequest request, 
                                            Map<String, Object> model) {
        if (htmlContent == null) {
            return null;
        }

        if (!templateRenderingService.shouldRenderTemplate(request, htmlContent)) {
            return htmlContent;
        }

        try {
            return templateRenderingService.renderTemplate(htmlContent, model, false);
        } catch (PdfGenerationException e) {
            logger.warn("Thymeleaf rendering failed, falling back to raw HTML: {}", e.getMessage());
            return htmlContent;
        }
    }

    /**
     * Renders Thymeleaf template directly to a file.
     */
    public void renderThymeleafTemplateToFile(Path htmlFile, 
                                             PdfGenerationRequest request, 
                                             Map<String, Object> model) throws IOException {
        if (htmlFile == null || !Files.exists(htmlFile)) {
            return;
        }

        String htmlContent = Files.readString(htmlFile, StandardCharsets.UTF_8);
        if (!templateRenderingService.shouldRenderTemplate(request, htmlContent)) {
            return;
        }

        try {
            String rendered = templateRenderingService.renderTemplate(htmlContent, model, false);
            if (rendered != null && !rendered.equals(htmlContent)) {
                Files.writeString(htmlFile, rendered, StandardCharsets.UTF_8);
            }
        } catch (PdfGenerationException e) {
            logger.warn("Thymeleaf file rendering failed, keeping original HTML: {}", e.getMessage());
        }
    }

    /**
     * Resolves an identifier for the HTML content (for logging purposes).
     */
    public String resolveHtmlIdentifier(Path htmlFile, PdfGenerationRequest request) {
        if (htmlFile != null) {
            Path fileName = htmlFile.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        }

        if (request != null && StringUtils.hasText(request.getReport())) {
            return request.getReport();
        }

        return "inline-html";
    }

    /**
     * Logs the final rendered HTML content.
     */
    public void logRenderedHtml(String context, String identifier, String htmlContent) {
        if (!logger.isInfoEnabled() && !logger.isDebugEnabled()) {
            return;
        }

        // IMPORTANT: Never dump full HTML by default.
        // Large reports can be 10s-100s of MB and will explode logs and memory.
        // To explicitly enable full HTML logging, set: LOG_RENDERED_HTML=true
        boolean allowFullHtmlLog = "true".equalsIgnoreCase(System.getenv("LOG_RENDERED_HTML"));

        int length = htmlContent != null ? htmlContent.length() : 0;
        if (htmlContent == null) {
            logger.info("Final HTML (context={}, id={}) is <null>", context, identifier);
            return;
        }

        if (logger.isInfoEnabled()) {
            logger.info("Final HTML rendered (context={}, id={}, length={} chars)", context, identifier, length);
            if (allowFullHtmlLog && logger.isDebugEnabled()) {
                logger.debug("Final HTML (context={}, id={}): {}", context, identifier, htmlContent);
            }
            return;
        }

        // DEBUG-only small preview (safe by default)
        int maxPreview = 2000;
        String printable = htmlContent.length() > maxPreview
                ? htmlContent.substring(0, maxPreview) + "\n...[truncated]"
                : htmlContent;
        logger.debug("Final HTML preview (context={}, id={}, length={} chars): {}", context, identifier, length, printable);
    }

    /**
     * Logs the final rendered HTML when it exists as a file.
     * Avoids loading very large HTML files into memory.
     */
    public void logRenderedHtmlFile(String context, String identifier, Path htmlFile) {
        if (!logger.isInfoEnabled() && !logger.isDebugEnabled()) {
            return;
        }

        if (htmlFile == null || !Files.exists(htmlFile)) {
            logger.info("Final HTML file (context={}, id={}) is <missing>", context, identifier);
            return;
        }

        try {
            long sizeBytes = Files.size(htmlFile);
            logger.info("Final HTML rendered (context={}, id={}, sizeBytes={})", context, identifier, sizeBytes);

            // Only load content into memory for small files.
            long maxInlineBytes = 2L * 1024 * 1024; // 2MB
            if (sizeBytes <= maxInlineBytes) {
                String html = Files.readString(htmlFile, StandardCharsets.UTF_8);
                logRenderedHtml(context, identifier, html);
            }
        } catch (Exception e) {
            logger.warn("Failed to log HTML file info (context={}, id={}, path={}): {}",
                    context, identifier, htmlFile, e.getMessage());
        }
    }

    /**
     * Builds a safe filename for a report.
     */
    public String buildReportFileName(String report) {
        if (!StringUtils.hasText(report)) {
            return "generated.pdf";
        }

        String sanitized = report.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (sanitized.length() > 60) {
            sanitized = sanitized.substring(0, 60);
        }
        return sanitized + ".pdf";
    }

    /**
     * Extracts file extension from filename.
     */
    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    public TempFileManager getTempFileManager() {
        return tempFileManager;
    }

    public TemplateRenderingService getTemplateRenderingService() {
        return templateRenderingService;
    }
}
