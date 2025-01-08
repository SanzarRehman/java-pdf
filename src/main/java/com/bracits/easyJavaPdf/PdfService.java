package com.bracits.easyJavaPdf;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.font.FontProvider;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class PdfService {


  private final Executor executor;

  public PdfService(Executor executor) {
    this.executor = executor;
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

        if (variables.containsKey("jsEnable") && variables.get("jsEnable").equals("true")) {
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

        HtmlConverter.convertToPdf(styledHtmlContent, outputStream, properties);

        return outputStream.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException("Failed to generate PDF", e);
      }
    });
  }



  private String wrapHtmlWithCss(String htmlContent, String cssContent) {
    return htmlContent;
  }


  /**
   * generatePdfWithHeaderFooter
   */


  @Async
  public CompletableFuture<byte[]> generatePdfFromTempFilesWithHeaderFooter(
      Path htmlFile,
      Path cssFile,
      String headerHtml,
      String footerHtml,
      List<Path> fontFiles,
      Map<String, String> formData) {

    return CompletableFuture.supplyAsync(() -> {
      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        ConverterProperties converterProperties = new ConverterProperties();
        if (fontFiles != null) {
          FontProvider fontProvider = new FontProvider();
          for (Path fontFile : fontFiles) {
            fontProvider.addFont(fontFile.toString());
          }
          converterProperties.setFontProvider(fontProvider);
        }

        String htmlContent = Files.readString(htmlFile, StandardCharsets.UTF_8);
        String cssContent = cssFile != null ? Files.readString(cssFile, StandardCharsets.UTF_8) : "";
        cssContent += getHeaderFooterCss(headerHtml, footerHtml);
        String finalHtmlContent = wrapHtmlWithCssH(htmlContent, cssContent);
        if (formData.containsKey("jsEnable") && formData.get("jsEnable").equals("true")) {
          System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriver"); // Path to ChromeDriver
          ChromeOptions options = new ChromeOptions();
          options.addArguments("--headless", "--disable-gpu", "--no-sandbox");
          ChromeDriver driver = new ChromeDriver(options);

          try {
            driver.navigate().to("data:text/html;charset=utf-8," + finalHtmlContent);
            finalHtmlContent = (String) driver.executeScript("return document.documentElement.innerHTML;");
          } finally {
            driver.quit();
          }
        }

        PdfDocument pdfDocument = new PdfDocument(new PdfWriter(outputStream));
        pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, new Header(headerHtml));
        pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new BengaliPageNumberHandler(footerHtml));

        HtmlConverter.convertToPdf(finalHtmlContent, pdfDocument, converterProperties);

        return outputStream.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException("Error generating PDF", e);
      }
    });
  }


  private String getHeaderFooterCss(String headerHtml, String footerHtml) {
    return "@page { size: A4 portrait; margin: 1cm; }";
  }


  private String wrapHtmlWithCssH(String htmlContent, String cssContent) {
    return "<html><head><style>" + cssContent + "</style></head><body>" + htmlContent + "</body></html>";
  }


}

