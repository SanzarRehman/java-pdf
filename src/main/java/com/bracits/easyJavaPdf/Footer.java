package com.bracits.easyJavaPdf;

import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.IElement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

class Footer extends AbstractPdfDocumentEventHandler {
  protected PdfFormXObject placeholder;
  protected float side = 20;
  protected float x = 300;
  protected float bottomMargin = 15;
  private final String footerHtmlContent;

  public Footer(String footerHtmlContent) {
    this.footerHtmlContent = footerHtmlContent;
    placeholder = new PdfFormXObject(new Rectangle(0, 0, side, side));
  }

  @Override
  protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
    PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
    PdfPage page = docEvent.getPage();
    Rectangle pageSize = page.getPageSize();

    PdfCanvas pdfCanvas = new PdfCanvas(page);
    Canvas canvas = new Canvas(pdfCanvas, pageSize);

    // Parse and render the HTML content for the footer
    List<IElement> elements;
    try {
      elements = HtmlConverter.convertToElements(new ByteArrayInputStream(footerHtmlContent.getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new RuntimeException("Error parsing footer HTML content", e);
    }

    // Add parsed HTML elements to the canvas
    for (IElement element : elements) {
      if (element instanceof IBlockElement) {
        canvas.add((IBlockElement) element);
      }
    }

    // Footer placement logic (adjust `footerY` for proper placement)
    float footerY = pageSize.getBottom() + bottomMargin;
    float footerWidth = pageSize.getWidth();

    // Set fixed position for the footer
    canvas.setFixedPosition(0, footerY, footerWidth);

    canvas.close();
    pdfCanvas.release();
  }
}
