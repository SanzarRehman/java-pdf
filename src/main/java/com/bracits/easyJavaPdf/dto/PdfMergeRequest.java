package com.bracits.easyJavaPdf.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * DTO for PDF merge requests containing all necessary parameters
 * for merging multiple PDF files and images with page range specifications.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PdfMergeRequest {

    /**
     * List of files to merge (PDFs and images) - required
     */
    @NotEmpty(message = "At least one file is required for merging")
    private List<MultipartFile> files;

    /**
     * Page range definition string (e.g., "1:3", ":-1", "::-1")
     */
    private String pagesDefinition;

    /**
     * Content disposition for the response (inline or attachment)
     */
    @Pattern(regexp = "^(inline|attachment)$", message = "Disposition must be either 'inline' or 'attachment'")
    private String disposition = "inline";

    /**
     * Output filename for the merged PDF
     */
    private String fileName = "merged.pdf";

    /**
     * Optional password for PDF encryption
     */
    private String password;

    /**
     * Flag to enable resource optimization for the merged document
     */
    private boolean resourceOptimizer;
}