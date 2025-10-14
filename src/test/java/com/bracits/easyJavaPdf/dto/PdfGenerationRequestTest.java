package com.bracits.easyJavaPdf.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PdfGenerationRequestTest {

    private Validator validator;
    private MultipartFile mockHtmlFile;
    private MultipartFile mockCssFile;
    private MultipartFile mockHeaderFile;
    private MultipartFile mockFooterFile;
    private MultipartFile mockBanglaFooter;
    private MultipartFile mockAssetFile;
    private MultipartFile mockDataModelFile;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();


        mockHtmlFile = new MockMultipartFile("html", "test.html", "text/html", "<html><body>Test</body></html>".getBytes());
        mockCssFile = new MockMultipartFile("css", "style.css", "text/css", "body { margin: 0; }".getBytes());
        mockHeaderFile = new MockMultipartFile("header", "header.html", "text/html", "<div>Header</div>".getBytes());
        mockFooterFile = new MockMultipartFile("footer", "footer.html", "text/html", "<div>Footer</div>".getBytes());
        mockBanglaFooter = new MockMultipartFile("bangla", "bangla.html", "text/html", "<div>বাংলা</div>".getBytes());
        mockAssetFile = new MockMultipartFile("asset", "font.ttf", "font/ttf", "font data".getBytes());
    mockDataModelFile = new MockMultipartFile("data", "data.json", "application/json", "{\"name\":\"Test\"}".getBytes());
    }

    @Test
    void testValidPdfGenerationRequest() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtmlFile(mockHtmlFile);
        request.setCssFile(mockCssFile);
        request.setHeaderFile(mockHeaderFile);
        request.setFooterFile(mockFooterFile);
        request.setBanglaFooter(mockBanglaFooter);
        request.setAssets(Arrays.asList(mockAssetFile));
        request.setPassword("testPassword");
        request.setJsEnabled(true);

        Set<ConstraintViolation<PdfGenerationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should have no validation violations");
    }

    @Test
    void testPdfGenerationRequestWithNullHtmlFile() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtmlFile(null);
        request.setCssFile(mockCssFile);

        Set<ConstraintViolation<PdfGenerationRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "Should have one validation violation for null HTML file");
        
        ConstraintViolation<PdfGenerationRequest> violation = violations.iterator().next();
        assertEquals("HTML file is required", violation.getMessage());
        assertEquals("htmlFile", violation.getPropertyPath().toString());
    }

    @Test
    void testPdfGenerationRequestWithMinimalValidData() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtmlFile(mockHtmlFile);

        Set<ConstraintViolation<PdfGenerationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Request with only HTML file should be valid");
    }

    @Test
    void testPdfGenerationRequestGettersAndSetters() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        

        request.setHtmlFile(mockHtmlFile);
        assertEquals(mockHtmlFile, request.getHtmlFile());
        
        request.setCssFile(mockCssFile);
        assertEquals(mockCssFile, request.getCssFile());
        
        request.setHeaderFile(mockHeaderFile);
        assertEquals(mockHeaderFile, request.getHeaderFile());
        
        request.setFooterFile(mockFooterFile);
        assertEquals(mockFooterFile, request.getFooterFile());
        
        request.setBanglaFooter(mockBanglaFooter);
        assertEquals(mockBanglaFooter, request.getBanglaFooter());
        
        request.setAssets(Arrays.asList(mockAssetFile));
        assertEquals(1, request.getAssets().size());
        assertEquals(mockAssetFile, request.getAssets().get(0));
        
        request.setPassword("testPassword");
        assertEquals("testPassword", request.getPassword());
        
        request.setJsEnabled(true);
        assertTrue(request.isJsEnabled());

        request.setThymeleafTemplate(true);
        assertTrue(request.isThymeleafTemplate());

        request.setDataModel("{\"foo\":\"bar\"}");
        assertEquals("{\"foo\":\"bar\"}", request.getDataModel());

        request.setDataModelFile(mockDataModelFile);
        assertEquals(mockDataModelFile, request.getDataModelFile());
    }

    @Test
    void testPdfGenerationRequestAllArgsConstructor() {
        PdfGenerationRequest request = new PdfGenerationRequest(
            mockHtmlFile,
            mockCssFile,
            "body { margin: 0; }",
            "<html><body>Content</body></html>",
            mockHeaderFile,
            mockFooterFile,
            mockBanglaFooter,
            Arrays.asList(mockAssetFile),
            "testPassword",
            true,
            true,
            "{\"foo\":\"bar\"}",
            mockDataModelFile
        );

        assertEquals(mockHtmlFile, request.getHtmlFile());
        assertEquals(mockCssFile, request.getCssFile());
        assertEquals("body { margin: 0; }", request.getCssContent());
        assertEquals("<html><body>Content</body></html>", request.getHtmlContent());
        assertEquals(mockHeaderFile, request.getHeaderFile());
        assertEquals(mockFooterFile, request.getFooterFile());
        assertEquals(mockBanglaFooter, request.getBanglaFooter());
        assertEquals(1, request.getAssets().size());
        assertEquals("testPassword", request.getPassword());
        assertTrue(request.isJsEnabled());
        assertTrue(request.isThymeleafTemplate());
        assertEquals("{\"foo\":\"bar\"}", request.getDataModel());
        assertEquals(mockDataModelFile, request.getDataModelFile());
    }

    @Test
    void testPdfGenerationRequestNoArgsConstructor() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        
        assertNull(request.getHtmlFile());
        assertNull(request.getCssFile());
        assertNull(request.getHeaderFile());
        assertNull(request.getFooterFile());
        assertNull(request.getBanglaFooter());
        assertNull(request.getAssets());
        assertNull(request.getPassword());
        assertFalse(request.isJsEnabled());
        assertFalse(request.isThymeleafTemplate());
        assertNull(request.getDataModel());
        assertNull(request.getDataModelFile());
    }
}