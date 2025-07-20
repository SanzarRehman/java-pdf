package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.FileProcessingException;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.handler.BengaliPageNumberHandler;
import com.bracits.easyJavaPdf.handler.Footer;
import com.bracits.easyJavaPdf.handler.Header;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.font.FontProvider;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * iText-based implementation of the PdfGenerator interface.
 * Uses iText library for PDF generation and Selenium WebDriver for JavaScript execution.
 */
@Component
public class ITextPdfGenerator implements PdfGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ITextPdfGenerator.class);
    private static final String DEFAULT_PAGE_CSS = 
        "@page { size: A4 portrait; margin: 1cm; } " +
        "* { box-sizing: border-box; margin: 0; padding: 0; } " +
        "body { margin: 0 !important; padding: 0 !important; line-height: 1.4; } " +
        "html { margin: 0 !important; padding: 0 !important; } " +
        "p, div, h1, h2, h3, h4, h5, h6 { margin-top: 0; margin-bottom: 0.5em; } " +
        "p:last-child, div:last-child { margin-bottom: 0; }";

    @Override
    public byte[] generatePdf(Path htmlFile, Path cssFile, String headerHtml, 
            String footerHtml, String banglaFooterHtml, List<Path> fontFiles, 
            String password, String jsEnable) {
        
        logger.debug("Starting PDF generation from files - HTML: {}, CSS: {}", htmlFile, cssFile);
        
        try {
            String htmlContent = readFileContent(htmlFile);
            String cssContent = cssFile != null ? readFileContent(cssFile) : "";
            
            return generatePdfInternal(htmlContent, cssContent, headerHtml, footerHtml, 
                banglaFooterHtml, fontFiles, password, jsEnable, cssFile);
                
        } catch (IOException e) {
            throw new FileProcessingException("Failed to read HTML or CSS file", e);
        }
    }

    @Override
    public byte[] generatePdfFromContent(String htmlContent, String cssContent, 
            List<Path> fontFiles, String password) {
        
        logger.debug("Starting PDF generation from content strings");
        
        // For content-based generation, we need to determine the base directory from font files
        Path cssFile = null;
        if (fontFiles != null && !fontFiles.isEmpty()) {
            // Use the directory of the first font file as the base directory
            cssFile = fontFiles.get(0).getParent().resolve("styles.css");
            try {
                // Create a temporary CSS file in the same directory as assets
                if (cssContent != null && !cssContent.trim().isEmpty()) {
                    java.nio.file.Files.write(cssFile, cssContent.getBytes());
                }
            } catch (java.io.IOException e) {
                logger.warn("Failed to create temporary CSS file, proceeding without file-based CSS resolution", e);
                cssFile = null;
            }
        }
        
        return generatePdfInternal(htmlContent, cssContent != null ? cssContent : "", 
            null, null, null, fontFiles, password, "false", cssFile);
    }

    /**
     * Internal method that handles the core PDF generation logic.
     */
    private byte[] generatePdfInternal(String htmlContent, String cssContent, 
            String headerHtml, String footerHtml, String banglaFooterHtml, 
            List<Path> fontFiles, String password, String jsEnable) {
        
        return generatePdfInternal(htmlContent, cssContent, headerHtml, footerHtml, 
            banglaFooterHtml, fontFiles, password, jsEnable, null);
    }

    /**
     * Internal method that handles the core PDF generation logic with CSS file path.
     */
    private byte[] generatePdfInternal(String htmlContent, String cssContent, 
            String headerHtml, String footerHtml, String banglaFooterHtml, 
            List<Path> fontFiles, String password, String jsEnable, Path cssFile) {
        
        try {

            String processedHtml = processHtmlContent(htmlContent, cssContent, jsEnable);
            

            ConverterProperties converterProperties = setupConverterProperties(fontFiles, cssFile);
            

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                PdfWriter writer = createPdfWriter(outputStream, password);
                PdfDocument pdfDocument = new PdfDocument(writer);
                
                configureEventHandlers(pdfDocument, headerHtml, footerHtml, banglaFooterHtml);
                
                logger.debug("Converting HTML to PDF");
                HtmlConverter.convertToPdf(processedHtml, pdfDocument, converterProperties);
                pdfDocument.close();
                
                byte[] pdfBytes = outputStream.toByteArray();
                logger.debug("PDF generation completed, size: {} bytes", pdfBytes.length);
                
                return pdfBytes;
            }
        } catch (Exception e) {
            throw new PdfGenerationException("Failed to generate PDF", e);
        }
    }

    /**
     * Processes HTML content by applying CSS and optionally executing JavaScript.
     */
    private String processHtmlContent(String htmlContent, String cssContent, String jsEnable) {
        String sanitizedCss = sanitizeCssForIText(cssContent);
        String fullCssContent = sanitizedCss + DEFAULT_PAGE_CSS;
        String wrappedHtml = wrapHtmlWithCss(htmlContent, fullCssContent);
        
        if (isJavaScriptEnabled(jsEnable)) {
            logger.debug("JavaScript execution enabled, processing with Chrome driver");
            return executeJavaScript(wrappedHtml);
        }
        
        return wrappedHtml;
    }

    /**
     * Sets up converter properties including font providers.
     */
    private ConverterProperties setupConverterProperties(List<Path> fontFiles, Path cssFile) {
        ConverterProperties converterProperties = new ConverterProperties();
        
        // Set base URI to the directory containing the CSS file (if available)
        if (cssFile != null && cssFile.getParent() != null) {
            String baseUri = cssFile.getParent().toUri().toString();
            converterProperties.setBaseUri(baseUri);
            logger.debug("Set base URI to: {}", baseUri);
        } else {
            // Fallback to system temp directory
            String tempDir = System.getProperty("java.io.tmpdir");
            if (tempDir != null) {
                converterProperties.setBaseUri("file://" + tempDir + "/");
                logger.debug("Set base URI to temp directory: {}", tempDir);
            }
        }
        
        // Configure charset
        converterProperties.setCharset(StandardCharsets.UTF_8.name());
        
        if (fontFiles != null && !fontFiles.isEmpty()) {
            logger.debug("Setting up font provider with {} fonts", fontFiles.size());
            FontProvider fontProvider = createFontProvider(fontFiles);
            converterProperties.setFontProvider(fontProvider);
        }
        
        return converterProperties;
    }

    /**
     * Creates a font provider with the specified font files.
     */
    private FontProvider createFontProvider(List<Path> fontFiles) {
        FontProvider fontProvider = new FontProvider();
        for (Path fontFile : fontFiles) {
            try {
                logger.debug("Adding font: {}", fontFile);
                fontProvider.addFont(fontFile.toString());
            } catch (Exception e) {
                logger.warn("Failed to add font: {}, continuing without it", fontFile, e);
            }
        }
        return fontProvider;
    }

    /**
     * Creates a PDF writer with optional password encryption.
     */
    private PdfWriter createPdfWriter(ByteArrayOutputStream outputStream, String password) {
        try {
            if (password != null && !password.trim().isEmpty()) {
                logger.debug("Creating encrypted PDF writer");
                return new PdfWriter(outputStream, new WriterProperties().setStandardEncryption(
                    password.getBytes(), null,
                    EncryptionConstants.ALLOW_PRINTING, EncryptionConstants.ENCRYPTION_AES_256));
            } else {
                logger.debug("Creating standard PDF writer");
                return new PdfWriter(outputStream);
            }
        } catch (Exception e) {
            throw new PdfGenerationException("Failed to create PDF writer", e);
        }
    }

    /**
     * Configures event handlers for headers and footers.
     */
    private void configureEventHandlers(PdfDocument pdfDocument, String headerHtml, 
            String footerHtml, String banglaFooterHtml) {
        
        if (headerHtml != null && !headerHtml.trim().isEmpty()) {
            logger.debug("Adding header event handler");
            pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, new Header(headerHtml));
        }

        if (footerHtml != null && !footerHtml.trim().isEmpty()) {
            logger.debug("Adding footer event handler");
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new Footer(footerHtml));
        } else if (banglaFooterHtml != null && !banglaFooterHtml.trim().isEmpty()) {
            logger.debug("Adding Bengali footer event handler");
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new BengaliPageNumberHandler(banglaFooterHtml));
        }
    }

    /**
     * Executes JavaScript in the HTML content using Chrome WebDriver.
     */
    private String executeJavaScript(String htmlContent) {
        ChromeDriver driver = null;
        try {
            logger.debug("Setting up Chrome WebDriver for JavaScript execution");
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");
            
            driver = new ChromeDriver(options);
            driver.navigate().to("data:text/html;charset=utf-8," + htmlContent);
            
            String processedHtml = (String) driver.executeScript("return document.documentElement.innerHTML;");
            logger.debug("JavaScript execution completed successfully");
            
            return processedHtml;
        } catch (Exception e) {
            throw new PdfGenerationException("Failed to execute JavaScript in HTML content", e);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.warn("Failed to close Chrome driver", e);
                }
            }
        }
    }

    /**
     * Reads content from a file.
     */
    private String readFileContent(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * Wraps HTML content with CSS styling.
     */
    private String wrapHtmlWithCss(String htmlContent, String cssContent) {
        // Clean and normalize the HTML content
        String cleanHtmlContent = htmlContent != null ? htmlContent.trim() : "";
        
        // If the content is already a complete HTML document, return it with CSS injected
        if (cleanHtmlContent.toLowerCase().contains("<html") && cleanHtmlContent.toLowerCase().contains("</html>")) {
            // Insert CSS into existing HTML structure
            if (cleanHtmlContent.toLowerCase().contains("<head>")) {
                return cleanHtmlContent.replaceFirst("(?i)<head>", "<head><style>" + cssContent + "</style>");
            } else if (cleanHtmlContent.toLowerCase().contains("<html>")) {
                return cleanHtmlContent.replaceFirst("(?i)<html>", "<html><head><style>" + cssContent + "</style></head>");
            }
        }
        
        // Wrap partial HTML content
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>" + cssContent + "</style></head><body>" + cleanHtmlContent + "</body></html>";
    }

    /**
     * Checks if JavaScript execution is enabled.
     */
    private boolean isJavaScriptEnabled(String jsEnable) {
        return "true".equalsIgnoreCase(jsEnable);
    }

    /**
     * Sanitizes CSS content to remove problematic rules that cause iText issues.
     */
    private String sanitizeCssForIText(String cssContent) {
        if (cssContent == null || cssContent.trim().isEmpty()) {
            return "";
        }

        logger.debug("Sanitizing CSS content for iText compatibility");
        
        // Step 1: Remove all @page rules completely (they cause the margin collapse issue)
        String sanitized = cssContent.replaceAll("@page[^{]*\\{(?:[^{}]*\\{[^{}]*\\}[^{}]*|[^{}])*\\}", "");
        
        // Step 2: Remove problematic pseudo-elements and selectors
        sanitized = sanitized
            // Remove ::before and ::after pseudo-elements
            .replaceAll("[^{}]*::?(?:before|after)[^{]*\\{(?:[^{}]*\\{[^{}]*\\}[^{}]*|[^{}])*\\}", "")
            // Remove complex selectors with multiple pseudo-classes
            .replaceAll("[^{}]*:(?:first-of-type|last-of-type|not\\([^)]*\\))[^{]*\\{(?:[^{}]*\\{[^{}]*\\}[^{}]*|[^{}])*\\}", "");
        
        // Step 3: Remove ALL url() references that could cause file resolution issues
        sanitized = sanitized
            // Remove ANY property containing url() - this is the most aggressive approach
            .replaceAll("[^;{}]*url\\([^)]*\\)[^;]*;", "")
            // Remove @import statements that might reference external files
            .replaceAll("@import[^;]*;", "")
            // Remove advanced layout properties
            .replaceAll("(?:display\\s*:\\s*(?:flex|grid)|align-(?:items|content)|justify-content|flex(?:-wrap|-direction)?|grid-[^:]*|columns?)\\s*:[^;]*;", "")
            // Remove page break properties
            .replaceAll("(?:break-(?:before|after|inside)|page-break-[^:]*|orphans|widows)\\s*:[^;]*;", "")
            // Remove advanced font properties
            .replaceAll("font-variant-(?:ligatures|numeric|position|caps)\\s*:[^;]*;", "")
            // Remove content property (causes issues with generated content)
            .replaceAll("content\\s*:[^;]*;", "")
            // Remove string-set and counter properties
            .replaceAll("(?:string-set|counter-[^:]*|target-[^:]*)\\s*:[^;]*;", "")
            // Remove transform and animation properties
            .replaceAll("(?:transform|animation|transition)[^:]*:[^;]*;", "")
            // Remove float property (can cause layout issues)
            .replaceAll("float\\s*:[^;]*;", "")
            // Remove position absolute/fixed (not well supported)
            .replaceAll("position\\s*:\\s*(?:absolute|fixed)[^;]*;", "");
        
        // Step 4: Clean up empty rules and normalize whitespace
        sanitized = sanitized
            .replaceAll("\\s*\\{\\s*\\}", "") // Remove empty rules
            .replaceAll("\\s+", " ") // Normalize whitespace
            .replaceAll(";\\s*;", ";") // Remove duplicate semicolons
            .trim();
        
        // Step 5: Extract and preserve @font-face rules (they're usually safe)
        StringBuilder fontFaces = new StringBuilder();
        java.util.regex.Pattern fontFacePattern = java.util.regex.Pattern.compile(
            "@font-face\\s*\\{[^{}]*\\}", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = fontFacePattern.matcher(cssContent);
        while (matcher.find()) {
            String fontFace = matcher.group();
            // Clean the font-face rule of problematic properties
            fontFace = fontFace.replaceAll("src\\s*:\\s*url\\([^)]*\\)[^;]*;", ""); // Remove font URLs that might not resolve
            if (fontFace.contains("font-family")) { // Only keep if it has font-family
                fontFaces.append(fontFace).append(" ");
            }
        }
        
        // Step 6: Build safe CSS with basic styling
        StringBuilder safeCss = new StringBuilder();
        
        // Add safe @page rule
        safeCss.append("@page { size: A4; margin: 2cm; } ");
        
        // Add preserved font faces
        safeCss.append(fontFaces);
        
        // Add sanitized CSS
        safeCss.append(sanitized);
        
        // Add some basic safe styles to prevent common issues
        safeCss.append(" body { margin: 0; padding: 1em; line-height: 1.4; } ");
        safeCss.append(" * { box-sizing: border-box; } ");
        safeCss.append(" p, div, h1, h2, h3, h4, h5, h6 { margin-bottom: 0.5em; } ");
        
        String result = safeCss.toString().trim();
        logger.debug("CSS sanitization completed, original length: {}, sanitized length: {}", 
                    cssContent.length(), result.length());
        
        return result;
    }
}