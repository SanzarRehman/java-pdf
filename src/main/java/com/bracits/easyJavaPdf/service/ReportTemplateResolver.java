package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.entity.ReportTemplate;
import com.bracits.easyJavaPdf.exception.ValidationException;
import com.bracits.easyJavaPdf.repository.ReportTemplateRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;

/**
 * Resolves Thymeleaf template names of the form {@code @report/<key>} against
 * {@link ReportTemplateRepository}, the same pattern notification-service's
 * {@code NotificationTemplateResolver} uses for {@code @html/}, {@code @text/} etc.
 *
 * <p>Only created when the {@code db} Spring profile is active (see application-db.properties);
 * with no database configured, this bean does not exist, and
 * {@link ReportTemplateService} falls back to the classpath for every report, unchanged.
 *
 * <p><b>Order matters.</b> {@link TemplateRenderingService} registers a catch-all
 * {@code StringTemplateResolver} with no name pattern and order {@code Integer.MAX_VALUE} — it
 * accepts any template name as literal HTML. Without an explicit lower order here, an unordered
 * resolver would sort behind it (Thymeleaf's {@code EngineConfiguration} places a {@code null}
 * order after any explicit order), so the inline resolver would claim {@code @report/<key>}
 * first and render that literal string as the "document" instead of the looked-up body.
 *
 * <p>Deliberately does not return {@code null} for a missing key (unlike
 * {@code NotificationTemplateResolver}): with no name pattern on the inline resolver, a
 * {@code null} here would fall through to it and silently render {@code @report/<key>} as
 * literal text rather than fail loudly.
 */
@Component
@ConditionalOnProperty(name = "pdf.report.db.enabled", havingValue = "true")
public class ReportTemplateResolver extends StringTemplateResolver {

    private static final Logger logger = LoggerFactory.getLogger(ReportTemplateResolver.class);
    private static final String PREFIX = "@report/";

    private final ReportTemplateRepository repository;

    public ReportTemplateResolver(ReportTemplateRepository repository,
                                  @Value("${pdf.report.db.cache-ttl:PT5M}") Duration cacheTtl) {
        this.repository = repository;
        setName("dbReportTemplateResolver");
        setResolvablePatterns(Set.of(PREFIX + "*"));
        // Required, not cosmetic: must sort before the inline catch-all (order = MAX_VALUE).
        setOrder(1);
        setCacheable(true);
        setCacheTTLMs(cacheTtl.toMillis());
        setCheckExistence(false);
    }

    public static String getPath(String key) {
        return PREFIX + key;
    }

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration,
                                                        String ownerTemplate,
                                                        String templateName,
                                                        Map<String, Object> attributes) {
        String key = templateName.substring(PREFIX.length());
        logger.debug("Loading report template with key {} from database", key);

        ReportTemplate template = repository.findByKey(key)
                .orElseThrow(() -> new ValidationException(
                        "Report template '" + key + "' not found in database"));

        return super.computeTemplateResource(configuration, ownerTemplate, template.getBody(), attributes);
    }
}
