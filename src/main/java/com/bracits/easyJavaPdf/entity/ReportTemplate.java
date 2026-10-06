package com.bracits.easyJavaPdf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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
 *
 * <p>{@code variables} is documentation only, exactly like notification-service's
 * {@code NotificationTemplate.variables} — a JSON object of {@code {"<thymeleaf variable
 * path>": "<human-readable label>"}} describing every value the template's {@code data}/
 * {@code data_set} payload needs to supply, so a user filling it in doesn't have to read the
 * raw Thymeleaf markup to find out. Never read by {@link com.bracits.easyJavaPdf.service.ReportTemplateResolver}
 * or by rendering — Thymeleaf only ever sees {@code body}. Nullable: a template with no
 * documented variables simply has no row value here.
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

    @Column(name = "variables", columnDefinition = "text")
    private String variables;

    /** Set once on insert, e.g. {@code 2023-09-24 12:15:34.163000}. */
    @Column(name = "created_on", nullable = false, updatable = false, columnDefinition = "timestamp(6)")
    private LocalDateTime createdOn;

    /** Refreshed on every update; equals {@code createdOn} until the row is first modified. */
    @Column(name = "updated_on", nullable = false, columnDefinition = "timestamp(6)")
    private LocalDateTime updatedOn;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdOn = now;
        updatedOn = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedOn = LocalDateTime.now();
    }
}
