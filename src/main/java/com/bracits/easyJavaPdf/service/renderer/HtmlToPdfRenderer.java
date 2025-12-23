package com.bracits.easyJavaPdf.service.renderer;

import com.bracits.easyJavaPdf.model.PageOrientation;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for HTML to PDF rendering implementations.
 * Allows switching between different PDF generation backends (iText, Flying Saucer, Chromium/Puppeteer).
 */
public interface HtmlToPdfRenderer {

    /**
     * Renders HTML content to PDF bytes.
     *
     * @param htmlContent      The HTML content to render
     * @param cssContent       Optional CSS content
     * @param fontFiles        List of font files to use
     * @param password         Optional password for PDF encryption
     * @param orientation      Page orientation
     * @param resourceBasePath Base path for resolving relative resources
     * @return PDF as byte array
     */
    byte[] render(String htmlContent, String cssContent, List<Path> fontFiles,
                  String password, PageOrientation orientation, Path resourceBasePath);

    /**
     * Renders HTML content to PDF bytes with renderer tuning options.
     * Implementations may ignore options they do not support.
     */
    default byte[] render(String htmlContent, String cssContent, List<Path> fontFiles,
                          String password, PageOrientation orientation, Path resourceBasePath,
                          RendererTuning tuning) {
        return render(htmlContent, cssContent, fontFiles, password, orientation, resourceBasePath);
    }

    /**
     * Returns the name of this renderer.
     */
    String getName();

    /**
     * Checks if this renderer is available and properly configured.
     */
    boolean isAvailable();
}
