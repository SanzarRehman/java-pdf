package com.bracits.easyJavaPdf.repository;

import com.bracits.easyJavaPdf.entity.ReportTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    Optional<ReportTemplate> findByKey(String key);

    boolean existsByKey(String key);
}
