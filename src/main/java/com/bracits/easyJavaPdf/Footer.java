package com.bracits.easyJavaPdf;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

class Footer extends AbstractPdfDocumentEventHandler {
  protected PdfFormXObject placeholder;
  protected float side = 20;
  protected float x = 300;
  protected float y = 25;
  protected float space = 4.5f;
  protected float descent = 3;
  private String footerHtmlContent;

  public Footer(String footerHtmlContent) {
    this.footerHtmlContent = footerHtmlContent;
    placeholder = new PdfFormXObject(new Rectangle(0, 0, side, side));
  }

  @Override
  protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
    PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
    PdfDocument pdf = docEvent.getDocument();
    PdfPage page = docEvent.getPage();
    int pageNumber = pdf.getPageNumber(page);
    Rectangle pageSize = page.getPageSize();

    PdfCanvas pdfCanvas = new PdfCanvas(page);
    Canvas canvas = new Canvas(pdfCanvas, pageSize);

    // Calculate footer position dynamically
    float footerY = pageSize.getBottom() + 30; // 30 points above the page bottom

    // Create footer content
    Paragraph footerContent = new Paragraph("Page " + pageNumber + "\n© 2025 Your Company Name")
        .setTextAlignment(TextAlignment.CENTER)
        .setFontSize(10)
        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY);

    // Add footer content to the canvas
    canvas.showTextAligned(footerContent, pageSize.getWidth() / 2, footerY, TextAlignment.CENTER);

    canvas.close();
    pdfCanvas.release();
  }

}