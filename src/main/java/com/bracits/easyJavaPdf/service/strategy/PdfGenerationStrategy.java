package com.bracits.easyJavaPdf.service.strategy;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.model.PageOrientation;

/**
 * Strategy interface for different PDF generation approaches.
 * Each implementation handles a specific type of PDF generation (file-based, content-based, report-based).
 */
public interface PdfGenerationStrategy {

    /**
     * Determines if this strategy can handle the given request.
     *
     * @param request the PDF generation request
     * @return true if this strategy supports the request, false otherwise
     */
    boolean supports(PdfGenerationRequest request);

    /**
     * Generates PDF using this strategy's approach.
     *
     * @param request the PDF generation request
     * @param orientation the page orientation for the PDF
     * @return the PDF response containing the generated PDF bytes
     * @throws Exception if PDF generation fails
     */
    PdfResponse generate(PdfGenerationRequest request, PageOrientation orientation) throws Exception;

    /**
     * Returns the priority order of this strategy (lower number = higher priority).
     * Used when multiple strategies could support the same request.
     *
     * @return the strategy priority (0 = highest priority)
     */
    default int getPriority() {
        return Integer.MAX_VALUE;
    }
}
