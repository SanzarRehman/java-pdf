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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class PdfService {

  private static final Logger logger = LoggerFactory.getLogger(PdfService.class);
  private static final String CHROME_DRIVER_PATH = "src/main/resources/chromedriver";
  private static final String DEFAULT_PAGE_CSS = "@page { size: A4 portrait; margin: 1cm; }";

  private final Executor executor;

  public PdfService(@Qualifier("executor") Executor executor) {
    this.executor = executor;
  }

  /**
   * Generates PDF from HTML content with optional header, footer, and styling.
   *
   * @param htmlFile HTML file to convert
   * @param cssFile CSS file for styling (optional)
   * @param headerHtml Header HTML content (optional)
   * @param footerHtml Footer HTML content (optional)
   * @param banglaFooterHtml Bengali footer content (optional)
   * @param fontFiles List of font files to embed (optional)
   * @param password Password for PDF encryption (optional)
   * @param jsEnable Enable JavaScript execution during conversion
   * @return CompletableFuture containing the generated PDF as byte array
   */
  @Async
  public CompletableFuture<byte[]> generate(
      Path htmlFile,
      Path cssFile,
      String headerHtml,
      String footerHtml,
      String banglaFooterHtml,
      List<Path> fontFiles,
      String password,
      String jsEnable) {

    logger.info("Starting PDF generation for file: {}", htmlFile);
    long startTime = System.currentTimeMillis();

    return CompletableFuture.supplyAsync(() -> {
      try {
        byte[] pdfBytes = generatePdfInternal(htmlFile, cssFile, headerHtml, footerHtml, 
            banglaFooterHtml, fontFiles, password, jsEnable);
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("PDF generation completed successfully in {} ms, size: {} bytes", 
            duration, pdfBytes.length);
        
        return pdfBytes;
      } catch (Exception e) {
        logger.error("PDF generation failed for file: {}", htmlFile, e);
        throw new PdfGenerationException("Failed to generate PDF from HTML file: " + htmlFile, e);
      }
    }, executor);
  }

  /**
   * Internal method that handles the actual PDF generation process.
   */
  private byte[] generatePdfInternal(Path htmlFile, Path cssFile, String headerHtml, 
      String footerHtml, String banglaFooterHtml, List<Path> fontFiles, 
      String password, String jsEnable) {
    
    logger.debug("Processing HTML and CSS content");
    String processedHtml = processHtmlContent(htmlFile, cssFile, jsEnable);
    
    logger.debug("Setting up converter properties");
    ConverterProperties converterProperties = setupConverterProperties(fontFiles);
    
    logger.debug("Creating PDF document");
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      PdfWriter writer = createPdfWriter(outputStream, password);
      PdfDocument pdfDocument = new PdfDocument(writer);
      
      configureEventHandlers(pdfDocument, headerHtml, footerHtml, banglaFooterHtml);
      
      logger.debug("Converting HTML to PDF");
      HtmlConverter.convertToPdf(processedHtml, pdfDocument, converterProperties);
      pdfDocument.close();
      
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new PdfGenerationException("Error during PDF document creation", e);
    }
  }

  /**
   * Processes HTML content by reading files, applying CSS, and optionally executing JavaScript.
   */
  private String processHtmlContent(Path htmlFile, Path cssFile, String jsEnable) {
    try {
      logger.debug("Reading HTML content from: {}", htmlFile);
      String htmlContent = Files.readString(htmlFile, StandardCharsets.UTF_8);
      
      String cssContent = "";
      if (cssFile != null) {
        logger.debug("Reading CSS content from: {}", cssFile);
        cssContent = Files.readString(cssFile, StandardCharsets.UTF_8);
      }
      
      cssContent += DEFAULT_PAGE_CSS;
      String wrappedHtml = wrapHtmlWithCss(htmlContent, cssContent);
      
      if (isJavaScriptEnabled(jsEnable)) {
        logger.debug("JavaScript execution enabled, processing with Chrome driver");
        return executeJavaScript(wrappedHtml);
      }
      
      return wrappedHtml;
    } catch (IOException e) {
      throw new FileProcessingException("Failed to read HTML or CSS file", e);
    }
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

