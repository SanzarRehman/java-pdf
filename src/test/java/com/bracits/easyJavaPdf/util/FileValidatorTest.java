package com.bracits.easyJavaPdf.util;

import com.bracits.easyJavaPdf.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileValidatorTest {

    @Mock
    private MultipartFile mockFile;

    @Test
    void validateFileNotEmpty_WithNullFile_ShouldThrowValidationException() {
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateFileNotEmpty(null, "testFile"));
        
        assertEquals("testFile cannot be null", exception.getMessage());
    }

    @Test
    void validateFileNotEmpty_WithEmptyFile_ShouldThrowValidationException() {
        when(mockFile.isEmpty()).thenReturn(true);
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateFileNotEmpty(mockFile, "testFile"));
        
        assertEquals("testFile cannot be empty", exception.getMessage());
    }

    @Test
    void validateFileNotEmpty_WithValidFile_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        
        assertDoesNotThrow(() -> FileValidator.validateFileNotEmpty(mockFile, "testFile"));
    }

    @Test
    void validateFileSize_WithOversizedFile_ShouldThrowValidationException() {
        long oversizedFileSize = 25 * 1024 * 1024;
        when(mockFile.getSize()).thenReturn(oversizedFileSize);
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateFileSize(mockFile, "testFile"));
        
        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
        assertTrue(exception.getMessage().contains("25.0 MB"));
        assertTrue(exception.getMessage().contains("20.0 MB"));
    }

    @Test
    void validateFileSize_WithValidSizeFile_ShouldNotThrowException() {
        long validFileSize = 10 * 1024 * 1024;
        when(mockFile.getSize()).thenReturn(validFileSize);
        
        assertDoesNotThrow(() -> FileValidator.validateFileSize(mockFile, "testFile"));
    }

    @Test
    void validateFileSize_WithNullFile_ShouldNotThrowException() {
        assertDoesNotThrow(() -> FileValidator.validateFileSize(null, "testFile"));
    }

    @Test
    void validateHtmlFile_WithValidHtmlFile_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("text/html");
        when(mockFile.getOriginalFilename()).thenReturn("test.html");
        
        assertDoesNotThrow(() -> FileValidator.validateHtmlFile(mockFile, "htmlFile"));
    }

    @Test
    void validateHtmlFile_WithHtmlExtension_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/octet-stream");
        when(mockFile.getOriginalFilename()).thenReturn("test.htm");
        
        assertDoesNotThrow(() -> FileValidator.validateHtmlFile(mockFile, "htmlFile"));
    }

    @Test
    void validateHtmlFile_WithInvalidType_ShouldThrowValidationException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateHtmlFile(mockFile, "htmlFile"));
        
        assertEquals("htmlFile must be an HTML file (.html, .htm)", exception.getMessage());
    }

    @Test
    void validateHtmlFile_WithNullOrEmptyFile_ShouldNotThrowException() {
        assertDoesNotThrow(() -> FileValidator.validateHtmlFile(null, "htmlFile"));
        
        when(mockFile.isEmpty()).thenReturn(true);
        assertDoesNotThrow(() -> FileValidator.validateHtmlFile(mockFile, "htmlFile"));
    }

    @Test
    void validateCssFile_WithValidCssFile_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("text/css");
        when(mockFile.getOriginalFilename()).thenReturn("styles.css");
        
        assertDoesNotThrow(() -> FileValidator.validateCssFile(mockFile, "cssFile"));
    }

    @Test
    void validateCssFile_WithInvalidType_ShouldThrowValidationException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateCssFile(mockFile, "cssFile"));
        
        assertEquals("cssFile must be a CSS file (.css)", exception.getMessage());
    }

    @Test
    void validatePdfFile_WithValidPdfFile_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getOriginalFilename()).thenReturn("document.pdf");
        
        assertDoesNotThrow(() -> FileValidator.validatePdfFile(mockFile, "pdfFile"));
    }

    @Test
    void validatePdfFile_WithInvalidType_ShouldThrowValidationException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("text/html");
        when(mockFile.getOriginalFilename()).thenReturn("test.html");
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validatePdfFile(mockFile, "pdfFile"));
        
        assertEquals("pdfFile must be a PDF file (.pdf)", exception.getMessage());
    }

    @Test
    void validateImageFile_WithValidImageFile_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getOriginalFilename()).thenReturn("image.jpg");
        
        assertDoesNotThrow(() -> FileValidator.validateImageFile(mockFile, "imageFile"));
    }

    @Test
    void validateImageFile_WithPngExtension_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/octet-stream");
        when(mockFile.getOriginalFilename()).thenReturn("image.png");
        
        assertDoesNotThrow(() -> FileValidator.validateImageFile(mockFile, "imageFile"));
    }

    @Test
    void validateImageFile_WithInvalidType_ShouldThrowValidationException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateImageFile(mockFile, "imageFile"));
        
        assertTrue(exception.getMessage().contains("must be a valid image file"));
    }

    @Test
    void validateFontFile_WithValidTtfFont_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("font/ttf");
        when(mockFile.getOriginalFilename()).thenReturn("font.ttf");
        
        assertDoesNotThrow(() -> FileValidator.validateFontFile(mockFile, "fontFile"));
    }

    @Test
    void validateFontFile_WithOtfExtension_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/octet-stream");
        when(mockFile.getOriginalFilename()).thenReturn("font.otf");
        
        assertDoesNotThrow(() -> FileValidator.validateFontFile(mockFile, "fontFile"));
    }

    @Test
    void validateFontFile_WithInvalidType_ShouldThrowValidationException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateFontFile(mockFile, "fontFile"));
        
        assertTrue(exception.getMessage().contains("must be a valid font file"));
    }

    @Test
    void validatePdfOrImageFile_WithValidPdfFile_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getOriginalFilename()).thenReturn("document.pdf");
        
        assertDoesNotThrow(() -> FileValidator.validatePdfOrImageFile(mockFile, "file"));
    }

    @Test
    void validatePdfOrImageFile_WithValidImageFile_ShouldNotThrowException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("image/png");
        when(mockFile.getOriginalFilename()).thenReturn("image.png");
        
        assertDoesNotThrow(() -> FileValidator.validatePdfOrImageFile(mockFile, "file"));
    }

    @Test
    void validatePdfOrImageFile_WithInvalidType_ShouldThrowValidationException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getContentType()).thenReturn("text/html");
        when(mockFile.getOriginalFilename()).thenReturn("test.html");
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validatePdfOrImageFile(mockFile, "file"));
        
        assertTrue(exception.getMessage().contains("must be either a PDF file (.pdf) or an image file"));
    }

    @Test
    void validatePdfOrImageFile_WithNullFile_ShouldThrowValidationException() {
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validatePdfOrImageFile(null, "file"));
        
        assertEquals("file cannot be null or empty", exception.getMessage());
    }

    @Test
    void validatePdfOrImageFile_WithEmptyFile_ShouldThrowValidationException() {
        when(mockFile.isEmpty()).thenReturn(true);
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validatePdfOrImageFile(mockFile, "file"));
        
        assertEquals("file cannot be null or empty", exception.getMessage());
    }

    @Test
    void validateMergeFiles_WithNullList_ShouldThrowValidationException() {
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateMergeFiles(null));
        
        assertEquals("At least one file is required for merging", exception.getMessage());
    }

    @Test
    void validateMergeFiles_WithEmptyList_ShouldThrowValidationException() {
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateMergeFiles(Collections.emptyList()));
        
        assertEquals("At least one file is required for merging", exception.getMessage());
    }

    @Test
    void validateMergeFiles_WithValidFiles_ShouldNotThrowException() {
        MultipartFile pdfFile = createMockFile("application/pdf", "document.pdf", 1024L, false);
        MultipartFile imageFile = createMockFile("image/jpeg", "image.jpg", 2048L, false);
        
        List<MultipartFile> files = Arrays.asList(pdfFile, imageFile);
        
        assertDoesNotThrow(() -> FileValidator.validateMergeFiles(files));
    }

    @Test
    void validateMergeFiles_WithInvalidFile_ShouldThrowValidationException() {
        MultipartFile validFile = createMockFile("application/pdf", "document.pdf", 1024L, false);
        MultipartFile invalidFile = createMockFile("text/html", "test.html", 1024L, false);
        
        List<MultipartFile> files = Arrays.asList(validFile, invalidFile);
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateMergeFiles(files));
        
        assertTrue(exception.getMessage().contains("File 2"));
    }

    @Test
    void validateAssetFiles_WithNullList_ShouldNotThrowException() {
        assertDoesNotThrow(() -> FileValidator.validateAssetFiles(null));
    }

    @Test
    void validateAssetFiles_WithEmptyList_ShouldNotThrowException() {
        assertDoesNotThrow(() -> FileValidator.validateAssetFiles(Collections.emptyList()));
    }

    @Test
    void validateAssetFiles_WithValidAssets_ShouldNotThrowException() {
        MultipartFile imageAsset = createMockFile("image/png", "image.png", 1024L, false);
        MultipartFile fontAsset = createMockFile("font/ttf", "font.ttf", 2048L, false);
        
        List<MultipartFile> assets = Arrays.asList(imageAsset, fontAsset);
        
        assertDoesNotThrow(() -> FileValidator.validateAssetFiles(assets));
    }

    @Test
    void validateAssetFiles_WithInvalidAsset_ShouldThrowValidationException() {
        MultipartFile validAsset = createMockFile("image/png", "image.png", 1024L, false);
        MultipartFile invalidAsset = createMockFile("application/pdf", "test.pdf", 1024L, false);
        
        List<MultipartFile> assets = Arrays.asList(validAsset, invalidAsset);
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateAssetFiles(assets));
        
        assertTrue(exception.getMessage().contains("Asset 2"));
        assertTrue(exception.getMessage().contains("must be either an image file"));
    }

    @Test
    void validateAssetFiles_WithOversizedAsset_ShouldThrowValidationException() {
        long oversizedFileSize = 25 * 1024 * 1024;
        MultipartFile oversizedAsset = org.mockito.Mockito.mock(MultipartFile.class);
        when(oversizedAsset.isEmpty()).thenReturn(false);
        when(oversizedAsset.getSize()).thenReturn(oversizedFileSize);
        
        List<MultipartFile> assets = Arrays.asList(oversizedAsset);
        
        ValidationException exception = assertThrows(ValidationException.class, 
            () -> FileValidator.validateAssetFiles(assets));
        
        assertTrue(exception.getMessage().contains("Asset 1"));
        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
    }

    @Test
    void validateAssetFiles_WithNullAndEmptyFiles_ShouldNotThrowException() {
        MultipartFile nullFile = null;
        MultipartFile emptyFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);
        
        List<MultipartFile> assets = Arrays.asList(nullFile, emptyFile);
        
        assertDoesNotThrow(() -> FileValidator.validateAssetFiles(assets));
    }

    private MultipartFile createMockFile(String contentType, String filename, long size, boolean isEmpty) {
        MultipartFile mockFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(mockFile.getContentType()).thenReturn(contentType);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getSize()).thenReturn(size);
        when(mockFile.isEmpty()).thenReturn(isEmpty);
        return mockFile;
    }
}