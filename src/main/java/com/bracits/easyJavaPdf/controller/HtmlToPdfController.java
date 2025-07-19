package com.bracits.easyJavaPdf.controller;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.service.PdfService;
import com.bracits.easyJavaPdf.util.PdfGenerationRequestValidator;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


@RestController
@RequestMapping("/api/v1.0")
public class HtmlToPdfController {

    private static final Logger logger = LoggerFactory.getLogger(HtmlToPdfController.class);
    private final PdfService pdfService;

    /**
     * Constructor for dependency injection.
     *
     * @param pdfService the PDF service for business logic
     */
    public HtmlToPdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @PostMapping("/print")
    public ResponseEntity<byte[]> generatePdf(@Valid PdfGenerationRequest request) 
            throws ExecutionException, InterruptedException {
        
        logger.info("Received PDF generation request");
        
        // Validate request with business rules
        PdfGenerationRequestValidator.validate(request);
        
        // Delegate to service layer
        CompletableFuture<PdfResponse> pdfFuture = pdfService.generatePdf(request);
        PdfResponse response = pdfFuture.get();
        
        logger.info("PDF generation completed successfully, size: {} bytes", 
                response.getContentLength());
                
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, response.getContentDispositionHeader())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(response.getContentLength())
                .body(response.getContent());
    }
}