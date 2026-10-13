package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.ValidationException;
import com.bracits.easyJavaPdf.repository.ReportTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Loads predefined report templates and their associated assets from the classpath.
 *
 * <p>A report whose key has a row in the database is rendered from it instead. The database is
 * optional: while it is unreachable every report resolves from the classpath, and it is not asked
 * again until {@code pdf.report.db.retry-after} has passed, so an outage costs one failed
 * connection attempt per window rather than one per request.
 */
@Service
public class ReportTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(ReportTemplateService.class);
    private static final String REPORT_PREFIX = "classpath:/reports/";
    private static final String TEMPLATE_PREFIX = "classpath:/templates/";

    private final ResourceLoader resourceLoader;
    private final ResourcePatternResolver resourcePatternResolver;
    private final ReportTemplateRepository reportTemplateRepository;
    private final Duration retryAfter;

    // Outage state, shared by all request threads. skipDatabaseUntil is a System.nanoTime()
    // deadline and is written before databaseDown, so a reader that sees databaseDown also sees it.
    // probing makes the retry single-flight: a failed connection costs ~1s (Hikari pauses before
    // giving up), and Hikari serializes pool start-up, so concurrent retries would queue behind
    // each other instead of failing in parallel.
    private volatile long skipDatabaseUntil;
    private volatile boolean databaseDown;
    private final AtomicBoolean probing = new AtomicBoolean();

    public ReportTemplateService(ResourceLoader resourceLoader,
                                 ReportTemplateRepository reportTemplateRepository,
                                 @Value("${pdf.report.db.retry-after:PT30S}") Duration retryAfter) {
        this.resourceLoader = resourceLoader;
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader.getClassLoader());
        this.reportTemplateRepository = reportTemplateRepository;
        this.retryAfter = retryAfter;
    }

    public ReportTemplateDescriptor prepareTemplate(String reportName, Path workingDirectory) {
        return prepareTemplate(reportName, null, workingDirectory);
    }

    public ReportTemplateDescriptor prepareTemplate(String reportName, String templateName, Path workingDirectory) {
        String key = normalizeReportName(reportName);

        String resolvedTemplateName;
        Path htmlTarget;
        String dbTemplateName = null;
        Set<Path> copied = new LinkedHashSet<>();

        if (existsInDatabase(key)) {
            // Database branch: the HTML body is not copied to disk here — it is rendered
            // straight from the database by name (see ReportTemplateResolver) and the result
            // written to htmlTarget by the caller, exactly where the classpath branch would
            // have put its own copy. Assets (below) still land next to it either way.
            logger.debug("Resolved report template '{}' to database key '{}'", reportName, key);
            resolvedTemplateName = key;
            htmlTarget = workingDirectory.resolve(key + ".html");
            createParentDirectories(htmlTarget);
            dbTemplateName = ReportTemplateResolver.getPath(key);
        } else {
            TemplateResource templateResource = locateTemplate(reportName);
            resolvedTemplateName = templateResource.templateName();
            htmlTarget = workingDirectory.resolve(templateResource.relativePath());
            createParentDirectories(htmlTarget);
            copyResourceTo(templateResource.resource(), htmlTarget);
        }
        // htmlTarget is registered for cleanup the same way regardless of branch — the database
        // branch still writes real rendered bytes to it, just not by copying a classpath resource.
        copied.add(htmlTarget);

        copied.addAll(copyAssetTree(key, workingDirectory));

        if (StringUtils.hasText(templateName)) {
            copied.addAll(copyTemplateAssetsFlat(templateName, htmlTarget.getParent()));
        }

        List<Path> fontFiles = copied.stream()
                .filter(Files::isRegularFile)
                .filter(path -> isFontFile(path.getFileName().toString()))
                .collect(Collectors.toUnmodifiableList());

        return new ReportTemplateDescriptor(
                resolvedTemplateName,
                htmlTarget,
                List.copyOf(copied),
                fontFiles,
                dbTemplateName
        );
    }

    /**
     * Whether {@code key} has a database row, treating an unreachable database as "no row" so the
     * caller falls back to the classpath. Only connection failures are absorbed — anything else
     * (a bad query, a schema mismatch) is a real defect and propagates.
     */
    private boolean existsInDatabase(String key) {
        if (!databaseDown) {
            return lookUpInDatabase(key);
        }
        // Down: once the window has passed, exactly one request probes; the rest — including any
        // that arrive while that probe is in flight — keep resolving from the classpath.
        if (System.nanoTime() - skipDatabaseUntil < 0 || !probing.compareAndSet(false, true)) {
            return false;
        }
        try {
            return lookUpInDatabase(key);
        } finally {
            probing.set(false);
        }
    }

    private boolean lookUpInDatabase(String key) {
        try {
            boolean exists = reportTemplateRepository.existsByKey(key);
            if (databaseDown) {
                databaseDown = false;
                logger.info("Database is reachable again; report templates resolve from it once more");
            }
            return exists;
        } catch (CannotCreateTransactionException | DataAccessResourceFailureException e) {
            skipDatabaseUntil = System.nanoTime() + retryAfter.toNanos();
            databaseDown = true;
            logger.warn("Database unavailable, resolving report '{}' from the classpath and retrying in {}: {}",
                    key, retryAfter, e.getMostSpecificCause().getMessage());
            return false;
        }
    }

    private TemplateResource locateTemplate(String reportName) {
        String normalized = normalizeReportName(reportName);
        List<TemplateCandidate> candidates = buildCandidates(normalized);

        for (TemplateCandidate candidate : candidates) {
            Resource resource = resourceLoader.getResource(REPORT_PREFIX + candidate.resourcePath());
            if (resource.exists() && resource.isReadable()) {
                logger.debug("Resolved report template '{}' to resource '{}' under {}", reportName, candidate.resourcePath(), REPORT_PREFIX);
                return new TemplateResource(candidate.templateName(), candidate.resourcePath(), resource, normalized);
            }
        }

        for (TemplateCandidate candidate : candidates) {
            Resource resource = resourceLoader.getResource(TEMPLATE_PREFIX + candidate.resourcePath());
            if (resource.exists() && resource.isReadable()) {
                logger.debug("Resolved report template '{}' to resource '{}' under {}", reportName, candidate.resourcePath(), TEMPLATE_PREFIX);
                return new TemplateResource(candidate.templateName(), candidate.resourcePath(), resource, normalized);
            }
        }

        throw new ValidationException("Template '" + reportName + "' not found under classpath:/reports or classpath:/templates");
    }

    public static String normalizeReportName(String reportName) {
        if (!StringUtils.hasText(reportName)) {
            throw new ValidationException("Report template name cannot be empty");
        }

        String normalized = reportName.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith(".html")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        if (!StringUtils.hasText(normalized)) {
            throw new ValidationException("Report template name cannot be empty");
        }
        return normalized;
    }

    private List<TemplateCandidate> buildCandidates(String normalized) {
        Map<String, TemplateCandidate> ordered = new LinkedHashMap<>();
        addCandidate(ordered, normalized, normalized + ".html");
        addCandidate(ordered, normalized + "/index", normalized + "/index.html");

        String lastSegment = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;
        addCandidate(ordered, normalized + "/" + lastSegment, normalized + "/" + lastSegment + ".html");

        return new ArrayList<>(ordered.values());
    }

    private void addCandidate(Map<String, TemplateCandidate> map, String templateName, String resourcePath) {
        String cleanedResource = resourcePath.replaceAll("//+", "/");
        if (!map.containsKey(cleanedResource)) {
            String cleanedTemplate = templateName.replaceAll("//+", "/");
            map.put(cleanedResource, new TemplateCandidate(cleanedTemplate, cleanedResource));
        }
    }

    private List<Path> copyAssetTree(String assetBase, Path workingDirectory) {
        if (!StringUtils.hasText(assetBase)) {
            return List.of();
        }

        try {
            Resource[] resources = resourcePatternResolver.getResources(TEMPLATE_PREFIX + assetBase + "/**");
            Set<Path> copied = new LinkedHashSet<>();
            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }

                String relative = extractRelativePath(resource);
                if (!StringUtils.hasText(relative)) {
                    continue;
                }

                Path target = workingDirectory.resolve(relative);
                if (resource.isReadable()) {
                    copyResourceTo(resource, target);
                    copied.add(target);
                } else {
                    createParentDirectories(target);
                }
            }
            return new ArrayList<>(copied);
        } catch (FileNotFoundException e) {
            return List.of();
        } catch (IOException e) {
            throw new ValidationException("Failed to copy assets for report template", e);
        }
    }

    private String extractRelativePath(Resource resource) throws IOException {
        String path;
        if (resource instanceof ClassPathResource classPathResource) {
            path = classPathResource.getPath();
        } else {
            String url = resource.getURL().toString();
            int index = url.indexOf("/templates/");
            if (index == -1) {
                return null;
            }
            path = url.substring(index + "/templates/".length());
        }
        return path;
    }

    private List<Path> copyTemplateAssetsFlat(String templateName, Path targetDirectory) {
        try {
            Resource[] resources = resourcePatternResolver.getResources(TEMPLATE_PREFIX + templateName + "/**");
            Set<Path> copied = new LinkedHashSet<>();
            for (Resource resource : resources) {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }

                String filename = resource.getFilename();
                if (!StringUtils.hasText(filename)) {
                    continue;
                }

                Path target = targetDirectory.resolve(filename);
                copyResourceTo(resource, target);
                copied.add(target);
            }

            if (copied.isEmpty()) {
                throw new ValidationException("Template '" + templateName + "' not found under classpath:/templates");
            }
            return new ArrayList<>(copied);
        } catch (IOException e) {
            throw new ValidationException("Failed to copy assets for template '" + templateName + "'", e);
        }
    }

    private void copyResourceTo(Resource resource, Path target) {
        try (InputStream inputStream = resource.getInputStream()) {
            createParentDirectories(target);
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ValidationException("Failed to copy template resource '" + resource.getFilename() + "'", e);
        }
    }

    private void createParentDirectories(Path target) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new ValidationException("Failed to prepare working directory for report template", e);
        }
    }

    private boolean isFontFile(String filename) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf")
                || lower.endsWith(".otf")
                || lower.endsWith(".ttc")
                || lower.endsWith(".woff")
                || lower.endsWith(".woff2");
    }

    private record TemplateCandidate(String templateName, String resourcePath) {
    }

    private record TemplateResource(String templateName, String relativePath, Resource resource, String assetBase) {
    }
}

