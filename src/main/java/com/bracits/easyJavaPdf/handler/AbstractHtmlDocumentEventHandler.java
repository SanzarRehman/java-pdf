package com.bracits.easyJavaPdf.handler;

import com.bracits.easyJavaPdf.exception.PdfProcessingException;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.IElement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for HTML-based document event handlers like headers and footers.
 * Provides common functionality for rendering HTML content in PDF documents.
 */
public abstract class AbstractHtmlDocumentEventHandler extends AbstractPdfDocumentEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(AbstractHtmlDocumentEventHandler.class);
    
    protected final String htmlContent;

    /**
     * Creates a new HTML document event handler with the specified HTML content.
     *
     * @param htmlContent the HTML content to render
     */
    protected AbstractHtmlDocumentEventHandler(String htmlContent) {
        this.htmlContent = htmlContent;
    }

    @Override
    protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
        PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
        PdfDocument pdf = docEvent.getDocument();
        PdfPage page = docEvent.getPage();
        Rectangle pageSize = page.getPageSize();
        
        try {
            PdfCanvas pdfCanvas = createPdfCanvas(page, pdf);
            Canvas canvas = new Canvas(pdfCanvas, pageSize);
            
            String processedHtml = processHtmlContent(pdf, page);
            List<IElement> elements = convertHtmlToElements(processedHtml);
            
            renderElements(canvas, elements, pageSize);
            
            canvas.close();
            pdfCanvas.release();
        } catch (Exception e) {
            logger.error("Error rendering HTML content in document: {}", e.getMessage());
            throw new PdfProcessingException("Failed to render HTML content in document", e);
        }
    }
    
    /**
     * Creates a PDF canvas for rendering content.
     * 
     * @param page the PDF page
     * @param pdf the PDF document
     * @return the PDF canvas
     */
    protected PdfCanvas createPdfCanvas(PdfPage page, PdfDocument pdf) {
        return new PdfCanvas(page);
    }
    
    /**
     * Processes the HTML content before rendering.
     * Subclasses can override this method to modify the HTML content.
     * 
     * @param pdf the PDF document
     * @param page the PDF page
     * @return the processed HTML content
     */
    protected String processHtmlContent(PdfDocument pdf, PdfPage page) {
        return htmlContent;
    }
    
    /**
     * Converts HTML content to iText elements.
     * 
     * @param html the HTML content
     * @return a list of iText elements
     */
    protected List<IElement> convertHtmlToElements(String html) {
        try {
            return HtmlConverter.convertToElements(
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            logger.error("Failed to convert HTML to elements: {}", e.getMessage());
            throw new PdfProcessingException("Failed to convert HTML to elements", e);
        }
    }
    
    /**
     * Renders the elements on the canvas.
     * Subclasses must implement this method to define how elements are positioned.
     * 
     * @param canvas the canvas to render on
     * @param elements the elements to render
     * @param pageSize the page size
     */
    protected abstract void renderElements(Canvas canvas, List<IElement> elements, Rectangle pageSize);
}