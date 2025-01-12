package com.bracits.easyJavaPdf.controller;


import static com.bracits.easyJavaPdf.util.FileUtil.deleteDirectory;
import static com.bracits.easyJavaPdf.util.FileUtil.saveFileInDirectory;

import com.bracits.easyJavaPdf.service.PdfService;
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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;


@RestController
@RequestMapping
public class HtmlToPdfController {


  private final PdfService pdfService;

  public HtmlToPdfController(PdfService pdfService) {
    this.pdfService = pdfService;
  }


  /**
   * Generate PDF from HTML file
   *
   * @param htmlFile
   * @param cssFile
   * @param headerFile
   * @param footerFile
   * @param fontFiles
   * @param password
   * @return
   */

  @PostMapping("/api/v1.0/print")
  public ResponseEntity<byte[]> generatePdfWithPassword(
      @RequestParam("html") MultipartFile htmlFile,
      @RequestParam(value = "style", required = false) MultipartFile cssFile,
      @RequestParam(value = "header", required = false) MultipartFile headerFile,
      @RequestParam(value = "footer", required = false) MultipartFile footerFile,
      @RequestParam(value = "bangla_footer", required = false) MultipartFile banglaFooter,
      @RequestParam(value = "asset[]", required = false) MultipartFile[] fontFiles,
      @RequestParam(value = "password", required = false) String password) {

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
      String banglaFooterHtml = banglaFooter != null ? Files.readString(saveFileInDirectory(banglaFooter, tempDir)) : null;

      List<Path> tempFontFiles = new ArrayList<>();
      if (fontFiles != null) {
        for (MultipartFile fontFile : fontFiles) {
          tempFontFiles.add(saveFileInDirectory(fontFile, tempDir));
        }
      }

      CompletableFuture<byte[]> pdfFuture = pdfService.generate(
          tempHtmlFile, tempCssFile, headerHtml, footerHtml, banglaFooterHtml, tempFontFiles, password);

      byte[] pdfBytes = pdfFuture.get();

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=document_with_password.pdf")
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

}