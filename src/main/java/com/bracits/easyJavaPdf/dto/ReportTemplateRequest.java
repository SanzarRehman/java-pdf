package com.bracits.easyJavaPdf.dto;

/**
 * Body for creating or updating a report template. On create, {@code key} and {@code body}
 * are required. On update, every field is optional and only the ones sent are changed;
 * an empty {@code variables} clears it.
 */
public record ReportTemplateRequest(String key, String body, String variables) {
}
