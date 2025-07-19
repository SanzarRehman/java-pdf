package com.bracits.easyJavaPdf.controller;

import com.bracits.easyJavaPdf.dto.PdfMergeRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.dto.PageRange;
import com.bracits.easyJavaPdf.handler.PageRangeParser;
import com.bracits.easyJavaPdf.service.PdfMergerService;
import com.bracits.easyJavaPdf.util.PdfMergeRequestValidator;
import com.bracits.easyJavaPdf.util.TempFileManager;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@RestController
@RequestMapping("/api/v1.0")
@Slf4j
public class MergeController {

    private final PdfMergerService pdfMergerService;
    private final TempFileManager tempFileManager;


    public MergeController(PdfMergerService pdfMergerService, TempFileManager tempFileManager) {
        this.pdfMergerService = pdfMergerService;
        this.tempFileManager = tempFileManager;
    }


    @PostMapping("/merge")
    public ResponseEntity<byte[]> mergePdf(@Valid PdfMergeRequest request) {
        log.info("Received merge request with {} files", request.getFiles().size());
        
        // Validate request with business rules
        PdfMergeRequestValidator.validate(request);
        
        try (TempFileManager sessionTempManager = tempFileManager) {
            // Create temporary directory for this request
            Path tempDir = sessionTempManager.createTempDirectory("merge_request_");
            log.debug("Created temporary directory: {}", tempDir);
            
            // Save uploaded files to temporary directory
            List<Path> tempFiles = new ArrayList<>();
            for (var file : request.getFiles()) {
                Path tempFile = tempDir.resolve(file.getOriginalFilename());
                file.transferTo(tempFile);
                tempFiles.add(tempFile);
                sessionTempManager.registerForCleanup(tempFile);
            }
            
            // Parse page ranges
            List<PageRange> pageRanges = request.getPagesDefinition() != null
                ? PageRangeParser.parse(request.getPagesDefinition(), tempDir.toString())
                : Collections.emptyList();
            
            // Delegate to service for merging
            byte[] mergedPdfBytes = pdfMergerService.mergePdfs(
                tempFiles, 
                pageRanges, 
                request.getPassword(),
                tempDir,
                request.isResourceOptimizer() ? "true" : null
            );
            
            // Build response using PdfResponse
            PdfResponse response = PdfResponse.builder()
                .content(mergedPdfBytes)
                .fileName(request.getFileName())
                .contentType(MediaType.APPLICATION_PDF.toString())
                .disposition(request.getDisposition())
                .contentLength((long) mergedPdfBytes.length)
                .build();
            
            // Return HTTP response
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, response.getContentDispositionHeader())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(response.getContentLength())
                .body(response.getContent());
        } catch (Exception e) {
            log.error("Error merging PDF files", e);
            throw new RuntimeException("Error merging PDF files: " + e.getMessage(), e);
        }
    }
}