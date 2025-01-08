package com.bracits.easyJavaPdf;

import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.BlockElement;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.IElement;

import java.util.List;

public class Footer extends AbstractPdfDocumentEventHandler {
  private final String footerHtmlContent;

  public Footer(String footerHtmlContent) {
    this.footerHtmlContent = footerHtmlContent;
  }

  @Override
  protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
    PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
    PdfDocument pdf = docEvent.getDocument();
    PdfPage page = docEvent.getPage();
    int pageNumber = pdf.getPageNumber(page);
    Rectangle pageSize = page.getPageSize();

    float footerY = pageSize.getBottom() + 30;

    try {
      PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
      Canvas canvas = new Canvas(pdfCanvas, pageSize);

      String footerHtmlWithPage = footerHtmlContent.replace("{{pageNumber}}", String.valueOf(pageNumber));

      List<IElement> elements = HtmlConverter.convertToElements(footerHtmlWithPage);

      for (IElement element : elements) {
        if (element instanceof BlockElement) {
          BlockElement<?> block = (BlockElement<?>) element;
          canvas.add((IBlockElement) block.setFixedPosition(pageSize.getWidth() / 2 - 100, footerY, 200));
        }
      }

      canvas.close();
      pdfCanvas.release();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
