package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.exception.ValidationException;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.bracits.easyJavaPdf.service.strategy.PdfGenerationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Main service for PDF generation operations.
 * Uses the Strategy pattern to delegate to appropriate generation strategies.
 */
@Service
public class PdfService {

  private static final Logger logger = LoggerFactory.getLogger(PdfService.class);

  private final Executor executor;
  private final List<PdfGenerationStrategy> strategies;

  public PdfService(@Qualifier("executor") Executor executor,
                    List<PdfGenerationStrategy> strategies) {
    this.executor = executor;
    this.strategies = strategies;
    logger.info("Initialized PdfService with {} generation strategies", strategies.size());
  }

  /**
   * Generates PDF from a PdfGenerationRequest DTO.
   * Selects appropriate strategy and delegates generation.
   */
  @Async
  public CompletableFuture<PdfResponse> generatePdf(PdfGenerationRequest request) {
    logger.info("Starting PDF generation from request");
    
    return CompletableFuture.supplyAsync(() -> {
      try {
        PageOrientation orientation = resolveOrientation(request);
        
        // Find the appropriate strategy
        PdfGenerationStrategy strategy = strategies.stream()
            .filter(s -> s.supports(request))
            .min(Comparator.comparingInt(PdfGenerationStrategy::getPriority))
            .orElseThrow(() -> new ValidationException(
                "No suitable PDF generation strategy found for the request"));
        
        logger.debug("Selected strategy: {}", strategy.getClass().getSimpleName());
        
        // Delegate to the strategy
        return strategy.generate(request, orientation);
        
      } catch (Exception e) {
        logger.error("Failed to generate PDF from request", e);
        throw new PdfGenerationException("Failed to generate PDF", e);
      }
    }, executor);
  }

  /**
   * Resolves the page orientation from the request.
   */
  private PageOrientation resolveOrientation(PdfGenerationRequest request) {
    return PageOrientation.fromOrDefault(request.getPageOrientation(), PageOrientation.PORTRAIT);
  }


}

