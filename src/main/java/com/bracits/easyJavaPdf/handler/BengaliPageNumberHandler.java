package com.bracits.easyJavaPdf.handler;

import com.bracits.easyJavaPdf.exception.PdfProcessingException;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * Handler for rendering Bengali page numbers in PDF documents.
 * Provides header and footer with Bengali numerals.
 */
public class BengaliPageNumberHandler extends AbstractPdfDocumentEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(BengaliPageNumberHandler.class);
  

  private static final String[] BENGALI_NUMBERS = {"০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"};
  

  private static final float HEADER_FONT_SIZE = 12f;
  private static final float FOOTER_FONT_SIZE = 10f;
  private static final float HEADER_Y_OFFSET = 30f;
  private static final float FOOTER_Y_OFFSET = 20f;
  private static final float FOOTER_X_OFFSET = 30f;
  
  private final String headerContent;
  private PdfFont bengaliFont;

  /**
   * Creates a new Bengali page number handler with the specified header content.
   *
   * @param headerContent the content for the header
   */
  public BengaliPageNumberHandler(String headerContent) {
    this.headerContent = headerContent;
  }

  /**
   * Creates a new Bengali page number handler with the specified header content and font path.
   *
   * @param headerContent the content for the header
   * @param bengaliFontPath path to the Bengali font file
   */
  public BengaliPageNumberHandler(String headerContent, String bengaliFontPath) {
    this.headerContent = headerContent;
    if (bengaliFontPath != null && !bengaliFontPath.isEmpty()) {
      try {
        this.bengaliFont = PdfFontFactory.createFont(bengaliFontPath);
        logger.debug("Bengali font loaded from: {}", bengaliFontPath);
      } catch (IOException e) {
        logger.error("Failed to load Bengali font: {}", e.getMessage());
        throw new PdfProcessingException("Failed to load Bengali font", e);
      }
    }
  }

  @Override
  protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
    try {
      PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
      PdfDocument pdfDocument = docEvent.getDocument();
      PdfPage page = docEvent.getPage();
      Rectangle pageSize = page.getPageSize();

      PdfCanvas pdfCanvas = new PdfCanvas(page);
      Canvas canvas = new Canvas(pdfCanvas, pageSize);

      renderHeader(canvas, pageSize);
      renderFooter(canvas, pageSize, pdfDocument, page);

      canvas.close();
      pdfCanvas.release();
    } catch (Exception e) {
      logger.error("Error rendering Bengali page numbers: {}", e.getMessage());
      throw new PdfProcessingException("Failed to render Bengali page numbers", e);
    }
  }

  /**
   * Renders the header on the canvas.
   *
   * @param canvas the canvas to render on
   * @param pageSize the page size
   */
  protected void renderHeader(Canvas canvas, Rectangle pageSize) {
    logger.debug("Rendering Bengali header");
    
    Paragraph header = new Paragraph(headerContent)
        .setFontSize(HEADER_FONT_SIZE)
        .setTextAlignment(TextAlignment.CENTER);
    
    if (bengaliFont != null) {
      header.setFont(bengaliFont);
    }

    canvas.showTextAligned(header,
        pageSize.getWidth() / 2,
        pageSize.getTop() - HEADER_Y_OFFSET,
        TextAlignment.CENTER);
  }

  /**
   * Renders the footer with Bengali page numbers on the canvas.
   *
   * @param canvas the canvas to render on
   * @param pageSize the page size
   * @param pdfDocument the PDF document
   * @param page the PDF page
   */
  protected void renderFooter(Canvas canvas, Rectangle pageSize, PdfDocument pdfDocument, PdfPage page) {
    int pageNumber = pdfDocument.getPageNumber(page);
    int totalPages = pdfDocument.getNumberOfPages();
    
    logger.debug("Rendering Bengali footer for page {}/{}", pageNumber, totalPages);

    String bengaliPageNumber = convertToBengali(pageNumber);
    String bengaliTotalPages = convertToBengali(totalPages);

    String footerText = "পৃষ্ঠা " + bengaliPageNumber + " পাতার মধ্যে " + bengaliTotalPages;
    Paragraph footer = new Paragraph(footerText)
        .setFontSize(FOOTER_FONT_SIZE)
        .setTextAlignment(TextAlignment.RIGHT);
    
    if (bengaliFont != null) {
      footer.setFont(bengaliFont);
    }

    canvas.showTextAligned(footer,
        pageSize.getWidth() - FOOTER_X_OFFSET,
        pageSize.getBottom() + FOOTER_Y_OFFSET,
        TextAlignment.RIGHT);
  }

  /**
   * Converts an integer to Bengali numerals.
   *
   * @param number the number to convert
   * @return the Bengali numeral representation
   */
  protected String convertToBengali(int number) {
    StringBuilder bengaliNumber = new StringBuilder();
    String numberStr = String.valueOf(number);
    
    for (char digit : numberStr.toCharArray()) {
      if (Character.isDigit(digit)) {
        int digitValue = Character.getNumericValue(digit);
        if (digitValue >= 0 && digitValue < BENGALI_NUMBERS.length) {
          bengaliNumber.append(BENGALI_NUMBERS[digitValue]);
        } else {
          bengaliNumber.append(digit);
        }
      } else {
        bengaliNumber.append(digit);
      }
    }
    
    return bengaliNumber.toString();
  }
}
