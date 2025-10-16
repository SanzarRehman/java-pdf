package com.bracits.easyJavaPdf.util;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
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
class PdfGenerationRequestValidatorTest {

    @Mock
    private MultipartFile mockHtmlFile;
    
    @Mock
    private MultipartFile mockCssFile;
    
    @Mock
    private MultipartFile mockHeaderFile;
    
    @Mock
    private MultipartFile mockFooterFile;
    
    @Mock
    private MultipartFile mockBanglaFooter;
    
    @Mock
    private MultipartFile mockAssetFile;

    @Test
    void validate_WithNullRequest_ShouldThrowValidationException() {
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(null));
        
        assertEquals("PDF generation request cannot be null", exception.getMessage());
    }

    @Test
    void validate_WithValidMinimalRequest_ShouldNotThrowException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        
        assertDoesNotThrow(() -> PdfGenerationRequestValidator.validate(request));
    }

    @Test
    void validate_WithValidCompleteRequest_ShouldNotThrowException() {
        PdfGenerationRequest request = createValidCompleteRequest();
        
        assertDoesNotThrow(() -> PdfGenerationRequestValidator.validate(request));
    }

    @Test
    void validate_WithNullHtmlFile_ShouldThrowValidationException() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtml(null);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertEquals("HTML file cannot be null", exception.getMessage());
    }

    @Test
    void validate_WithEmptyHtmlFile_ShouldThrowValidationException() {
        when(mockHtmlFile.isEmpty()).thenReturn(true);
        
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtml(mockHtmlFile);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertEquals("HTML file cannot be empty", exception.getMessage());
    }

    @Test
    void validate_WithInvalidHtmlFileType_ShouldThrowValidationException() {
        when(mockHtmlFile.isEmpty()).thenReturn(false);
        when(mockHtmlFile.getSize()).thenReturn(1024L);
        when(mockHtmlFile.getContentType()).thenReturn("application/pdf");
        when(mockHtmlFile.getOriginalFilename()).thenReturn("test.pdf");
        
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtml(mockHtmlFile);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertEquals("HTML file must be an HTML file (.html, .htm)", exception.getMessage());
    }

    @Test
    void validate_WithLongHtmlFilename_ShouldThrowValidationException() {
        String longFilename = "a".repeat(256) + ".html";
        when(mockHtmlFile.isEmpty()).thenReturn(false);
        when(mockHtmlFile.getSize()).thenReturn(1024L);
        when(mockHtmlFile.getContentType()).thenReturn("text/html");
        when(mockHtmlFile.getOriginalFilename()).thenReturn(longFilename);
        
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtml(mockHtmlFile);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertEquals("HTML filename is too long (maximum 255 characters)", exception.getMessage());
    }

    @Test
    void validate_WithInvalidCssFile_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        
        when(mockCssFile.isEmpty()).thenReturn(false);
        when(mockCssFile.getSize()).thenReturn(1024L);
        when(mockCssFile.getContentType()).thenReturn("application/pdf");
        when(mockCssFile.getOriginalFilename()).thenReturn("test.pdf");
        
        request.setStyle(mockCssFile);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertEquals("CSS file must be a CSS file (.css)", exception.getMessage());
    }

    @Test
    void validate_WithLargeCssFilename_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        
        String longFilename = "a".repeat(256) + ".css";
        when(mockCssFile.isEmpty()).thenReturn(false);
        when(mockCssFile.getSize()).thenReturn(1024L);
        when(mockCssFile.getContentType()).thenReturn("text/css");
        when(mockCssFile.getOriginalFilename()).thenReturn(longFilename);
        
        request.setStyle(mockCssFile);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertEquals("CSS filename is too long (maximum 255 characters)", exception.getMessage());
    }

    @Test
    void validate_WithLargeHeaderFile_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        
        long largeSize = 2 * 1024 * 1024;
        when(mockHeaderFile.isEmpty()).thenReturn(false);
        when(mockHeaderFile.getSize()).thenReturn(largeSize);
        when(mockHeaderFile.getContentType()).thenReturn("text/html");
        when(mockHeaderFile.getOriginalFilename()).thenReturn("header.html");
        
        request.setHeaderFile(mockHeaderFile);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertEquals("Header file size should not exceed 1MB", exception.getMessage());
    }

    @Test
    void validate_WithLargeFooterFile_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        
        long largeSize = 2 * 1024 * 1024;
        when(mockFooterFile.isEmpty()).thenReturn(false);
        when(mockFooterFile.getSize()).thenReturn(largeSize);
        when(mockFooterFile.getContentType()).thenReturn("text/html");
        when(mockFooterFile.getOriginalFilename()).thenReturn("footer.html");
        
        request.setFooterFile(mockFooterFile);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertEquals("Footer file size should not exceed 1MB", exception.getMessage());
    }

    @Test
    void validate_WithLargeBanglaFooterFile_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        
        long largeSize = 2 * 1024 * 1024;
        when(mockBanglaFooter.isEmpty()).thenReturn(false);
        when(mockBanglaFooter.getSize()).thenReturn(largeSize);
        when(mockBanglaFooter.getContentType()).thenReturn("text/html");
        when(mockBanglaFooter.getOriginalFilename()).thenReturn("bangla_footer.html");
        
        request.setBanglaFooter(mockBanglaFooter);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertEquals("Bengali footer file size should not exceed 1MB", exception.getMessage());
    }

    @Test
    void validate_WithTooManyAssetFiles_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        


        List<MultipartFile> assets = IntStream.range(0, 51)
            .mapToObj(i -> {
                MultipartFile mockFile = org.mockito.Mockito.mock(MultipartFile.class);

                return mockFile;
            })
            .toList();
        
        request.setAsset(assets);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Too many asset files"));
        assertTrue(exception.getMessage().contains("Maximum allowed: 50"));
    }

    @Test
    void validate_WithDuplicateAssetFilenames_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        
        MultipartFile asset1 = createMockAssetFile("duplicate.png", "image/png", 1024L);
        MultipartFile asset2 = createMockAssetFile("duplicate.png", "image/png", 1024L);
        
        request.setAsset(Arrays.asList(asset1, asset2));
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Duplicate asset filename found"));
        assertTrue(exception.getMessage().contains("duplicate.png"));
    }

    @Test
    void validate_WithShortPassword_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        request.setPassword("123");
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Password is too short"));
        assertTrue(exception.getMessage().contains("Minimum length: 4"));
    }

    @Test
    void validate_WithLongPassword_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        request.setPassword("a".repeat(129));
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Password is too long"));
        assertTrue(exception.getMessage().contains("Maximum length: 128"));
    }

    @Test
    void validate_WithInvalidPasswordCharacters_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        request.setPassword("password\u007F\u0080");
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Password contains invalid characters"));
    }

    @Test
    void validate_WithValidPassword_ShouldNotThrowException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        request.setPassword("ValidPassword123!@#");
        
        assertDoesNotThrow(() -> PdfGenerationRequestValidator.validate(request));
    }

    @Test
    void validate_WithBothFooterAndBanglaFooter_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        

        MultipartFile footerFile = createMockHtmlFile("footer.html", 512L);
        MultipartFile banglaFooter = createMockHtmlFile("bangla_footer.html", 512L);
        
        request.setFooterFile(footerFile);
        request.setBanglaFooter(banglaFooter);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Cannot specify both regular footer and Bengali footer"));
    }

    @Test
    void validate_WithJsEnabledAndTooManyAssets_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        request.setJsEnabled(true);
        

        List<MultipartFile> assets = IntStream.range(0, 21)
            .mapToObj(i -> createMockAssetFile("asset" + i + ".png", "image/png", 1024L))
            .toList();
        
        request.setAsset(assets);
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("When JavaScript is enabled, maximum 20 asset files are allowed"));
    }

    @Test
    void validate_WithTotalAssetSizeExceedsLimit_ShouldThrowValidationException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        

        long largeAssetSize = 18 * 1024 * 1024;
        MultipartFile asset1 = createMockAssetFile("asset1.png", "image/png", largeAssetSize);
        MultipartFile asset2 = createMockAssetFile("asset2.png", "image/png", largeAssetSize);
        MultipartFile asset3 = createMockAssetFile("asset3.png", "image/png", largeAssetSize);
        
        request.setAsset(Arrays.asList(asset1, asset2, asset3));
        
        ValidationException exception = assertThrows(ValidationException.class,
            () -> PdfGenerationRequestValidator.validate(request));
        
        assertTrue(exception.getMessage().contains("Total size of all asset files exceeds maximum allowed size"));
    }

    @Test
    void validate_WithEmptyPassword_ShouldNotThrowException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        request.setPassword("   ");
        
        assertDoesNotThrow(() -> PdfGenerationRequestValidator.validate(request));
    }

    @Test
    void validate_WithNullPassword_ShouldNotThrowException() {
        PdfGenerationRequest request = createValidMinimalRequest();
        request.setPassword(null);
        
        assertDoesNotThrow(() -> PdfGenerationRequestValidator.validate(request));
    }

    private PdfGenerationRequest createValidMinimalRequest() {
        when(mockHtmlFile.isEmpty()).thenReturn(false);
        when(mockHtmlFile.getSize()).thenReturn(1024L);
        when(mockHtmlFile.getContentType()).thenReturn("text/html");
        when(mockHtmlFile.getOriginalFilename()).thenReturn("test.html");
        
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtml(mockHtmlFile);
        return request;
    }

    private PdfGenerationRequest createValidCompleteRequest() {
        PdfGenerationRequest request = createValidMinimalRequest();
        

        when(mockCssFile.isEmpty()).thenReturn(false);
        when(mockCssFile.getSize()).thenReturn(512L);
        when(mockCssFile.getContentType()).thenReturn("text/css");
        when(mockCssFile.getOriginalFilename()).thenReturn("styles.css");
        request.setStyle(mockCssFile);
        

        when(mockHeaderFile.isEmpty()).thenReturn(false);
        when(mockHeaderFile.getSize()).thenReturn(256L);
        when(mockHeaderFile.getContentType()).thenReturn("text/html");
        when(mockHeaderFile.getOriginalFilename()).thenReturn("header.html");
        request.setHeaderFile(mockHeaderFile);
        

        MultipartFile asset = createMockAssetFile("font.ttf", "font/ttf", 1024L);
        request.setAsset(Collections.singletonList(asset));
        

        request.setPassword("validPassword123");
        

        request.setJsEnabled(true);
        
        return request;
    }

    private MultipartFile createMockAssetFile(String filename, String contentType, long size) {
        MultipartFile mockFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn(contentType);
        when(mockFile.getSize()).thenReturn(size);
        return mockFile;
    }

    private MultipartFile createMockHtmlFile(String filename, long size) {
        MultipartFile mockFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn("text/html");
        when(mockFile.getSize()).thenReturn(size);
        return mockFile;
    }
}