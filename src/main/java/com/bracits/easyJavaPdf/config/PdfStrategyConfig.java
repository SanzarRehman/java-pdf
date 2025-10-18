package com.bracits.easyJavaPdf.config;

import com.bracits.easyJavaPdf.service.TemplateRenderingService;
import com.bracits.easyJavaPdf.service.strategy.PdfGenerationHelper;
import com.bracits.easyJavaPdf.util.TempFileManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for PDF generation strategy components.
 */
@Configuration
public class PdfStrategyConfig {

    @Bean
    public PdfGenerationHelper pdfGenerationHelper(TempFileManager tempFileManager,
                                                   TemplateRenderingService templateRenderingService) {
        return new PdfGenerationHelper(tempFileManager, templateRenderingService);
    }
}
