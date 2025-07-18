package com.bracits.easyJavaPdf.util;

import com.bracits.easyJavaPdf.dto.PdfMergeRequest;
import com.bracits.easyJavaPdf.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfMergeRequestValidatorTest {

    @Mock
    private MultipartFile mockPdfFile;
    
    @Mock
    private MultipartFile mockImageFile;

    @Test
    void validate_WithNullRequest_ShouldThrowValidationException() {
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(null));
        
        assertEquals("PDF merge request cannot be null", exception.getMessage());
    }

    @Test
    void validate_WithValidMinimalRequest_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithValidCompleteRequest_ShouldNotThrowException() {
        PdfMergeRequest request = createValidCompleteRequest();
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithNullFiles_ShouldThrowValidationException() {
        PdfMergeRequest request = new PdfMergeRequest();
        request.setFiles(null);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertEquals("At least one file is required for merging", exception.getMessage());
    }

    @Test
    void validate_WithEmptyFilesList_ShouldThrowValidationException() {
        PdfMergeRequest request = new PdfMergeRequest();
        request.setFiles(Collections.emptyList());
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertEquals("At least one file is required for merging", exception.getMessage());
    }

    @Test
    void validate_WithTooManyFiles_ShouldThrowValidationException() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        // Create 101 files (exceeds limit of 100)
        List<MultipartFile> files = IntStream.range(0, 101)
            .mapToObj(i -> createMockPdfFile("file" + i + ".pdf", 1024L))
            .toList();
        
        request.setFiles(files);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Too many files for merging"));
        assertTrue(exception.getMessage().contains("Maximum allowed: 100"));
    }

    @Test
    void validate_WithDuplicateFilenames_ShouldThrowValidationException() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        MultipartFile file1 = createMockPdfFile("duplicate.pdf", 1024L);
        MultipartFile file2 = createMockPdfFile("duplicate.pdf", 2048L);
        
        request.setFiles(Arrays.asList(file1, file2));
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Duplicate filename found"));
        assertTrue(exception.getMessage().contains("duplicate.pdf"));
    }

    @Test
    void validate_WithTotalFileSizeExceedsLimit_ShouldThrowValidationException() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        // Create files with total size > 100MB
        long largeFileSize = 60 * 1024 * 1024; // 60MB each
        MultipartFile file1 = createMockPdfFile("file1.pdf", largeFileSize);
        MultipartFile file2 = createMockPdfFile("file2.pdf", largeFileSize);
        
        request.setFiles(Arrays.asList(file1, file2));
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Total size of all files exceeds maximum allowed size"));
    }

    @Test
    void validate_WithLongPagesDefinition_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        String longPagesDefinition = "a".repeat(1001); // Exceeds 1000 character limit
        request.setPagesDefinition(longPagesDefinition);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Page definition is too long"));
    }

    @Test
    void validate_WithInvalidPagesDefinitionFormat_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPagesDefinition("invalid@#$%format");
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Invalid page range format"));
    }

    @Test
    void validate_WithValidSimplePagesDefinition_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPagesDefinition("file1.pdf~1:3 file2.pdf~2:5");
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithValidJsonPagesDefinition_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPagesDefinition("[{\"file\":\"file1.pdf\",\"range\":\"1:3\"}]");
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithMalformedJsonPagesDefinition_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPagesDefinition("[{\"file\":\"file1.pdf\",\"range\":\"1:3\""); // Missing closing brace
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Malformed JSON page range definition"));
    }

    @Test
    void validate_WithInvalidDisposition_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setDisposition("invalid");
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Invalid disposition"));
    }

    @Test
    void validate_WithValidDispositionInline_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setDisposition("inline");
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithValidDispositionAttachment_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setDisposition("attachment");
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithLongFileName_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        String longFileName = "a".repeat(256) + ".pdf"; // Exceeds 255 character limit
        request.setFileName(longFileName);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Filename is too long"));
    }

    @Test
    void validate_WithFileNameWithoutPdfExtension_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setFileName("document.txt");
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Filename must end with .pdf extension"));
    }

    @Test
    void validate_WithFileNameWithInvalidCharacters_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setFileName("document@#$.pdf");
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Filename contains invalid characters"));
    }

    @Test
    void validate_WithValidFileName_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setFileName("valid_document-name.pdf");
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithShortPassword_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPassword("123"); // Too short (less than 4 characters)
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Password is too short"));
    }

    @Test
    void validate_WithLongPassword_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPassword("a".repeat(129)); // Too long (exceeds 128 characters)
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Password is too long"));
    }

    @Test
    void validate_WithInvalidPasswordCharacters_ShouldThrowValidationException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPassword("password\u007F\u0080"); // Contains DEL and extended ASCII characters
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Password contains invalid characters"));
    }

    @Test
    void validate_WithValidPassword_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPassword("ValidPassword123!@#");
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithResourceOptimizerOnSingleFile_ShouldThrowValidationException() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        MultipartFile singleFile = createMockPdfFile("single.pdf", 1024L);
        request.setFiles(Collections.singletonList(singleFile));
        request.setResourceOptimizer(true);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Resource optimizer is not beneficial when merging only one file"));
    }

    @Test
    void validate_WithPageRangeReferencingMoreFilesThanProvided_ShouldThrowValidationException() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        // Provide only 1 file but reference 2 files in page range
        MultipartFile file = createMockPdfFile("file1.pdf", 1024L);
        request.setFiles(Collections.singletonList(file));
        request.setPagesDefinition("file1.pdf~1:3 file2.pdf~2:5");
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Page range definition references more files"));
    }

    @Test
    void validate_WithJsonPageRangeReferencingMoreFilesThanProvided_ShouldThrowValidationException() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        // Provide only 1 file but reference 2 files in JSON page range
        MultipartFile file = createMockPdfFile("file1.pdf", 1024L);
        request.setFiles(Collections.singletonList(file));
        request.setPagesDefinition("[{\"file\":\"file1.pdf\",\"range\":\"1:3\"},{\"file\":\"file2.pdf\",\"range\":\"2:5\"}]");
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfMergeRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Page range definition references more files"));
    }

    @Test
    void validate_WithNullPassword_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPassword(null);
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithEmptyPassword_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPassword("   "); // Empty after trim
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithNullPagesDefinition_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPagesDefinition(null);
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithEmptyPagesDefinition_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setPagesDefinition("   "); // Empty after trim
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithNullDisposition_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setDisposition(null);
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithNullFileName_ShouldNotThrowException() {
        PdfMergeRequest request = createValidMinimalRequest();
        
        request.setFileName(null);
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    @Test
    void validate_WithMixedPdfAndImageFiles_ShouldNotThrowException() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        MultipartFile pdfFile = createMockPdfFile("document.pdf", 1024L);
        MultipartFile imageFile = createMockImageFile("image.jpg", 2048L);
        
        request.setFiles(Arrays.asList(pdfFile, imageFile));
        
        assertDoesNotThrow(() -> PdfMergeRequestValidator.validate(request));
    }

    private PdfMergeRequest createValidMinimalRequest() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        MultipartFile file = createMockPdfFile("document.pdf", 1024L);
        request.setFiles(Collections.singletonList(file));
        
        return request;
    }

    private PdfMergeRequest createValidCompleteRequest() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        MultipartFile pdfFile = createMockPdfFile("document1.pdf", 1024L);
        MultipartFile imageFile = createMockImageFile("image.jpg", 2048L);
        
        request.setFiles(Arrays.asList(pdfFile, imageFile));
        request.setPagesDefinition("document1.pdf~1:3 image.jpg");
        request.setDisposition("attachment");
        request.setFileName("merged_document.pdf");
        request.setPassword("validPassword123");
        request.setResourceOptimizer(true);
        
        return request;
    }

    private MultipartFile createMockPdfFile(String filename, long size) {
        MultipartFile mockFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getSize()).thenReturn(size);
        return mockFile;
    }

    private MultipartFile createMockImageFile(String filename, long size) {
        MultipartFile mockFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getSize()).thenReturn(size);
        return mockFile;
    }
}