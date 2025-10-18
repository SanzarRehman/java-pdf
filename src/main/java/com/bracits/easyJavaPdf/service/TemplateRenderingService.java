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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders Thymeleaf templates using data supplied with {@link PdfGenerationRequest}.
 */
@Service
public class TemplateRenderingService {

    private static final String PAGE_BREAK_FRAGMENT = "<div style=\"page-break-after: always;\"></div>";

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

        if (request.isThymeleafTemplate() || hasDataModel(request) || StringUtils.hasText(request.getData())) {
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
        if (request == null) {
            return Collections.emptyMap();
        }

        if (StringUtils.hasText(request.getData())) {
            return parseJsonMap(request.getData(), "Invalid data JSON");
        }

        if (!hasDataModel(request)) {
            return Collections.emptyMap();
        }

        try {
            String json = extractModelJson(request);
            if (!StringUtils.hasText(json)) {
                return Collections.emptyMap();
            }

            return parseJsonMap(json, "Invalid data model JSON");
        } catch (IOException e) {
            throw new ValidationException("Invalid data model JSON", e);
        }
    }

    public List<Map<String, Object>> resolveModelCollection(PdfGenerationRequest request) {
        if (request == null) {
            return Collections.singletonList(Collections.emptyMap());
        }

        if (StringUtils.hasText(request.getDataSet())) {
            return parseJsonList(request.getDataSet(), "Invalid data_set JSON");
        }

        Map<String, Object> single = resolveModel(request);
        if (single == null || single.isEmpty()) {
            return Collections.singletonList(Collections.emptyMap());
        }

        return List.of(single);
    }

    /**
     * Renders the provided template content using Thymeleaf.
     */
    public String renderTemplate(String templateContent, Map<String, Object> model) {
        try {
            return templateEngine.process(templateContent, buildContext(model));
        } catch (Exception e) {
            throw new PdfGenerationException("Failed to render Thymeleaf template", e);
        }
    }

    public String renderTemplate(String templateContent, List<Map<String, Object>> models) {
        if (models == null || models.isEmpty()) {
            return renderTemplate(templateContent, Collections.emptyMap());
        }

        if (models.size() == 1) {
            return renderTemplate(templateContent, models.get(0));
        }

        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) {
                combined.append(PAGE_BREAK_FRAGMENT);
            }
            combined.append(renderTemplate(templateContent, models.get(i)));
        }
        return combined.toString();
    }

    public String renderNamedTemplate(String templateName, Map<String, Object> model) {
        try {
            return templateEngine.process(templateName, buildContext(model));
        } catch (Exception e) {
            throw new PdfGenerationException(
                    "Failed to render Thymeleaf template '" + templateName + "'", e);
        }
    }

    public String renderNamedTemplate(String templateName, List<Map<String, Object>> models) {
        if (models == null || models.isEmpty()) {
            return renderNamedTemplate(templateName, Collections.emptyMap());
        }

        if (models.size() == 1) {
            return renderNamedTemplate(templateName, models.get(0));
        }

        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) {
                combined.append(PAGE_BREAK_FRAGMENT);
            }
            combined.append(renderNamedTemplate(templateName, models.get(i)));
        }
        return combined.toString();
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

    private Map<String, Object> parseJsonMap(String json, String errorMessage) {
        try {
            Map<String, Object> result = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return result != null ? result : Collections.emptyMap();
        } catch (IOException e) {
            throw new ValidationException(errorMessage, e);
        }
    }

    private List<Map<String, Object>> parseJsonList(String json, String errorMessage) {
        try {
            List<Map<String, Object>> result = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            if (result == null || result.isEmpty()) {
                return Collections.singletonList(Collections.emptyMap());
            }
            return result;
        } catch (IOException e) {
            throw new ValidationException(errorMessage, e);
        }
    }

    private Context buildContext(Map<String, Object> model) {
        Context context = new Context(Locale.getDefault());
        if (model != null && !model.isEmpty()) {
            context.setVariables(model);
        }
        return context;
    }

    private void registerInlineTemplateResolverIfNecessary(SpringTemplateEngine engine) {
        boolean hasResolver = engine.getTemplateResolvers().stream()
            .anyMatch(resolver -> resolver instanceof StringTemplateResolver);

        if (!hasResolver) {
            StringTemplateResolver resolver = new StringTemplateResolver();
            resolver.setName("inlineTemplateResolver");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCacheable(false);
            resolver.setOrder(Integer.MAX_VALUE);
            resolver.setCheckExistence(false);
            resolver.setResolvablePatterns(Set.of("*<*"));
            engine.addTemplateResolver(resolver);
        }
    }
}
