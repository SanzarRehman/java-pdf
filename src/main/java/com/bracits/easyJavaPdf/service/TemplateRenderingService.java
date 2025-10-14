package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.exception.ValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Renders Thymeleaf templates using data supplied with {@link PdfGenerationRequest}.
 */
@Service
public class TemplateRenderingService {

    private final SpringTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    public TemplateRenderingService(SpringTemplateEngine templateEngine, ObjectMapper objectMapper) {
        this.templateEngine = templateEngine;
        this.objectMapper = objectMapper;
        registerInlineTemplateResolverIfNecessary(templateEngine);
    }

    /**
     * Determines whether the incoming request should be treated as a Thymeleaf template.
     */
    public boolean shouldRenderTemplate(PdfGenerationRequest request, String htmlContent) {
        if (request == null) {
            return false;
        }

        if (request.isThymeleafTemplate() || hasDataModel(request)) {
            return true;
        }

        if (!StringUtils.hasText(htmlContent)) {
            return false;
        }

        return htmlContent.contains("th:") || htmlContent.contains("${") || htmlContent.contains("[[");
    }

    /**
     * Resolves the data model from the request.
     */
    public Map<String, Object> resolveModel(PdfGenerationRequest request) {
        if (request == null || !hasDataModel(request)) {
            return Collections.emptyMap();
        }

        try {
            String json = extractModelJson(request);
            if (!StringUtils.hasText(json)) {
                return Collections.emptyMap();
            }

            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new ValidationException("Invalid data model JSON", e);
        }
    }

    /**
     * Renders the provided template content using Thymeleaf.
     */
    public String renderTemplate(String templateContent, Map<String, Object> model) {
        try {
            Context context = new Context(Locale.getDefault());
            if (model != null && !model.isEmpty()) {
                context.setVariables(model);
            }
            return templateEngine.process(templateContent, context);
        } catch (Exception e) {
            throw new PdfGenerationException("Failed to render Thymeleaf template", e);
        }
    }

    private boolean hasDataModel(PdfGenerationRequest request) {
        return (request.getDataModelFile() != null && !request.getDataModelFile().isEmpty())
                || StringUtils.hasText(request.getDataModel());
    }

    private String extractModelJson(PdfGenerationRequest request) throws IOException {
        if (StringUtils.hasText(request.getDataModel())) {
            return request.getDataModel();
        }

        MultipartFile dataModelFile = request.getDataModelFile();
        if (dataModelFile != null && !dataModelFile.isEmpty()) {
            return new String(dataModelFile.getBytes(), StandardCharsets.UTF_8);
        }

        return null;
    }

    private void registerInlineTemplateResolverIfNecessary(SpringTemplateEngine engine) {
        boolean hasResolver = engine.getTemplateResolvers().stream()
            .anyMatch(resolver -> resolver instanceof StringTemplateResolver);

        if (!hasResolver) {
            StringTemplateResolver resolver = new StringTemplateResolver();
            resolver.setName("inlineTemplateResolver");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCacheable(false);
            resolver.setOrder(1);
            resolver.setCheckExistence(false);
            engine.addTemplateResolver(resolver);
        }
    }
}
