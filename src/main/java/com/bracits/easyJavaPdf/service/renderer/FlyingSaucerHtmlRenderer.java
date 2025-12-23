package com.bracits.easyJavaPdf.service.renderer;

import com.bracits.easyJavaPdf.model.PageOrientation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Flying Saucer (OpenPDF) based HTML to PDF renderer.
 * Alternative renderer using OpenPDF backend.
 */
@Component
public class FlyingSaucerHtmlRenderer implements HtmlToPdfRenderer {

    private static final Logger logger = LoggerFactory.getLogger(FlyingSaucerHtmlRenderer.class);
    private static final String RENDERER_NAME = "flyingsaucer";

    @Override
    public byte[] render(String htmlContent, String cssContent, List<Path> fontFiles,
                         String password, PageOrientation orientation, Path resourceBasePath) {
        // Flying Saucer implementation would go here
        // For now, this is a placeholder that can be implemented if needed
        throw new UnsupportedOperationException(
                "Flying Saucer renderer is not yet implemented. " +
                "Use 'itext' or 'chromium' renderer instead.");
    }

    @Override
    public String getName() {
        return RENDERER_NAME;
    }

    @Override
    public boolean isAvailable() {
        // Return false until implementation is complete
        return false;
    }
}
