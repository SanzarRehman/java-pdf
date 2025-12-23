package com.bracits.easyJavaPdf.service.renderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for selecting and creating PDF renderer instances based on configuration.
 * Supports: itext, flyingsaucer, chromium
 */
@Component
public class PdfRendererFactory {

    private static final Logger logger = LoggerFactory.getLogger(PdfRendererFactory.class);

    @Value("${pdf.renderer:itext}")
    private String defaultRenderer;

    private final Map<String, HtmlToPdfRenderer> renderers;

    public PdfRendererFactory(List<HtmlToPdfRenderer> rendererList) {
        this.renderers = rendererList.stream()
                .collect(Collectors.toMap(
                        HtmlToPdfRenderer::getName,
                        Function.identity()
                ));
        logger.info("Available PDF renderers: {}", renderers.keySet());
    }

    /**
     * Gets the configured default renderer.
     */
    public HtmlToPdfRenderer getDefaultRenderer() {
        return getRenderer(defaultRenderer);
    }

    /**
     * Gets a specific renderer by name.
     *
     * @param name The renderer name (itext, flyingsaucer, chromium)
     * @return The renderer instance
     * @throws IllegalArgumentException if renderer is not found
     */
    public HtmlToPdfRenderer getRenderer(String name) {
        String normalizedName = name != null ? name.toLowerCase().trim() : defaultRenderer;

        HtmlToPdfRenderer renderer = renderers.get(normalizedName);
        if (renderer == null) {
            throw new IllegalArgumentException("Unknown PDF renderer: " + name +
                    ". Available renderers: " + renderers.keySet());
        }

        if (!renderer.isAvailable()) {
            logger.warn("Renderer '{}' is not available, falling back to default", name);
            renderer = renderers.get("itext");
            if (renderer == null) {
                throw new IllegalStateException("No PDF renderer available");
            }
        }

        logger.debug("Using PDF renderer: {}", renderer.getName());
        return renderer;
    }

    /**
     * Checks if a specific renderer is available.
     */
    public boolean isRendererAvailable(String name) {
        HtmlToPdfRenderer renderer = renderers.get(name.toLowerCase().trim());
        return renderer != null && renderer.isAvailable();
    }

    /**
     * Gets the name of the configured default renderer.
     */
    public String getDefaultRendererName() {
        return defaultRenderer;
    }
}
