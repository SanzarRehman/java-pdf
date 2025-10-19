package com.bracits.easyJavaPdf.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Value object describing a resolved report template and its copied assets.
 */
public record ReportTemplateDescriptor(
        String templateName,
        Path htmlFile,
        List<Path> copiedResources,
        List<Path> fontFiles) {

    public Path templateRoot() {
        return htmlFile != null ? htmlFile.getParent() : null;
    }
}
