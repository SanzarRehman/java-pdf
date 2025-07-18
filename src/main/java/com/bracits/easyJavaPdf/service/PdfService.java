package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class PdfService {

  private static final Logger logger = LoggerFactory.getLogger(PdfService.class);

  private final Executor executor;
  private final PdfGenerator pdfGenerator;

  public PdfService(@Qualifier("executor") Executor executor, PdfGenerator pdfGenerator) {
    this.executor = executor;
    this.pdfGenerator = pdfGenerator;
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
   * Internal method that delegates PDF generation to the PdfGenerator implementation.
   */
  private byte[] generatePdfInternal(Path htmlFile, Path cssFile, String headerHtml, 
      String footerHtml, String banglaFooterHtml, List<Path> fontFiles, 
      String password, String jsEnable) {
    
    logger.debug("Delegating PDF generation to PdfGenerator implementation");
    return pdfGenerator.generatePdf(htmlFile, cssFile, headerHtml, footerHtml, 
        banglaFooterHtml, fontFiles, password, jsEnable);
  }



}

