package com.bracits.easyJavaPdf.controller;

import com.bracits.easyJavaPdf.dto.ReportTemplateRequest;
import com.bracits.easyJavaPdf.dto.ReportTemplateResponse;
import com.bracits.easyJavaPdf.service.ReportTemplateManagementService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manages report templates stored in the database. Only available when
 * {@code pdf.report.db.enabled=true} (the {@code db} profile).
 */
@RestController
@RequestMapping("/api/v1.0/templates")
@ConditionalOnProperty(name = "pdf.report.db.enabled", havingValue = "true")
public class ReportTemplateController {

    private final ReportTemplateManagementService service;

    public ReportTemplateController(ReportTemplateManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReportTemplateResponse> list() {
        return service.findAll().stream().map(ReportTemplateResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ReportTemplateResponse get(@PathVariable Long id) {
        return ReportTemplateResponse.from(service.get(id));
    }

    @PostMapping
    public ResponseEntity<ReportTemplateResponse> create(@RequestBody ReportTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportTemplateResponse.from(service.create(request)));
    }

    /** Partial update: only the fields present in the body are changed. */
    @PatchMapping("/{id}")
    public ReportTemplateResponse update(@PathVariable Long id, @RequestBody ReportTemplateRequest request) {
        return ReportTemplateResponse.from(service.update(id, request));
    }
}
