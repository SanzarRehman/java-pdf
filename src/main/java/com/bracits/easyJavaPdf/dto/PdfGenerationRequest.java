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
}