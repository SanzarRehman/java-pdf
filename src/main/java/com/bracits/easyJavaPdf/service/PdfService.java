package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.exception.FileProcessingException;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.util.TempFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@Service
public class PdfService {

  private static final Logger logger = LoggerFactory.getLogger(PdfService.class);

  private final Executor executor;
  private final PdfGenerator pdfGenerator;
  private final TempFileManager tempFileManager;

  public PdfService(@Qualifier("executor") Executor executor, PdfGenerator pdfGenerator, TempFileManager tempFileManager) {
    this.executor = executor;
    this.pdfGenerator = pdfGenerator;
    this.tempFileManager = tempFileManager;
  }

  /**
   * Generates PDF from HTML file and associated resources.
   * This method handles the core PDF generation logic.
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
   * Generates PDF from a PdfGenerationRequest DTO.
   * This method handles the conversion from DTO to the internal generate method.
   */
  @Async
  public CompletableFuture<PdfResponse> generatePdf(PdfGenerationRequest request) {
    logger.info("Starting PDF generation from request");
    
    return CompletableFuture.supplyAsync(() -> {
      try {
        // Save uploaded files to temporary locations
        List<Path> fontFiles = new ArrayList<>();
        Path htmlFile = null;
        Path cssFile = null;
        
        // Process HTML file
        if (request.getHtmlFile() != null) {
          htmlFile = saveMultipartFile(request.getHtmlFile(), "html");
        }
        
        // Process CSS file
        if (request.getCssFile() != null) {
          cssFile = saveMultipartFile(request.getCssFile(), "css");
        }
        
        // Process asset files (fonts, images, etc.)
        if (request.getAssets() != null) {
          for (MultipartFile assetFile : request.getAssets()) {
            fontFiles.add(saveMultipartFile(assetFile, "asset"));
          }
        }
        
        // Process header and footer files to HTML strings
        String headerHtml = null;
        String footerHtml = null;
        String banglaFooterHtml = null;
        
        if (request.getHeaderFile() != null) {
          headerHtml = new String(request.getHeaderFile().getBytes());
        }
        
        if (request.getFooterFile() != null) {
          footerHtml = new String(request.getFooterFile().getBytes());
        }
        
        if (request.getBanglaFooter() != null) {
          banglaFooterHtml = new String(request.getBanglaFooter().getBytes());
        }
        
        // Generate PDF
        CompletableFuture<byte[]> pdfFuture = generate(
            htmlFile, 
            cssFile, 
            headerHtml,
            footerHtml,
            banglaFooterHtml,
            fontFiles,
            request.getPassword(),
            request.isJsEnabled() ? "true" : "false"
        );
        
        byte[] pdfBytes;
        try {
          pdfBytes = pdfFuture.get();
        } catch (InterruptedException | ExecutionException e) {
          Thread.currentThread().interrupt();
          throw new PdfGenerationException("Failed to complete PDF generation", e);
        }
        
        // Create response using the builder pattern
        PdfResponse response = PdfResponse.builder()
            .content(pdfBytes)
            .contentLength((long) pdfBytes.length)
            .fileName("generated.pdf")
            .disposition("attachment")
            .build();
        
        return response;
        
      } catch (Exception e) {
        logger.error("Failed to generate PDF from request", e);
        throw new PdfGenerationException("Failed to generate PDF", e);
      }
    }, executor);
  }

  /**
   * Saves a MultipartFile to a temporary location.
   */
  private Path saveMultipartFile(MultipartFile file, String prefix) throws IOException {
    if (file == null || file.isEmpty()) {
      return null;
    }
    
    Path tempFile = tempFileManager.createTempFile(prefix, getFileExtension(file.getOriginalFilename()));
    Files.write(tempFile, file.getBytes());
    return tempFile;
  }
  
  /**
   * Extracts file extension from filename.
   */
  private String getFileExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "";
    }
    return filename.substring(filename.lastIndexOf("."));
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

