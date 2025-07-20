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
        // Check if we should use content-based or file-based generation
        if (hasContentBasedInput(request)) {
          return generateFromContent(request);
        } else {
          return generateFromFiles(request);
        }
      } catch (Exception e) {
        logger.error("Failed to generate PDF from request", e);
        throw new PdfGenerationException("Failed to generate PDF", e);
      }
    }, executor);
  }

  /**
   * Generates PDF from content strings (HTML content, CSS content) using a dedicated temporary folder.
   */
  private PdfResponse generateFromContent(PdfGenerationRequest request) throws IOException {
    logger.debug("Using content-based PDF generation with dedicated temp folder");
    
    // Create a dedicated temporary folder for this request
    Path requestTempDir = tempFileManager.createTempDirectory("pdf-content-request");
    logger.debug("Created content request temp directory: {}", requestTempDir);
    
    try {
      // Prepare font files in the temp directory
      List<Path> fontFiles = new ArrayList<>();
      if (request.getAssets() != null) {
        for (MultipartFile assetFile : request.getAssets()) {
          String originalName = assetFile.getOriginalFilename();
          String assetFileName = (originalName != null && !originalName.isEmpty()) ? originalName : "asset_" + System.currentTimeMillis();
          Path assetPath = saveMultipartFileToDirectory(assetFile, requestTempDir, assetFileName);
          fontFiles.add(assetPath);
        }
      }
      
      // Get HTML content
      String htmlContent = request.getHtmlContent();
      if (htmlContent == null && request.getHtmlFile() != null) {
        htmlContent = new String(request.getHtmlFile().getBytes());
      }
      
      // Get CSS content
      String cssContent = request.getCssContent();
      if (cssContent == null && request.getCssFile() != null) {
        cssContent = new String(request.getCssFile().getBytes());
      }
      
      // Generate PDF using content-based method
      byte[] pdfBytes = pdfGenerator.generatePdfFromContent(
          htmlContent, 
          cssContent, 
          fontFiles, 
          request.getPassword()
      );
      
      return PdfResponse.builder()
          .content(pdfBytes)
          .contentLength((long) pdfBytes.length)
          .fileName("generated.pdf")
          .disposition("attachment")
          .build();
          
    } finally {
      // The temp directory and all its contents will be cleaned up automatically
      logger.debug("Content request processing completed, temp directory will be cleaned up: {}", requestTempDir);
    }
  }

  /**
   * Generates PDF from uploaded files using a dedicated temporary folder per request.
   */
  private PdfResponse generateFromFiles(PdfGenerationRequest request) throws IOException, InterruptedException, ExecutionException {
    logger.debug("Using file-based PDF generation with dedicated temp folder");
    
    // Create a dedicated temporary folder for this request
    Path requestTempDir = tempFileManager.createTempDirectory("pdf-request");
    logger.debug("Created request temp directory: {}", requestTempDir);
    
    try {
      List<Path> fontFiles = new ArrayList<>();
      Path htmlFile = null;
      Path cssFile = null;
      
      // Handle HTML file
      if (request.getHtmlFile() != null) {
        htmlFile = saveMultipartFileToDirectory(request.getHtmlFile(), requestTempDir, "index.html");
      } else if (request.getHtmlContent() != null) {
        // Create HTML file from content in the request temp directory
        htmlFile = requestTempDir.resolve("index.html");
        Files.write(htmlFile, request.getHtmlContent().getBytes());
        tempFileManager.registerForCleanup(htmlFile);
      }
      
      // Handle CSS file
      if (request.getCssFile() != null) {
        String originalName = request.getCssFile().getOriginalFilename();
        String cssFileName = (originalName != null && !originalName.isEmpty()) ? originalName : "styles.css";
        cssFile = saveMultipartFileToDirectory(request.getCssFile(), requestTempDir, cssFileName);
      } else if (request.getCssContent() != null) {
        // Create CSS file from content in the request temp directory
        cssFile = requestTempDir.resolve("styles.css");
        Files.write(cssFile, request.getCssContent().getBytes());
        tempFileManager.registerForCleanup(cssFile);
      }
      
      // Handle asset files (fonts, images, etc.) - save them in the same directory
      if (request.getAssets() != null) {
        for (MultipartFile assetFile : request.getAssets()) {
          String originalName = assetFile.getOriginalFilename();
          String assetFileName = (originalName != null && !originalName.isEmpty()) ? originalName : "asset_" + System.currentTimeMillis();
          Path assetPath = saveMultipartFileToDirectory(assetFile, requestTempDir, assetFileName);
          fontFiles.add(assetPath);
        }
      }
      
      // Handle header/footer content
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
      
      // Generate PDF using file-based method
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
      
      byte[] pdfBytes = pdfFuture.get();
      
      return PdfResponse.builder()
          .content(pdfBytes)
          .contentLength((long) pdfBytes.length)
          .fileName("generated.pdf")
          .disposition("attachment")
          .build();
          
    } finally {
      // The temp directory and all its contents will be cleaned up automatically
      // when the TempFileManager is closed, but we can also clean it up immediately
      logger.debug("Request processing completed, temp directory will be cleaned up: {}", requestTempDir);
    }
  }

  /**
   * Determines if the request contains content-based input (strings) vs file-based input.
   */
  private boolean hasContentBasedInput(PdfGenerationRequest request) {
    return (request.getHtmlContent() != null || request.getCssContent() != null) &&
           (request.getHeaderFile() == null && request.getFooterFile() == null && request.getBanglaFooter() == null);
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
   * Saves a MultipartFile to a specific directory with a given filename.
   */
  private Path saveMultipartFileToDirectory(MultipartFile file, Path directory, String filename) throws IOException {
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

