package com.bracits.easyJavaPdf.service;

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


  private final Executor executor;

  public PdfService(@Qualifier("executor") Executor executor) {
    this.executor = executor;
  }


  /**
   * generatePdfWithHeaderFooter
   */


  private String getHeaderFooterCss() {
    return "@page { size: A4 portrait; margin: 1cm; }";
  }


  private String wrapHtmlWithCssH(String htmlContent, String cssContent) {
    return "<html><head><style>" + cssContent + "</style></head><body>" + htmlContent + "</body></html>";
  }


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
        cssContent += getHeaderFooterCss();
        String finalHtmlContent = wrapHtmlWithCssH(htmlContent, cssContent);

        PdfWriter writer;
        if (password != null && !password.isEmpty()) {
          writer = new PdfWriter(outputStream, new WriterProperties().setStandardEncryption(
              password.getBytes(), null,
              EncryptionConstants.ALLOW_PRINTING, EncryptionConstants.ENCRYPTION_AES_256));
        } else {
          writer = new PdfWriter(outputStream);
        }

        PdfDocument pdfDocument = new PdfDocument(writer);

        if (headerHtml != null) {
          pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, new Header(headerHtml));
        }

        if (footerHtml != null) {
          pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new Footer(footerHtml));
        }

        if (banglaFooterHtml != null && footerHtml == null) {
          pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new BengaliPageNumberHandler(banglaFooterHtml));
        }


        if (jsEnable != null && jsEnable.equals("true")) {
          System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriver");
          ChromeOptions options = new ChromeOptions();
          options.addArguments("--headless");
          ChromeDriver driver = new ChromeDriver(options);
          driver.navigate().to("data:text/html;charset=utf-8," + finalHtmlContent);
          finalHtmlContent = (String) driver.executeScript("return document.documentElement.innerHTML;");
        }

        HtmlConverter.convertToPdf(finalHtmlContent, pdfDocument, converterProperties);
        pdfDocument.close();

        return outputStream.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException("Error generating PDF", e);
      }
    });
  }

}

