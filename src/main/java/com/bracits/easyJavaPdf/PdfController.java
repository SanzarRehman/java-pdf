package com.bracits.easyJavaPdf;



import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


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

      // Create a unique subdirectory under the parent directory for this request
      tempDir = Files.createTempDirectory(parentTempDir, "request_");

      // Save files in the temp directory
      Path tempHtmlFile = saveFileInDirectory(htmlFile, tempDir);
      Path tempCssFile = cssFile != null ? saveFileInDirectory(cssFile, tempDir) : null;

      List<Path> tempFontFiles = new ArrayList<>();
      if (fontFiles != null) {
        for (MultipartFile fontFile : fontFiles) {
          tempFontFiles.add(saveFileInDirectory(fontFile, tempDir));
        }
      }

      // Generate the PDF asynchronously
      CompletableFuture<byte[]> pdfFuture = pdfService.generatePdfFromTempFiles(
          tempHtmlFile, tempCssFile, tempFontFiles, new HashMap<>(formData));

      // Wait for the result (blocking) and return it
      byte[] pdfBytes = pdfFuture.get();

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=document.pdf")
          .contentType(MediaType.APPLICATION_PDF)
          .body(pdfBytes);
    } catch (IOException | InterruptedException | ExecutionException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(("Error generating PDF: " + e.getMessage()).getBytes());
    } finally {
      // Cleanup the temporary directory (parent directory is not deleted)
      if (tempDir != null) {
        deleteDirectory(tempDir);
      }
    }
  }

  private Path saveFileInDirectory(MultipartFile file, Path directory) throws IOException {
    // Ensure the directory exists
    if (!Files.exists(directory)) {
      Files.createDirectories(directory);
    }

    // Save the file to the directory
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

}