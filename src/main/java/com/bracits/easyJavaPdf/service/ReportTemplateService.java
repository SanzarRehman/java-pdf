package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads predefined report templates and their associated assets from the classpath.
 */
@Service
public class ReportTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(ReportTemplateService.class);
    private static final String TEMPLATE_PREFIX = "classpath:/templates/";

    private final ResourceLoader resourceLoader;
    private final ResourcePatternResolver resourcePatternResolver;

    public ReportTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader.getClassLoader());
    }

    public ReportTemplateDescriptor prepareTemplate(String reportName, Path workingDirectory) {
        TemplateResource templateResource = locateTemplate(reportName);
        Path htmlTarget = workingDirectory.resolve(templateResource.relativePath());
        createParentDirectories(htmlTarget);
        copyResourceTo(templateResource.resource(), htmlTarget);

        Set<Path> copied = new LinkedHashSet<>();
        copied.add(htmlTarget);

        copied.addAll(copyAssetTree(templateResource.assetBase(), workingDirectory));

        List<Path> fontFiles = copied.stream()
                .filter(Files::isRegularFile)
                .filter(path -> isFontFile(path.getFileName().toString()))
                .collect(Collectors.toUnmodifiableList());

        return new ReportTemplateDescriptor(
                templateResource.templateName(),
                htmlTarget,
                List.copyOf(copied),
                fontFiles
        );
    }

    private TemplateResource locateTemplate(String reportName) {
        String normalized = normalizeReportName(reportName);
        List<TemplateCandidate> candidates = buildCandidates(normalized);

        for (TemplateCandidate candidate : candidates) {
            Resource resource = resourceLoader.getResource(TEMPLATE_PREFIX + candidate.resourcePath());
            if (resource.exists() && resource.isReadable()) {
                logger.debug("Resolved report template '{}' to resource '{}'", reportName, candidate.resourcePath());
                return new TemplateResource(candidate.templateName(), candidate.resourcePath(), resource, normalized);
            }
        }

        throw new ValidationException("Template '" + reportName + "' not found under classpath:/templates");
    }

    private String normalizeReportName(String reportName) {
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

