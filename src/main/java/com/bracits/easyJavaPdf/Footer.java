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

class Footer extends AbstractPdfDocumentEventHandler {
  protected PdfFormXObject placeholder;
  protected float side = 20;
  protected float x = 300;
  protected float y = 25;
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

    float footerY = pageSize.getBottom() + 30;


//    Paragraph footerContent = new Paragraph("Page " + banglaPageNumber + "\n© 2025 Your Company Name")
//        .setTextAlignment(TextAlignment.CENTER)
//        .setFontSize(10)
//        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY);

//    canvas.showTextAligned(footerContent, pageSize.getWidth() / 2, footerY, TextAlignment.CENTER);

    canvas.close();
    pdfCanvas.release();
  }

}