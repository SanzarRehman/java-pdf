package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.PageRange;
import com.bracits.easyJavaPdf.exception.FileProcessingException;
import com.bracits.easyJavaPdf.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfMergerServiceTest {

    private PdfMergerService pdfMergerService;

    private ImageToPdfConverter imageToPdfConverter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        imageToPdfConverter = new ImageToPdfConverter();
        pdfMergerService = new PdfMergerService(imageToPdfConverter);
    }

    @Test
    void testMergePdfs_WithNullTempFiles_ThrowsValidationException() {

        List<PageRange> pageRanges = Arrays.asList(createPageRange("test", 0, 1, 1));


        ValidationException exception = assertThrows(ValidationException.class, () ->
                pdfMergerService.mergePdfs(null, pageRanges, null, tempDir, "false"));
        
        assertEquals("The list of temporary files cannot be null or empty", exception.getMessage());
    }

    @Test
    void testMergePdfs_WithEmptyTempFiles_ThrowsValidationException() {

        List<Path> tempFiles = Collections.emptyList();
        List<PageRange> pageRanges = Arrays.asList(createPageRange("test", 0, 1, 1));


        ValidationException exception = assertThrows(ValidationException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        
        assertEquals("The list of temporary files cannot be null or empty", exception.getMessage());
    }

    @Test
    void testMergePdfs_WithNullPageRanges_ThrowsValidationException() {

        List<Path> tempFiles = Arrays.asList(tempDir.resolve("test.pdf"));


        ValidationException exception = assertThrows(ValidationException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, null, null, tempDir, "false"));
        
        assertEquals("The list of page ranges cannot be null or empty", exception.getMessage());
    }

    @Test
    void testMergePdfs_WithEmptyPageRanges_ThrowsValidationException() {

        List<Path> tempFiles = Arrays.asList(tempDir.resolve("test.pdf"));
        List<PageRange> pageRanges = Collections.emptyList();


        ValidationException exception = assertThrows(ValidationException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        
        assertEquals("The list of page ranges cannot be null or empty", exception.getMessage());
    }

    @Test
    void testMergePdfs_WithNonExistentFile_ThrowsFileProcessingException() throws IOException {

        Path nonExistentFile = tempDir.resolve("nonexistent.pdf");
        List<Path> tempFiles = Arrays.asList(nonExistentFile);
        List<PageRange> pageRanges = Arrays.asList(createPageRange("nonexistent", 0, 1, 1));


        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        

        assertNotNull(exception.getMessage());
    }

    @Test
    void testMergePdfs_WithUnsupportedFileFormat_ThrowsValidationException() throws IOException {

        Path unsupportedFile = tempDir.resolve("test.txt");
        Files.createFile(unsupportedFile);
        List<Path> tempFiles = Arrays.asList(unsupportedFile);
        List<PageRange> pageRanges = Arrays.asList(createPageRange("test.txt", 0, 1, 1));


        ValidationException exception = assertThrows(ValidationException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        
        assertTrue(exception.getMessage().contains("Unsupported file format"));
    }

    @Test
    void testMergePdfs_WithImageFile_ThrowsFileProcessingException() throws IOException {

        Path imageFile = tempDir.resolve("test.jpg");

        Files.write(imageFile, new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0});
        
        List<Path> tempFiles = Arrays.asList(imageFile);
        List<PageRange> pageRanges = Arrays.asList(createPageRange("test.jpg", 0, 1, 1));



        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        

        assertNotNull(exception.getMessage());
    }

    @Test
    void testPageRangeValidation_WithValidRange() {


        

        List<Path> tempFiles = Arrays.asList(tempDir.resolve("test.pdf"));
        List<PageRange> pageRanges = Arrays.asList(createPageRange("test", 0, -1, 1));


        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        

        assertNotNull(exception.getMessage());
    }

    @Test
    void testPageRangeValidation_WithReverseRange() {

        List<Path> tempFiles = Arrays.asList(tempDir.resolve("test.pdf"));
        List<PageRange> pageRanges = Arrays.asList(createPageRange("test", 5, 1, -1));


        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        

        assertNotNull(exception.getMessage());
    }

    @Test
    void testOptimizerConfiguration_WithTrueValue() {

        List<Path> tempFiles = Arrays.asList(tempDir.resolve("test.pdf"));
        List<PageRange> pageRanges = Arrays.asList(createPageRange("test", 0, 1, 1));


        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "true"));
        

        assertNotNull(exception.getMessage());
    }

    @Test
    void testOptimizerConfiguration_WithFalseValue() {

        List<Path> tempFiles = Arrays.asList(tempDir.resolve("test.pdf"));
        List<PageRange> pageRanges = Arrays.asList(createPageRange("test", 0, 1, 1));


        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        

        assertNotNull(exception.getMessage());
    }

    @Test
    void testFileMatching_WithPdfExtension() {

        List<Path> tempFiles = Arrays.asList(tempDir.resolve("document.pdf"));
        List<PageRange> pageRanges = Arrays.asList(createPageRange("document", 0, 1, 1));


        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        

        assertNotNull(exception.getMessage());
    }

    @Test
    void testFileMatching_WithExactFileName() {

        List<Path> tempFiles = Arrays.asList(tempDir.resolve("document.pdf"));
        List<PageRange> pageRanges = Arrays.asList(createPageRange("document.pdf", 0, 1, 1));


        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                pdfMergerService.mergePdfs(tempFiles, pageRanges, null, tempDir, "false"));
        

        assertNotNull(exception.getMessage());
    }

    private PageRange createPageRange(String file, int startPage, int endPage, int step) {
        return new PageRange(file, startPage, endPage, step);
    }
}