package com.bracits.easyJavaPdf.dto;

import jakarta.validation.constraints.NotNull;
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
    private MultipartFile htmlFile;

    /**
     * Optional CSS file for styling the HTML content
     */
    private MultipartFile cssFile;

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
    private List<MultipartFile> assets;

    /**
     * Optional password for PDF encryption
     */
    private String password;

    /**
     * Flag to enable JavaScript execution during HTML conversion
     */
    private boolean jsEnabled;

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
}