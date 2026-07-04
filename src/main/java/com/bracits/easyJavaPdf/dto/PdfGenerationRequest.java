package com.bracits.easyJavaPdf.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * DTO for PDF generation requests containing all necessary parameters
 * for converting HTML content to PDF with optional styling and assets.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PdfGenerationRequest {

    /**
     * The main HTML file to convert to PDF (optional if htmlContent is provided)
     */
    private MultipartFile html;

    /**
     * Optional CSS file for styling the HTML content
     */
    private MultipartFile style;

    /**
     * Optional CSS content as string (alternative to cssFile)
     */
    private String cssContent;

    /**
     * Optional HTML content as string (alternative to htmlFile)
     */
    private String htmlContent;

    /**
     * Optional header HTML file for PDF header
     */
    private MultipartFile headerFile;

    /**
     * Optional footer HTML file for PDF footer
     */
    private MultipartFile footerFile;

    /**
     * Optional Bengali footer HTML file for multilingual support
     */
    private MultipartFile banglaFooter;

    /**
     * Optional list of asset files (fonts, images, etc.)
     */
    private List<MultipartFile> asset;

    /**
     * Optional named report template stored in the classpath
     */
    private String report;

    /**
     * Optional JSON payload providing model variables for a report template
     */
    private String data;

    /**
     * Optional JSON payload providing model variables for multiple report instances
     */
    @JsonAlias("data_set")
    private String dataSet;

    /**
     * Optional password for PDF encryption
     */
    private String password;

    /**
     * Flag to enable JavaScript execution during HTML conversion
     */
    private boolean jsEnable;

    /**
     * Forces browser-based rendering pipeline with no HTML/CSS sanitization
     */
    private boolean forceBrowserMode;

    /**
     * Flag indicating whether the provided HTML should be processed as a Thymeleaf template
     */
    private boolean thymeleafTemplate;

    /**
     * Optional JSON payload describing the data model for rendering Thymeleaf templates
     */
    private String dataModel;

    /**
     * Optional JSON file containing the data model for rendering Thymeleaf templates
     */
    private MultipartFile dataModelFile;

    /**
     * Optional page orientation hint (PORTRAIT, LANDSCAPE, INVERTED_PORTRAIT, SEASCAPE)
     */
    private String pageOrientation;

    /**
     * Optional chunk size in MB for Chromium chunked renderer. If null, defaults are used.
     */
    private Integer chunkSizeMb;

    /**
     * Optional parallelism (number of chunks processed concurrently) for Chromium chunked renderer.
     * If null, defaults are used; set to 1 for strict low-memory mode.
     */
    private Integer parallelism;

    /**
     * Optional renderer selection (itext, chromium, puppeteer)
     * Overrides the default renderer configured in application.properties
     * - "itext": Use iText library for PDF generation (default)
     * - "chromium" or "puppeteer": Use headless Chromium/Puppeteer for rendering
     */
    private String renderer;

    /**
     * Optional toggle for auto fit-to-width. Playwright renderer only. Ignored by
     * renderer=chromium (relies on Chromium's own print shrink-to-fit) and renderer=itext
     * (always applies its Chromium-parity column fitting).
     */
    private Boolean fitToWidth;

    /**
     * Optional explicit print scale override (0.1-2.0; itext clamps to at most 1.0). Honored by
     * the itext, chromium and playwright renderers; when set it replaces any automatic fitting.
     */
    private Double scale;

    /**
     * Optional render mode for the Playwright renderer (renderer=playwright):
     * <ul>
     *   <li>{@code "fast"} — reuse a warm browser page across renders (Gotenberg-style):
     *       lower latency, but higher RAM (the page is recycled every N renders to bound it).</li>
     *   <li>anything else / null (default) — close the page after each render: lowest RAM,
     *       slightly slower. Safe default for tight-memory containers.</li>
     * </ul>
     * Ignored by the iText and Chromium renderers.
     */
    private String mode;
}