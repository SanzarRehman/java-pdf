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

import java.io.IOException;
import java.nio.file.Files;
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
        

        PdfMergeRequestValidator.validate(request);

        TempFileManager sessionTempManager = tempFileManager;
        Path tempDir = null;
        List<Path> tempFiles = new ArrayList<>();
        
        try {
            tempDir = sessionTempManager.createTempDirectory("merge_request_");
            log.debug("Created temporary directory: {}", tempDir);

            for (var file : request.getFiles()) {
                Path tempFile = tempDir.resolve(file.getOriginalFilename());
                file.transferTo(tempFile);
                tempFiles.add(tempFile);
                sessionTempManager.registerForCleanup(tempFile);
            }

            List<PageRange> pageRanges = request.getPagesDefinition() != null
                ? PageRangeParser.parse(request.getPagesDefinition(), tempDir.toString())
                : Collections.emptyList();
            

            byte[] mergedPdfBytes = pdfMergerService.mergePdfs(
                tempFiles, 
                pageRanges, 
                request.getPassword(),
                tempDir,
                request.isResourceOptimizer() ? "true" : null
            );
            

            PdfResponse response = PdfResponse.builder()
                .content(mergedPdfBytes)
                .fileName(request.getFileName())
                .contentType(MediaType.APPLICATION_PDF.toString())
                .disposition(request.getDisposition())
                .contentLength((long) mergedPdfBytes.length)
                .build();
            

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, response.getContentDispositionHeader())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(response.getContentLength())
                .body(response.getContent());
        } catch (Exception e) {
            log.error("Error merging PDF files", e);
            throw new RuntimeException("Error merging PDF files: " + e.getMessage(), e);
        } finally {
            // Ensure temp files are deleted even if exception occurs
            for (Path tempFile : tempFiles) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.warn("Failed to delete temp file: {}", tempFile, ex);
                }
            }

            // Delete temp directory
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir);
                } catch (IOException ex) {
                    log.warn("Failed to delete temp directory: {}", tempDir, ex);
                }
            }
        }
    }
}


