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
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.IElement;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.html2pdf.HtmlConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

class Footer extends AbstractPdfDocumentEventHandler {
  protected PdfFormXObject placeholder;
  protected float side = 20;
  protected float x = 300;
  protected float y = 25;
  protected float space = 4.5f;
  protected float descent = 3;
  private final String footerHtmlContent;

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

    List<IElement> elements = null;
    try {
      elements = HtmlConverter.convertToElements(new ByteArrayInputStream(footerHtmlContent.getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    for (IElement element : elements) {
      if (element instanceof Paragraph) {
        ((Paragraph) element).add(" Page " + pageNumber);
        canvas.showTextAligned((Paragraph) element, x, y, TextAlignment.RIGHT);
      } else {
        canvas.add((IBlockElement) element);
      }
    }

    canvas.close();
    pdfCanvas.addXObjectAt(placeholder, x + space, y - descent);
    pdfCanvas.release();
  }
}