package com.bracits.easyJavaPdf.controller;


import com.bracits.easyJavaPdf.dto.PageRange;
import com.bracits.easyJavaPdf.handler.PageRangeParser;
import com.bracits.easyJavaPdf.service.PdfMergerService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping
public class Controller {


  private final PdfMergerService pdfMergerService;

  public Controller(PdfMergerService pdfMergerService) {
    this.pdfMergerService = pdfMergerService;
  }


  @PostMapping("/merge")
  public ResponseEntity<byte[]> mergePdfWithTempFiles(
      @RequestParam("files") MultipartFile[] files,
      @RequestParam(value = "pages") String pagesDefinition,
      @RequestParam(value = "disposition", defaultValue = "inline") String disposition,
      @RequestParam(value = "file_name", defaultValue = "merged.pdf") String fileName,
      @RequestParam(value = "password", required = false) String password,
      @RequestParam(value = "resourceOptimizer", required = false) String resourceOptimizer) {

    Path parentTempDir = null;
    Path tempDir = null;

    try {
      parentTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "pdf_temp_");
      if (!Files.exists(parentTempDir)) {
        Files.createDirectories(parentTempDir);
      }

      tempDir = Files.createTempDirectory(parentTempDir, "merge_request_");

      List<Path> tempFiles = new ArrayList<>();
      for (MultipartFile file : files) {
        tempFiles.add(saveFileInDirectory(file, tempDir));
      }


      List<PageRange> pageRanges = pagesDefinition != null
          ? PageRangeParser.parse(pagesDefinition, tempDir.toString())
          : Collections.emptyList();


      byte[] mergedPdfBytes = pdfMergerService.mergePdfs(tempFiles, pageRanges, password,tempDir,resourceOptimizer);


      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=" + fileName)
          .contentType(MediaType.APPLICATION_PDF)
          .body(mergedPdfBytes);

    } catch (IOException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(("Error merging PDF: " + e.getMessage()).getBytes());
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (tempDir != null) {
        deleteDirectory(tempDir);
      }
    }
  }
  private Path saveFileInDirectory(MultipartFile file, Path directory) throws IOException {
    Path filePath = directory.resolve(file.getOriginalFilename());
    Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
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


}
