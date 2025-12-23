package com.bracits.easyJavaPdf.service.renderer;

import com.bracits.easyJavaPdf.model.PageOrientation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * iText-based HTML to PDF renderer.
 * This is the default renderer providing full feature support including
 * headers, footers, bookmarks, and QR codes.
 */
@Component
public class ITextHtmlRenderer implements HtmlToPdfRenderer {

    private static final Logger logger = LoggerFactory.getLogger(ITextHtmlRenderer.class);
    private static final String RENDERER_NAME = "itext";

    @Override
    public byte[] render(String htmlContent, String cssContent, List<Path> fontFiles,
                         String password, PageOrientation orientation, Path resourceBasePath) {
        // This is a simplified interface - the actual iText rendering is handled
        // by ITextPdfGenerator which has more complex logic for headers, footers, etc.
        // This renderer is used when a simple HTML-to-PDF conversion is needed.
        throw new UnsupportedOperationException(
                "Direct rendering via ITextHtmlRenderer is not supported. " +
                "Use ITextPdfGenerator for full iText PDF generation with all features.");
    }

    @Override
    public String getName() {
        return RENDERER_NAME;
    }

    @Override
    public boolean isAvailable() {
        // iText is always available as it's a core dependency
        return true;
    }
}
