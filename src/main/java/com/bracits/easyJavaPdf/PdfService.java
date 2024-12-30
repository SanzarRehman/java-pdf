package com.bracits.easyJavaPdf;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PdfService {


  private final Executor executor;

  public PdfService(Executor executor) {
    this.executor = executor;
  }

  @Deprecated
  public byte[] generatePdfFromHtml(String htmlContent) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      HtmlConverter.convertToPdf(htmlContent, outputStream);
      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate PDF", e);
    }
  }

  @Async
  public CompletableFuture<byte[]> generatePdfFromHtml(String htmlContent, String cssContent, MultipartFile[] fontFiles) {


//    FontProgram fontProgram = FontProgramFactory.CreateFont(FONT);


    return CompletableFuture.supplyAsync(() -> {
      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        String styledHtmlContent = wrapHtmlWithCss(htmlContent, cssContent);
        HtmlConverter.convertToPdf(styledHtmlContent, outputStream);
        return outputStream.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException("Failed to generate PDF", e);
      }
    }, executor);
  }



  @Async
  public CompletableFuture<byte[]> generatePdfFromTempFiles(
      Path htmlFilePath, Path cssFilePath, List<Path> fontFilePaths, HashMap<String, String> variables) {




    return CompletableFuture.supplyAsync(() -> {
      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {


        String htmlContent = Files.readString(htmlFilePath, StandardCharsets.UTF_8);

        if(variables.containsKey("jsEnable") && variables.get("jsEnable").equals("true")) {
          System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriver");
          ChromeOptions options = new ChromeOptions();
          options.addArguments("--headless");
          ChromeDriver driver = new ChromeDriver(options);
          driver.navigate().to("data:text/html;charset=utf-8," + htmlContent);
          htmlContent = (String) driver.executeScript("return document.documentElement.innerHTML;");
        }



        String cssContent = cssFilePath != null ? Files.readString(cssFilePath, StandardCharsets.UTF_8) : null;

        String baseUrl = "file://" + htmlFilePath.getParent().toAbsolutePath().toString() + "/";


        List<byte[]> fontContents = new ArrayList<>();
        if (fontFilePaths != null) {
          for (Path fontFilePath : fontFilePaths) {
            try {

              byte[] fontData = Files.readAllBytes(fontFilePath);
              fontContents.add(fontData);
            } catch (IOException e) {
              throw new RuntimeException("Error reading font file: " + fontFilePath, e);
            }
          }
          }


        String styledHtmlContent = wrapHtmlWithCss(htmlContent, cssContent);
        ConverterProperties properties = new ConverterProperties();
        properties.setBaseUri(baseUrl);

        HtmlConverter.convertToPdf(styledHtmlContent, outputStream,properties);

        return outputStream.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException("Failed to generate PDF", e);
      }
    });
  }


  private List<PdfFont> loadFontsFromFiles(MultipartFile[] fontFiles) throws IOException {
    return List.of(fontFiles).stream()
        .map(fontFile -> {
          try {
            File tempFontFile = File.createTempFile("font", ".otf");
            fontFile.transferTo(tempFontFile);
            return PdfFontFactory.createRegisteredFont(tempFontFile.getAbsolutePath());
          } catch (IOException e) {
            throw new RuntimeException("Error loading font file", e);
          }
        })
        .collect(Collectors.toList());
  }

  private String wrapHtmlWithCss(String htmlContent, String cssContent) {
    return htmlContent;

//    if(cssContent == null || cssContent.isEmpty()) {
//      return htmlContent;
//    }
//    // Wrap HTML content with the provided CSS styles
//    return "<html><head><style>" + cssContent + "</style></head><body>" + htmlContent + "</body></html>";
  }
}
