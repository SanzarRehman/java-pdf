package com.bracits.easyJavaPdf.dto;

import com.bracits.easyJavaPdf.util.PdfGenerationRequestValidator;
import com.bracits.easyJavaPdf.exception.ValidationException;
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
        request.setHtml(mockHtmlFile);
        request.setStyle(mockCssFile);
        request.setHeaderFile(mockHeaderFile);
        request.setFooterFile(mockFooterFile);
        request.setBanglaFooter(mockBanglaFooter);
        request.setAsset(Arrays.asList(mockAssetFile));
        request.setPassword("testPassword");
        request.setJsEnable(true);
        request.setForceBrowserMode(true);

        Set<ConstraintViolation<PdfGenerationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should have no validation violations");
    }

    @Test
    void testPdfGenerationRequestWithNullHtmlFile() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtml(null);
        request.setStyle(mockCssFile);

    Set<ConstraintViolation<PdfGenerationRequest>> violations = validator.validate(request);
    assertTrue(violations.isEmpty(), "Bean validation allows missing htmlFile when htmlContent may be supplied");

    assertThrows(ValidationException.class, () -> PdfGenerationRequestValidator.validate(request));
    }

    @Test
    void testPdfGenerationRequestWithMinimalValidData() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setHtml(mockHtmlFile);

        Set<ConstraintViolation<PdfGenerationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Request with only HTML file should be valid");
    }

    @Test
    void testPdfGenerationRequestGettersAndSetters() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        

        request.setHtml(mockHtmlFile);
        assertEquals(mockHtmlFile, request.getHtml());
        
        request.setStyle(mockCssFile);
        assertEquals(mockCssFile, request.getStyle());
        
        request.setHeaderFile(mockHeaderFile);
        assertEquals(mockHeaderFile, request.getHeaderFile());
        
        request.setFooterFile(mockFooterFile);
        assertEquals(mockFooterFile, request.getFooterFile());
        
        request.setBanglaFooter(mockBanglaFooter);
        assertEquals(mockBanglaFooter, request.getBanglaFooter());
        
        request.setAsset(Arrays.asList(mockAssetFile));
        assertEquals(1, request.getAsset().size());
        assertEquals(mockAssetFile, request.getAsset().get(0));
        
        request.setPassword("testPassword");
        assertEquals("testPassword", request.getPassword());
        
        request.setJsEnable(true);
        assertTrue(request.isJsEnable());

        request.setForceBrowserMode(true);
        assertTrue(request.isForceBrowserMode());

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
            true,
            "{\"foo\":\"bar\"}",
            mockDataModelFile,
            "LANDSCAPE"
        );

        assertEquals(mockHtmlFile, request.getHtml());
        assertEquals(mockCssFile, request.getStyle());
        assertEquals("body { margin: 0; }", request.getCssContent());
        assertEquals("<html><body>Content</body></html>", request.getHtmlContent());
        assertEquals(mockHeaderFile, request.getHeaderFile());
        assertEquals(mockFooterFile, request.getFooterFile());
        assertEquals(mockBanglaFooter, request.getBanglaFooter());
        assertEquals(1, request.getAsset().size());
        assertEquals("testPassword", request.getPassword());
        assertTrue(request.isJsEnable());
        assertTrue(request.isForceBrowserMode());
        assertTrue(request.isThymeleafTemplate());
        assertEquals("{\"foo\":\"bar\"}", request.getDataModel());
        assertEquals(mockDataModelFile, request.getDataModelFile());
        assertEquals("LANDSCAPE", request.getPageOrientation());
    }

    @Test
    void testPdfGenerationRequestNoArgsConstructor() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        
        assertNull(request.getHtml());
        assertNull(request.getStyle());
        assertNull(request.getHeaderFile());
        assertNull(request.getFooterFile());
        assertNull(request.getBanglaFooter());
        assertNull(request.getAsset());
        assertNull(request.getPassword());
        assertFalse(request.isJsEnable());
        assertFalse(request.isForceBrowserMode());
        assertFalse(request.isThymeleafTemplate());
        assertNull(request.getDataModel());
        assertNull(request.getDataModelFile());
    }
}