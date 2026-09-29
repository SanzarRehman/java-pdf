package com.bracits.easyJavaPdf.repository;

import com.bracits.easyJavaPdf.entity.ReportTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Only created as a bean when the "db" profile is active (no DataSource, no repository —
 * see application.properties' auto-config exclude and application-db.properties).
 */
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    Optional<ReportTemplate> findByKey(String key);

    boolean existsByKey(String key);
}
