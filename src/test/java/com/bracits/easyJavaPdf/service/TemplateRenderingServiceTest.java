package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TemplateRenderingServiceTest {

    private TemplateRenderingService templateRenderingService;

    @BeforeEach
    void setUp() {
        templateRenderingService = new TemplateRenderingService(new SpringTemplateEngine(), new ObjectMapper());
    }

    @Test
    void resolveModelCollectionParsesDataSet() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setReport("sample-report");
        request.setThymeleafTemplate(true);
        request.setDataSet("[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]");

        List<Map<String, Object>> models = templateRenderingService.resolveModelCollection(request);

        assertNotNull(models);
        assertEquals(2, models.size());
        assertEquals("Alice", models.get(0).get("name"));
        assertEquals("Bob", models.get(1).get("name"));
    }

    @Test
    void resolveModelParsesDataPayload() {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setThymeleafTemplate(true);
        request.setData("{\"greeting\":\"hello\"}");

        Map<String, Object> model = templateRenderingService.resolveModel(request);

        assertNotNull(model);
        assertEquals("hello", model.get("greeting"));
    }
}
