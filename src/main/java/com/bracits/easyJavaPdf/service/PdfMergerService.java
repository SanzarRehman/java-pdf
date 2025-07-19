package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.PageRange;
import com.bracits.easyJavaPdf.exception.FileProcessingException;
import com.bracits.easyJavaPdf.exception.PdfMergeException;
import com.bracits.easyJavaPdf.exception.ValidationException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class PdfMergerService {

    private static final Logger logger = LoggerFactory.getLogger(PdfMergerService.class);
    
    private final ImageToPdfConverter imageToPdfConverter;

    @Autowired
    public PdfMergerService(ImageToPdfConverter imageToPdfConverter) {
        this.imageToPdfConverter = imageToPdfConverter;
    }

    public byte[] mergePdfs(List<Path> tempFiles, List<PageRange> pageRanges, String password, Path tempLoc, String optimizer) {
        logger.info("Starting PDF merge operation with {} files and {} page ranges", 
                   tempFiles != null ? tempFiles.size() : 0, 
                   pageRanges != null ? pageRanges.size() : 0);
        
        validateMergeInputs(tempFiles, pageRanges);
        
        try {
            PDFMergerUtility pdfMerger = configurePdfMerger(optimizer);
            
            for (PageRange pageRange : pageRanges) {
                processPageRange(tempFiles, pageRange, tempLoc, pdfMerger);
            }
            
            return executeMerge(pdfMerger);
            
        } catch (IOException e) {
            logger.error("Failed to merge PDFs", e);
            throw new PdfMergeException("Failed to merge PDFs: " + e.getMessage(), e);
        }
    }

    /**
     * Validates the input parameters for PDF merging
     */
    private void validateMergeInputs(List<Path> tempFiles, List<PageRange> pageRanges) {
        if (tempFiles == null || tempFiles.isEmpty()) {
            throw new ValidationException("The list of temporary files cannot be null or empty");
        }
        if (pageRanges == null || pageRanges.isEmpty()) {
            throw new ValidationException("The list of page ranges cannot be null or empty");
        }
        
        logger.debug("Validated merge inputs: {} files, {} page ranges", tempFiles.size(), pageRanges.size());
    }

    /**
     * Configures the PDF merger utility with optimization settings
     */
    private PDFMergerUtility configurePdfMerger(String optimizer) {
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        
        if ("true".equals(optimizer)) {
            pdfMerger.setDocumentMergeMode(PDFMergerUtility.DocumentMergeMode.OPTIMIZE_RESOURCES_MODE);
            logger.debug("PDF merger configured with resource optimization");
        } else {
            pdfMerger.setDocumentMergeMode(PDFMergerUtility.DocumentMergeMode.PDFBOX_LEGACY_MODE);
            logger.debug("PDF merger configured with legacy mode");
        }
        
        return pdfMerger;
    }

    /**
     * Processes a single page range by finding the file and handling it based on type
     */
    private void processPageRange(List<Path> tempFiles, PageRange pageRange, Path tempLoc, PDFMergerUtility pdfMerger) {
        logger.debug("Processing page range for file: {}", pageRange.getFile());
        
        Path inputPath = findInputFile(tempFiles, pageRange);
        validateFileExists(inputPath);
        
        try {
            if (isPdfFile(inputPath)) {
                processPdfFile(inputPath, pageRange, tempLoc, pdfMerger);
            } else if (isImageFile(inputPath)) {
                processImageFile(inputPath, tempLoc, pdfMerger);
            } else {
                throw new ValidationException("Unsupported file format: " + inputPath.getFileName());
            }
        } catch (IOException e) {
            logger.error("Failed to process file: {}", inputPath, e);
            throw new FileProcessingException("Failed to process file: " + inputPath.getFileName(), e);
        }
    }

    /**
     * Finds the input file matching the page range specification
     */
    private Path findInputFile(List<Path> tempFiles, PageRange pageRange) {
        return tempFiles.stream()
                .filter(file -> {
                    String fileName = file.getFileName().toString();
                    return fileName.equals(pageRange.getFile() + ".pdf") || 
                           fileName.equals(pageRange.getFile());
                })
                .findFirst()
                .orElseThrow(() -> new FileProcessingException("File not found: " + pageRange.getFile()));
    }

    /**
     * Validates that the specified file exists
     */
    private void validateFileExists(Path inputPath) {
        if (Files.notExists(inputPath)) {
            throw new FileProcessingException("File does not exist: " + inputPath);
        }
    }

    /**
     * Processes a PDF file with the specified page range
     */
    private void processPdfFile(Path inputPath, PageRange pageRange, Path tempLoc, PDFMergerUtility pdfMerger) throws IOException {
        logger.debug("Processing PDF file: {} with page range {}-{}", inputPath.getFileName(), 
                    pageRange.getStartPage(), pageRange.getEndPage());
        
        try (PDDocument sourceDocument = Loader.loadPDF(inputPath.toFile())) {
            validatePageRange(pageRange, sourceDocument.getNumberOfPages());
            
            PDDocument newDocument = extractPagesFromDocument(sourceDocument, pageRange);
            String tempPdfPath = saveTempDocument(newDocument, tempLoc);
            
            pdfMerger.addSource(tempPdfPath);
            newDocument.close();
            
            logger.debug("Successfully processed PDF file: {}", inputPath.getFileName());
        }
    }

    /**
     * Validates that the page range is valid for the document
     */
    private void validatePageRange(PageRange pageRange, int totalPages) {
        if (pageRange.getStartPage() < 0 || pageRange.getEndPage() >= totalPages) {
            if (!(pageRange.getStartPage() == 0 && pageRange.getEndPage() == -1)) {
                logger.warn("Page range {}-{} may be invalid for document with {} pages", 
                           pageRange.getStartPage(), pageRange.getEndPage(), totalPages);
            }
        }
    }

    /**
     * Extracts specified pages from a PDF document
     */
    private PDDocument extractPagesFromDocument(PDDocument sourceDocument, PageRange pageRange) {
        PDDocument newDocument = new PDDocument();
        
        int start = Math.max(0, pageRange.getStartPage());
        int end = calculateEndPage(pageRange, sourceDocument.getNumberOfPages());
        int step = pageRange.getStep();
        
        logger.debug("Extracting pages from {} to {} with step {}", start, end, step);
        
        if (start <= end) {
            for (int i = start; i <= end; i += step) {
                if (i < sourceDocument.getNumberOfPages()) {
                    newDocument.addPage(sourceDocument.getPage(i));
                }
            }
        } else {
            for (int i = start; i >= end; i += step) {
                if (i >= 0 && i < sourceDocument.getNumberOfPages()) {
                    newDocument.addPage(sourceDocument.getPage(i));
                }
            }
        }
        
        return newDocument;
    }

    /**
     * Calculates the actual end page based on page range and document size
     */
    private int calculateEndPage(PageRange pageRange, int totalPages) {
        int end = Math.min(pageRange.getEndPage(), totalPages - 1);
        
        if (pageRange.getStartPage() == 0 && pageRange.getEndPage() == -1) {
            end = totalPages - 1;
        }
        
        return end;
    }

    /**
     * Processes an image file by converting it to PDF
     */
    private void processImageFile(Path inputPath, Path tempLoc, PDFMergerUtility pdfMerger) throws IOException {
        logger.debug("Processing image file: {}", inputPath.getFileName());
        
        try (PDDocument imageDocument = imageToPdfConverter.convertImageToPdf(
                inputPath.toString(), 
                ImageToPdfConverter.PageSize.A4, 
                ImageToPdfConverter.ScalingMode.FIT_PAGE, 
                0.9f)) {
            
            String tempPdfPath = saveTempDocument(imageDocument, tempLoc);
            pdfMerger.addSource(tempPdfPath);
            
            logger.debug("Successfully processed image file: {}", inputPath.getFileName());
        }
    }

    /**
     * Saves a temporary PDF document and returns its path
     */
    private String saveTempDocument(PDDocument document, Path tempLoc) throws IOException {
        String tempPdfPath = tempLoc.toString() + "/" + UUID.randomUUID().toString() + ".pdf";
        document.save(tempPdfPath);
        logger.debug("Saved temporary PDF: {}", tempPdfPath);
        return tempPdfPath;
    }

    /**
     * Executes the final merge operation
     */
    private byte[] executeMerge(PDFMergerUtility pdfMerger) throws IOException {
        logger.debug("Executing final PDF merge");
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            pdfMerger.setDestinationStream(outputStream);
            
            CompressParameters compressParameters = new CompressParameters();
            compressParameters.isCompress();
            
            pdfMerger.mergeDocuments(null, compressParameters);
            
            byte[] result = outputStream.toByteArray();
            logger.info("PDF merge completed successfully, output size: {} bytes", result.length);
            
            return result;
        }
    }

    /**
     * Checks if the file is a PDF file
     */
    private boolean isPdfFile(Path filePath) {
        return filePath.toString().toLowerCase().endsWith(".pdf");
    }

    /**
     * Checks if the file is an image file
     */
    private boolean isImageFile(Path filePath) {
        return imageToPdfConverter.isImageFile(filePath.toString());
    }

    /**
     * Converts an image file to PDF document
     */
    private PDDocument convertImageToPDF(String imageFilePath) throws IOException {
        return imageToPdfConverter.convertImageToPdf(imageFilePath);
    }
}
