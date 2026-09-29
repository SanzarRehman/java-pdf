package com.bracits.easyJavaPdf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A report's HTML body, loaded from {@code pdf_report_templates} instead of the classpath.
 *
 * <p>Deliberately minimal: unlike notification-service's {@code NotificationTemplate}, this
 * entity carries no module/feature/subject/layout/audit metadata. CSS, SVG, fonts and other
 * assets are never stored here — they stay file-based under
 * {@code classpath:/templates/<template>/}, copied flat next to the rendered HTML at request
 * time by {@link com.bracits.easyJavaPdf.service.ReportTemplateService}, unchanged.
 *
 * <p>{@code key} is the normalized {@code report} form field the client sends (leading "/" and
 * trailing ".html" stripped) — see {@link com.bracits.easyJavaPdf.service.ReportTemplateResolver}.
 * A report with no row here simply falls back to the classpath, so this table only needs a row
 * for reports actually migrated to the database.
 */
@Getter
@Setter
@Entity
@Table(name = "pdf_report_templates")
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key", unique = true, nullable = false)
    private String key;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;
}
