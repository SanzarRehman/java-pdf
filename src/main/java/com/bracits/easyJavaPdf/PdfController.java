package com.bracits.easyJavaPdf;


import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;


@RestController
@RequestMapping
public class PdfController {


  private final PdfService pdfService;

  public PdfController(PdfService pdfService) {
    this.pdfService = pdfService;
  }


  @PostMapping("/generate")
  public ResponseEntity<byte[]> generatePdfWithTempFiles(
      @RequestParam("file") MultipartFile htmlFile,
      @RequestParam(value = "style", required = false) MultipartFile cssFile,
      @RequestParam(value = "fonts", required = false) MultipartFile[] fontFiles,
      @RequestParam Map<String, String> formData) {

    Path parentTempDir = null;
    Path tempDir = null;

    try {
      // Ensure that the main parent temporary directory exists (only once)
      parentTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "pdf_temp_");
      if (!Files.exists(parentTempDir)) {
        Files.createDirectories(parentTempDir);
      }

      tempDir = Files.createTempDirectory(parentTempDir, "request_");
      Path tempHtmlFile = saveFileInDirectory(htmlFile, tempDir);
      Path tempCssFile = cssFile != null ? saveFileInDirectory(cssFile, tempDir) : null;

      List<Path> tempFontFiles = new ArrayList<>();
      if (fontFiles != null) {
        for (MultipartFile fontFile : fontFiles) {
          tempFontFiles.add(saveFileInDirectory(fontFile, tempDir));
        }
      }
      CompletableFuture<byte[]> pdfFuture = pdfService.generatePdfFromTempFiles(
          tempHtmlFile, tempCssFile, tempFontFiles, new HashMap<>(formData));

      byte[] pdfBytes = pdfFuture.get();

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=document.pdf")
          .contentType(MediaType.APPLICATION_PDF)
          .body(pdfBytes);
    } catch (IOException | InterruptedException | ExecutionException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(("Error generating PDF: " + e.getMessage()).getBytes());
    } finally {
      if (tempDir != null) {
        deleteDirectory(tempDir);
      }
    }
  }

  private Path saveFileInDirectory(MultipartFile file, Path directory) throws IOException {
    if (!Files.exists(directory)) {
      Files.createDirectories(directory);
    }

    Path filePath = directory.resolve(file.getOriginalFilename());
    Files.write(filePath, file.getBytes());
    System.out.println("Saved file: " + filePath.toAbsolutePath());
    return filePath;
  }

  private void deleteDirectory(Path directory) {
    try (Stream<Path> files = Files.walk(directory)) {
      files.sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    } catch (IOException e) {
      System.err.println("Failed to delete directory: " + directory);
    }
  }

  /**
   * generatePdfWithHeaderFooter
   */



  @PostMapping("/generate-with-header-footer")
  public ResponseEntity<byte[]> generatePdfWithHeaderFooter(
      @RequestParam("file") MultipartFile htmlFile,
      @RequestParam(value = "style", required = false) MultipartFile cssFile,
      @RequestParam(value = "header", required = false) MultipartFile headerFile,
      @RequestParam(value = "footer", required = false) MultipartFile footerFile,
      @RequestParam(value = "fonts", required = false) MultipartFile[] fontFiles,
      @RequestParam Map<String, String> formData) {

    Path parentTempDir = null;
    Path tempDir = null;

    try {
      parentTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "pdf_temp_");
      if (!Files.exists(parentTempDir)) {
        Files.createDirectories(parentTempDir);
      }

      tempDir = Files.createTempDirectory(parentTempDir, "request_");

      Path tempHtmlFile = saveFileInDirectory(htmlFile, tempDir);
      Path tempCssFile = cssFile != null ? saveFileInDirectory(cssFile, tempDir) : null;

      String headerHtml = headerFile != null ? Files.readString(saveFileInDirectory(headerFile, tempDir)) : null;
      String footerHtml = footerFile != null ? Files.readString(saveFileInDirectory(footerFile, tempDir)) : null;

      List<Path> tempFontFiles = new ArrayList<>();
      if (fontFiles != null) {
        for (MultipartFile fontFile : fontFiles) {
          tempFontFiles.add(saveFileInDirectory(fontFile, tempDir));
        }
      }

      if (formData == null) {
        formData = new HashMap<>();
      }

      CompletableFuture<byte[]> pdfFuture =
          pdfService.generatePdfFromTempFilesWithHeaderFooter(
              tempHtmlFile, tempCssFile, headerHtml, footerHtml, tempFontFiles, new HashMap<>(formData));

      byte[] pdfBytes = pdfFuture.get();

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=document_with_header_footer.pdf")
          .contentType(MediaType.APPLICATION_PDF)
          .body(pdfBytes);
    } catch (IOException | InterruptedException | ExecutionException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(("Error generating PDF with header and footer: " + e.getMessage()).getBytes());
    } finally {
      if (tempDir != null) {
        deleteDirectory(tempDir);
      }
    }


  }
}