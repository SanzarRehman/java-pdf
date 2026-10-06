package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.ReportTemplateRequest;
import com.bracits.easyJavaPdf.entity.ReportTemplate;
import com.bracits.easyJavaPdf.exception.ValidationException;
import com.bracits.easyJavaPdf.repository.ReportTemplateRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;

/**
 * Create / update / read of {@code pdf_report_templates} rows. Only exists with the database
 * enabled, like {@link ReportTemplateResolver}.
 *
 * <p>Timestamps are not set here: {@link ReportTemplate} fills {@code createdOn} on insert and
 * {@code updatedOn} on every update.
 */
@Service
@ConditionalOnProperty(name = "pdf.report.db.enabled", havingValue = "true")
public class ReportTemplateManagementService {

    private final ReportTemplateRepository repository;
    private final SpringTemplateEngine templateEngine;

    public ReportTemplateManagementService(ReportTemplateRepository repository,
                                           SpringTemplateEngine templateEngine) {
        this.repository = repository;
        this.templateEngine = templateEngine;
    }

    @Transactional(readOnly = true)
    public List<ReportTemplate> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public ReportTemplate get(Long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public ReportTemplate create(ReportTemplateRequest request) {
        if (!StringUtils.hasText(request.key())) {
            throw new ValidationException("'key' is required");
        }
        if (!StringUtils.hasText(request.body())) {
            throw new ValidationException("'body' is required");
        }
        String key = ReportTemplateService.normalizeReportName(request.key());
        if (repository.existsByKey(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Template with key '" + key + "' already exists");
        }

        ReportTemplate template = new ReportTemplate();
        template.setKey(key);
        template.setBody(request.body());
        template.setVariables(blankToNull(request.variables()));
        return repository.saveAndFlush(template);
    }

    @Transactional
    public ReportTemplate update(Long id, ReportTemplateRequest request) {
        ReportTemplate template = repository.findById(id).orElseThrow(() -> notFound(id));
        String oldKey = template.getKey();

        if (request.key() != null) {
            String key = ReportTemplateService.normalizeReportName(request.key());
            if (!key.equals(oldKey) && repository.existsByKey(key)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Template with key '" + key + "' already exists");
            }
            template.setKey(key);
        }
        if (request.body() != null) {
            if (!StringUtils.hasText(request.body())) {
                throw new ValidationException("'body' cannot be blank");
            }
            template.setBody(request.body());
        }
        if (request.variables() != null) {
            template.setVariables(blankToNull(request.variables()));
        }

        // saveAndFlush so @PreUpdate runs (and updatedOn is set) before we return the row.
        ReportTemplate saved = repository.saveAndFlush(template);
        // The resolver caches bodies; drop the stale entries so the edit applies immediately.
        templateEngine.clearTemplateCacheFor(ReportTemplateResolver.getPath(oldKey));
        templateEngine.clearTemplateCacheFor(ReportTemplateResolver.getPath(saved.getKey()));
        return saved;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Template " + id + " not found");
    }
}
