package com.bracits.easyJavaPdf;

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

public class BengaliPageNumberHandler extends AbstractPdfDocumentEventHandler {
  private final String[] bengaliNumbers = {"০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"};
  private final String headerContent;

  public BengaliPageNumberHandler(String headerContent) {
    this.headerContent = headerContent;
  }

  @Override
  protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
    PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
    PdfDocument pdfDocument = docEvent.getDocument();
    PdfPage page = docEvent.getPage();
    Rectangle pageSize = page.getPageSize();

    PdfCanvas pdfCanvas = new PdfCanvas(page);
    Canvas canvas = new Canvas(pdfCanvas, pageSize);
    
    int pageNumber = pdfDocument.getPageNumber(page);
    int totalPages = pdfDocument.getNumberOfPages();
    String bengaliPageNumber = convertToBengali(pageNumber);
    String bengaliTotalPages = convertToBengali(totalPages);

    Paragraph header = new Paragraph(headerContent)
        .setFontSize(12)
        .setTextAlignment(TextAlignment.CENTER);

    canvas.showTextAligned(header,
        pageSize.getWidth() / 2,
        pageSize.getTop() - 30,
        TextAlignment.CENTER);

    String footerText = "পৃষ্ঠা " + bengaliPageNumber + " পাতার মধ্যে " + bengaliTotalPages;
    Paragraph footer = new Paragraph(footerText)
        .setFontSize(10)
        .setTextAlignment(TextAlignment.RIGHT);

    canvas.showTextAligned(footer,
        pageSize.getWidth() - 30,
        pageSize.getBottom() + 20,
        TextAlignment.RIGHT);

    canvas.close();
    pdfCanvas.release();
  }

  private String convertToBengali(int number) {
    StringBuilder bengaliNumber = new StringBuilder();
    for (char digit : String.valueOf(number).toCharArray()) {
      bengaliNumber.append(bengaliNumbers[Character.getNumericValue(digit)]);
    }
    return bengaliNumber.toString();
  }
}
