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
    private static final String CHROME_DRIVER_PATH = "src/main/resources/chromedriver";
    private static final String DEFAULT_PAGE_CSS = "@page { size: A4 portrait; margin: 1cm; }";

    @Override
    public byte[] generatePdf(Path htmlFile, Path cssFile, String headerHtml, 
            String footerHtml, String banglaFooterHtml, List<Path> fontFiles, 
            String password, String jsEnable) {
        
        logger.debug("Starting PDF generation from files - HTML: {}, CSS: {}", htmlFile, cssFile);
        
        try {
            String htmlContent = readFileContent(htmlFile);
            String cssContent = cssFile != null ? readFileContent(cssFile) : "";
            
            return generatePdfInternal(htmlContent, cssContent, headerHtml, footerHtml, 
                banglaFooterHtml, fontFiles, password, jsEnable);
                
        } catch (IOException e) {
            throw new FileProcessingException("Failed to read HTML or CSS file", e);
        }
    }

    @Override
    public byte[] generatePdfFromContent(String htmlContent, String cssContent, 
            List<Path> fontFiles, String password) {
        
        logger.debug("Starting PDF generation from content strings");
        
        return generatePdfInternal(htmlContent, cssContent != null ? cssContent : "", 
            null, null, null, fontFiles, password, "false");
    }

    /**
     * Internal method that handles the core PDF generation logic.
     */
    private byte[] generatePdfInternal(String htmlContent, String cssContent, 
            String headerHtml, String footerHtml, String banglaFooterHtml, 
            List<Path> fontFiles, String password, String jsEnable) {
        
        try {
            // Process HTML content
            String processedHtml = processHtmlContent(htmlContent, cssContent, jsEnable);
            
            // Setup converter properties
            ConverterProperties converterProperties = setupConverterProperties(fontFiles);
            
            // Generate PDF
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
        String fullCssContent = cssContent + DEFAULT_PAGE_CSS;
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
    private ConverterProperties setupConverterProperties(List<Path> fontFiles) {
        ConverterProperties converterProperties = new ConverterProperties();
        
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
            System.setProperty("webdriver.chrome.driver", CHROME_DRIVER_PATH);
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
            
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
        return "<html><head><style>" + cssContent + "</style></head><body>" + htmlContent + "</body></html>";
    }

    /**
     * Checks if JavaScript execution is enabled.
     */
    private boolean isJavaScriptEnabled(String jsEnable) {
        return "true".equalsIgnoreCase(jsEnable);
    }
}