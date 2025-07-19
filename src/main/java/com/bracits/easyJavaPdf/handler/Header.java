package com.bracits.easyJavaPdf.handler;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.IElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handler for rendering headers in PDF documents.
 * Extends the AbstractHtmlDocumentEventHandler to render HTML content as a header.
 */
public class Header extends AbstractHtmlDocumentEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(Header.class);
  
  protected PdfFormXObject placeholder;
  protected float side = 20;
  protected float x = 300;
  protected float y = 800;
  protected float space = 4.5f;
  protected float descent = 3;

  /**
   * Creates a new header handler with the specified HTML content.
   *
   * @param headerHtmlContent the HTML content for the header
   */
  public Header(String headerHtmlContent) {
    super(headerHtmlContent);
    placeholder = new PdfFormXObject(new Rectangle(0, 0, side, side));
    logger.debug("Header handler initialized with placeholder at position ({}, {})", x, y);
  }

  /**
   * Renders the header elements on the canvas.
   *
   * @param canvas the canvas to render on
   * @param elements the elements to render
   * @param pageSize the page size
   */
  @Override
  protected void renderElements(Canvas canvas, List<IElement> elements, Rectangle pageSize) {
    logger.debug("Rendering header with {} elements", elements.size());
    
    for (IElement element : elements) {
      if (element instanceof IBlockElement) {
        canvas.add((IBlockElement) element);
      }
    }
    
    // Add placeholder for additional content if needed
    canvas.getPdfCanvas().addXObjectAt(placeholder, x + space, y - descent);
  }
}