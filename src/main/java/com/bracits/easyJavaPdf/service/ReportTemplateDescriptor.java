package com.bracits.easyJavaPdf.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Value object describing a resolved report template and its copied assets.
 *
 * @param dbTemplateName the Thymeleaf template name to pass to
 *                       {@link TemplateRenderingService#renderNamedTemplate}
 *                       (e.g. {@code "@report/student-grade"}) when this report's HTML came
 *                       from the database; {@code null} when it came from the classpath, in
 *                       which case {@code htmlFile} should be read and rendered inline instead.
 */
public record ReportTemplateDescriptor(
        String templateName,
        Path htmlFile,
        List<Path> copiedResources,
        List<Path> fontFiles,
        String dbTemplateName) {

    /**
     * Classpath-sourced descriptor (existing behavior) — {@code dbTemplateName} is {@code null}.
     */
    public ReportTemplateDescriptor(String templateName, Path htmlFile, List<Path> copiedResources,
                                    List<Path> fontFiles) {
        this(templateName, htmlFile, copiedResources, fontFiles, null);
    }

    public Path templateRoot() {
        return htmlFile != null ? htmlFile.getParent() : null;
    }
}
