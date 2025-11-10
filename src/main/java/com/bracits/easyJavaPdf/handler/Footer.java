package com.bracits.easyJavaPdf.handler;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.BlockElement;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.IElement;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.styledxmlparser.jsoup.Jsoup;
import com.itextpdf.styledxmlparser.jsoup.nodes.Document;
import com.itextpdf.styledxmlparser.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handler for rendering footers in PDF documents.
 * Extends the AbstractHtmlDocumentEventHandler to render HTML content as a footer.
 */
public class Footer extends AbstractHtmlDocumentEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(Footer.class);
  private static final float FOOTER_Y_OFFSET = 30f;
  private static final float FOOTER_WIDTH = 200f;

    private boolean skipFirstPage = false;

  /**
   * Creates a new footer handler with the specified HTML content.
   *
   * @param footerHtmlContent the HTML content for the footer
   */
  public Footer(String footerHtmlContent) {
    super(footerHtmlContent);

      try {
          Document doc = Jsoup.parse(footerHtmlContent);
          Element footerDiv = doc.selectFirst(".footer");
          if (footerDiv != null) {
              String skip = footerDiv.attr("data-skip-first-page");
              if ("true".equalsIgnoreCase(skip)) {
                  skipFirstPage = true;
              }
          }
      } catch (Exception e) {
          logger.warn("Failed to parse skipFirstPage from HTML, defaulting to false", e);
      }
  }

  /**
   * Creates a PDF canvas specifically for footer rendering.
   * Uses a new content stream before the existing content to ensure footer appears behind content.
   *
   * @param page the PDF page
   * @param pdf the PDF document
   * @return the PDF canvas
   */
  @Override
  protected PdfCanvas createPdfCanvas(PdfPage page, PdfDocument pdf) {
    return new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
  }

  /**
   * Processes the HTML content before rendering, replacing page number placeholders.
   *
   * @param pdf the PDF document
   * @param page the PDF page
   * @return the processed HTML content
   */
  @Override
  protected String processHtmlContent(PdfDocument pdf, PdfPage page) {
    int pageNumber = pdf.getPageNumber(page);
    logger.debug("Processing footer for page {}", pageNumber);
      return htmlContent
              .replace("{{pageNumber}}", String.valueOf(pageNumber));
  }

  /**
   * Renders the footer elements on the canvas.
   *
   * @param canvas the canvas to render on
   * @param elements the elements to render
   * @param pageSize the page size
   */
  @Override
  protected void renderElements(Canvas canvas, List<IElement> elements, Rectangle pageSize) {
      // Skip footer rendering if page = 1 and skipFirstPage is true
      int currentPage = canvas.getPdfDocument().getPageNumber(canvas.getPdfDocument().getLastPage());
      if (skipFirstPage && currentPage == 2) {
          logger.debug("Skipping footer on page 1");
          return; // do not render footer
      }
    float footerY = pageSize.getBottom() + FOOTER_Y_OFFSET;
    float centerX = pageSize.getWidth() / 2 - FOOTER_WIDTH / 2;
    
    logger.debug("Rendering footer with {} elements at y-position {}", elements.size(), footerY);
    
    for (IElement element : elements) {
      if (element instanceof BlockElement) {
        BlockElement<?> block = (BlockElement<?>) element;
        canvas.add((IBlockElement) block.setFixedPosition(centerX, footerY, FOOTER_WIDTH));
      }
    }
  }
}
