package com.bracits.easyJavaPdf.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for PDF generation operations.
 * Provides abstraction for different PDF generation implementations.
 */
public interface PdfGenerator {

    /**
     * Generates a PDF document from HTML content with optional styling and configuration.
     *
     * @param htmlFile HTML file to convert to PDF
     * @param cssFile CSS file for styling (optional)
     * @param headerHtml Header HTML content (optional)
     * @param footerHtml Footer HTML content (optional)
     * @param banglaFooterHtml Bengali footer content (optional)
     * @param fontFiles List of font files to embed (optional)
     * @param password Password for PDF encryption (optional)
     * @param jsEnable Enable JavaScript execution during conversion
     * @return Generated PDF as byte array
     * @throws com.bracits.easyJavaPdf.exception.PdfGenerationException if PDF generation fails
     */
    byte[] generatePdf(
        Path htmlFile,
        Path cssFile,
        String headerHtml,
        String footerHtml,
        String banglaFooterHtml,
        List<Path> fontFiles,
        String password,
        String jsEnable
    );

    /**
     * Generates a PDF document from HTML content string with optional configuration.
     *
     * @param htmlContent HTML content as string
     * @param cssContent CSS content as string (optional)
     * @param fontFiles List of font files to embed (optional)
     * @param password Password for PDF encryption (optional)
     * @return Generated PDF as byte array
     * @throws com.bracits.easyJavaPdf.exception.PdfGenerationException if PDF generation fails
     */
    byte[] generatePdfFromContent(
        String htmlContent,
        String cssContent,
        List<Path> fontFiles,
        String password
    );
}