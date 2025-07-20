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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PdfMergeRequestTest {

    private Validator validator;
    private MultipartFile mockPdfFile1;
    private MultipartFile mockPdfFile2;
    private MultipartFile mockImageFile;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();


        mockPdfFile1 = new MockMultipartFile("pdf1", "document1.pdf", "application/pdf", "PDF content 1".getBytes());
        mockPdfFile2 = new MockMultipartFile("pdf2", "document2.pdf", "application/pdf", "PDF content 2".getBytes());
        mockImageFile = new MockMultipartFile("image", "image.jpg", "image/jpeg", "Image content".getBytes());
    }

    @Test
    void testValidPdfMergeRequest() {
        PdfMergeRequest request = new PdfMergeRequest();
        request.setFiles(Arrays.asList(mockPdfFile1, mockPdfFile2));
        request.setPagesDefinition("1:3,2:-1");
        request.setDisposition("attachment");
        request.setFileName("merged_document.pdf");
        request.setPassword("testPassword");
        request.setResourceOptimizer(true);

        Set<ConstraintViolation<PdfMergeRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should have no validation violations");
    }

    @Test
    void testPdfMergeRequestWithNullFiles() {
        PdfMergeRequest request = new PdfMergeRequest();
        request.setFiles(null);

        Set<ConstraintViolation<PdfMergeRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "Should have one validation violation for null files");
        
        ConstraintViolation<PdfMergeRequest> violation = violations.iterator().next();
        assertEquals("At least one file is required for merging", violation.getMessage());
        assertEquals("files", violation.getPropertyPath().toString());
    }

    @Test
    void testPdfMergeRequestWithEmptyFiles() {
        PdfMergeRequest request = new PdfMergeRequest();
        request.setFiles(Collections.emptyList());

        Set<ConstraintViolation<PdfMergeRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "Should have one validation violation for empty files list");
        
        ConstraintViolation<PdfMergeRequest> violation = violations.iterator().next();
        assertEquals("At least one file is required for merging", violation.getMessage());
        assertEquals("files", violation.getPropertyPath().toString());
    }

    @Test
    void testPdfMergeRequestWithInvalidDisposition() {
        PdfMergeRequest request = new PdfMergeRequest();
        request.setFiles(Arrays.asList(mockPdfFile1));
        request.setDisposition("invalid");

        Set<ConstraintViolation<PdfMergeRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "Should have one validation violation for invalid disposition");
        
        ConstraintViolation<PdfMergeRequest> violation = violations.iterator().next();
        assertEquals("Disposition must be either 'inline' or 'attachment'", violation.getMessage());
        assertEquals("disposition", violation.getPropertyPath().toString());
    }

    @Test
    void testPdfMergeRequestWithValidDispositions() {

        PdfMergeRequest request1 = new PdfMergeRequest();
        request1.setFiles(Arrays.asList(mockPdfFile1));
        request1.setDisposition("inline");

        Set<ConstraintViolation<PdfMergeRequest>> violations1 = validator.validate(request1);
        assertTrue(violations1.isEmpty(), "Request with 'inline' disposition should be valid");


        PdfMergeRequest request2 = new PdfMergeRequest();
        request2.setFiles(Arrays.asList(mockPdfFile1));
        request2.setDisposition("attachment");

        Set<ConstraintViolation<PdfMergeRequest>> violations2 = validator.validate(request2);
        assertTrue(violations2.isEmpty(), "Request with 'attachment' disposition should be valid");
    }

    @Test
    void testPdfMergeRequestWithMinimalValidData() {
        PdfMergeRequest request = new PdfMergeRequest();
        request.setFiles(Arrays.asList(mockPdfFile1));

        Set<ConstraintViolation<PdfMergeRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Request with only files should be valid");
    }

    @Test
    void testPdfMergeRequestGettersAndSetters() {
        PdfMergeRequest request = new PdfMergeRequest();
        List<MultipartFile> files = Arrays.asList(mockPdfFile1, mockImageFile);
        

        request.setFiles(files);
        assertEquals(files, request.getFiles());
        assertEquals(2, request.getFiles().size());
        
        request.setPagesDefinition("1:3,2:-1");
        assertEquals("1:3,2:-1", request.getPagesDefinition());
        
        request.setDisposition("attachment");
        assertEquals("attachment", request.getDisposition());
        
        request.setFileName("custom_merge.pdf");
        assertEquals("custom_merge.pdf", request.getFileName());
        
        request.setPassword("testPassword");
        assertEquals("testPassword", request.getPassword());
        
        request.setResourceOptimizer(true);
        assertTrue(request.isResourceOptimizer());
    }

    @Test
    void testPdfMergeRequestAllArgsConstructor() {
        List<MultipartFile> files = Arrays.asList(mockPdfFile1, mockPdfFile2);
        PdfMergeRequest request = new PdfMergeRequest(
            files,
            "1:3,2:-1",
            "attachment",
            "merged_document.pdf",
            "testPassword",
            true
        );

        assertEquals(files, request.getFiles());
        assertEquals("1:3,2:-1", request.getPagesDefinition());
        assertEquals("attachment", request.getDisposition());
        assertEquals("merged_document.pdf", request.getFileName());
        assertEquals("testPassword", request.getPassword());
        assertTrue(request.isResourceOptimizer());
    }

    @Test
    void testPdfMergeRequestNoArgsConstructor() {
        PdfMergeRequest request = new PdfMergeRequest();
        
        assertNull(request.getFiles());
        assertNull(request.getPagesDefinition());
        assertEquals("inline", request.getDisposition());
        assertEquals("merged.pdf", request.getFileName());
        assertNull(request.getPassword());
        assertFalse(request.isResourceOptimizer());
    }

    @Test
    void testPdfMergeRequestDefaultValues() {
        PdfMergeRequest request = new PdfMergeRequest();
        

        assertEquals("inline", request.getDisposition());
        assertEquals("merged.pdf", request.getFileName());
        assertFalse(request.isResourceOptimizer());
    }
}