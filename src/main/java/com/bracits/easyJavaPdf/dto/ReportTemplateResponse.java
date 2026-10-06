package com.bracits.easyJavaPdf.dto;

import com.bracits.easyJavaPdf.entity.ReportTemplate;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ReportTemplateResponse(
        Long id,
        String key,
        String body,
        String variables,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS") LocalDateTime createdOn,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS") LocalDateTime updatedOn) {

    public static ReportTemplateResponse from(ReportTemplate t) {
        return new ReportTemplateResponse(t.getId(), t.getKey(), t.getBody(), t.getVariables(),
                t.getCreatedOn(), t.getUpdatedOn());
    }
}
