package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.model.PageOrientation;
import com.bracits.easyJavaPdf.model.PaperSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CssProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CssProcessor.class);

    private static final Pattern URL_PATTERN = Pattern.compile(
        "[^;{}]*url\\s*\\([^)]*\\)[^;]*;", Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "@import[^;]*;", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROBLEMATIC_PROPERTIES_PATTERN = Pattern.compile(
        "(?:content|counter-[^:]*|string-set|target-[^:]*|columns?|" +
        "display\\s*:\\s*(?:flex|grid)|flex[^:]*|grid[^:]*|transform|animation|transition|filter)\\s*:[^;]*;",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern PSEUDO_ELEMENTS_PATTERN = Pattern.compile(
        "[^{}]*::?(?:before|after)[^{]*\\{(?:[^{}]*\\{[^{}]*\\}[^{}]*|[^{}])*\\}",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern EMPTY_RULES_PATTERN = Pattern.compile("\\s*\\{\\s*\\}");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Pattern FONT_FACE_PATTERN = Pattern.compile(
        "@font-face\\s*\\{[^{}]*\\}", Pattern.CASE_INSENSITIVE);

    private static final Pattern SRC_DECLARATION_PATTERN = Pattern.compile(
        "src\\s*:[^;]*;", Pattern.CASE_INSENSITIVE);

    private static final Pattern FONT_URL_PATTERN = Pattern.compile(
        "url\\s*\\(\\s*['\"]?([^'\"\\)]+)['\"]?\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> SUPPORTED_FONT_EXTENSIONS = Set.of("ttf", "otf", "woff", "woff2", "ttc");

    /**
     * Processes CSS content by removing problematic rules and properties
     * that cause FileNotFoundException and other PDF generation issues.
     *
     * @param cssContent the raw CSS content to process
     * @return sanitized CSS content safe for PDF generation
     */
    public String processCss(String cssContent, PageOrientation orientation) {
        return processCss(cssContent, orientation, PaperSize.A4);
    }

    public String processCss(String cssContent, PageOrientation orientation, PaperSize paperSize) {
        PaperSize effectiveSize = paperSize != null ? paperSize : PaperSize.A4;
        boolean hasPageRule = containsPageRule(cssContent);
        if (cssContent == null || cssContent.trim().isEmpty()) {
            return getDefaultPageCss(orientation, hasPageRule, effectiveSize);
        }

        logger.debug("Processing CSS content for PDF generation safety");

        try {
            String sanitized = sanitizeCss(cssContent);
            String orientationRule = buildOrientationCss(orientation, effectiveSize);
            StringBuilder resultBuilder = new StringBuilder();
            appendCssChunk(resultBuilder, getDefaultPageCss(orientation, hasPageRule, effectiveSize));
            appendCssChunk(resultBuilder, orientationRule);
            appendCssChunk(resultBuilder, sanitized);
            String result = resultBuilder.toString().trim();

            logger.debug("CSS processing completed, original length: {}, processed length: {}",
                        cssContent.length(), result.length());

            return result;

        } catch (Exception e) {
            logger.warn("Error during CSS processing, returning safe defaults", e);
            return getDefaultPageCss(orientation, hasPageRule, effectiveSize);
        }
    }

    public String processCss(String cssContent) {
        return processCss(cssContent, null);
    }

    /**
     * Core CSS sanitization method using compiled regex patterns for performance.
     */
    private String sanitizeCss(String cssContent) {
        logger.debug("Sanitizing CSS using optimized regex patterns");
        
        String sanitized = cssContent;
        

        sanitized = URL_PATTERN.matcher(sanitized).replaceAll("");
        

        sanitized = IMPORT_PATTERN.matcher(sanitized).replaceAll("");
        

        sanitized = PROBLEMATIC_PROPERTIES_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = PSEUDO_ELEMENTS_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = stripCssVariables(sanitized);
        sanitized = removeMarginCollapseProperties(sanitized);
        

        sanitized = EMPTY_RULES_PATTERN.matcher(sanitized).replaceAll("");
        

        sanitized = WHITESPACE_PATTERN.matcher(sanitized).replaceAll(" ");
        

        String preservedFontFaces = extractSafeFontFaces(cssContent);
        
        return (preservedFontFaces + " " + sanitized).trim();
    }

    /**
     * Removes CSS properties that cause critical issues in iText.
     * Preserves font-weight, font-style, margin, and other styling properties.
     */
    private String removeMarginCollapseProperties(String cssContent) {
        // Only remove properties that cause actual rendering failures in iText
        // Preserve: font-weight, font-style, margin, padding, line-height, etc.
        return cssContent
            // Remove only truly problematic properties that break iText
            .replaceAll("position\\s*:\\s*(?:fixed|sticky)[^;]*;", "")
            .replaceAll("z-index\\s*:[^;]*;", "");
    }

    /**
     * Extracts and sanitizes @font-face rules, removing problematic src declarations.
     */
    private String extractSafeFontFaces(String cssContent) {
        StringBuilder safeFontFaces = new StringBuilder();
        
        Matcher matcher = FONT_FACE_PATTERN.matcher(cssContent);
        while (matcher.find()) {
            String fontFace = matcher.group();
            
            // Only keep font-face rules that have font-family but remove src with url()
            if (fontFace.toLowerCase().contains("font-family")) {
                // Remove src declarations with url() references
                String cleanedFontFace = SRC_DECLARATION_PATTERN.matcher(fontFace).replaceAll("");
                String safeSrc = buildSafeFontSrc(fontFace);
                if (safeSrc != null) {
                    int closingIndex = cleanedFontFace.lastIndexOf('}');
                    if (closingIndex != -1) {
                        String before = cleanedFontFace.substring(0, closingIndex).trim();
                        String after = cleanedFontFace.substring(closingIndex);
                        cleanedFontFace = before + " " + safeSrc + " " + after;
                    } else {
                        cleanedFontFace = cleanedFontFace.trim() + " " + safeSrc;
                    }
                }
                
                // Only keep if it still has meaningful content after cleaning
                if (cleanedFontFace.toLowerCase().contains("font-family") && 
                    cleanedFontFace.length() > "@font-face{}".length()) {
                    safeFontFaces.append(cleanedFontFace).append(" ");
                    logger.debug("Preserved sanitized font-face rule");
                }
            }
        }
        
        return safeFontFaces.toString();
    }

    private String buildSafeFontSrc(String fontFace) {
        Matcher srcMatcher = SRC_DECLARATION_PATTERN.matcher(fontFace);
        while (srcMatcher.find()) {
            String declaration = srcMatcher.group();
            Matcher urlMatcher = FONT_URL_PATTERN.matcher(declaration);
            while (urlMatcher.find()) {
                String url = urlMatcher.group(1).trim();
                if (isSupportedFontAsset(url)) {
                    String format = detectFontFormat(url);
                    return String.format("src: url('%s')%s;", url, format);
                }
            }
        }
        return null;
    }

    private boolean isSupportedFontAsset(String path) {
        String lower = path.toLowerCase();
        int dotIndex = lower.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == lower.length() - 1) {
            return false;
        }
        String ext = lower.substring(dotIndex + 1);
        return SUPPORTED_FONT_EXTENSIONS.contains(ext);
    }

    private String detectFontFormat(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".ttf") || lower.endsWith(".ttc")) {
            return " format('truetype')";
        }
        if (lower.endsWith(".otf")) {
            return " format('opentype')";
        }
        if (lower.endsWith(".woff2")) {
            return " format('woff2')";
        }
        if (lower.endsWith(".woff")) {
            return " format('woff')";
        }
        return "";
    }

    /**
     * Returns safe default CSS for PDF generation with minimal interference.
     * Does NOT add any @page rule - lets HTML render naturally like in a browser.
     */
    private String getDefaultPageCss(PageOrientation orientation, boolean hasPageRule, PaperSize paperSize) {
        StringBuilder builder = new StringBuilder();

        // Only add @page rule if orientation is explicitly requested
        if (!hasPageRule && orientation != null) {
            if (orientation == PageOrientation.LANDSCAPE || orientation == PageOrientation.SEASCAPE) {
                builder.append("@page { size: ").append(paperSize.toCssPageSize()).append(" landscape; } ");
            }
            // For portrait/default, don't add @page - let it render naturally
        }

        // Minimal helper classes only
        builder.append(".pdf-content-wrapper { display: block; } ")
               .append(".li-wrapper { display: block; } ")
               .append(".section-wrapper { display: block; } ")
               .append(".block-wrapper { display: block; }");

        return builder.toString();
    }

    private boolean containsPageRule(String cssContent) {
        if (cssContent == null) {
            return false;
        }
        return cssContent.toLowerCase().contains("@page");
    }

    private String buildOrientationCss(PageOrientation orientation, PaperSize paperSize) {
        if (orientation == null) {
            return "";
        }

        String size = paperSize.toCssPageSize();
        return switch (orientation) {
            case LANDSCAPE -> "@media print { @page { size: " + size + " landscape; } }";
            case SEASCAPE -> "@media print { @page { size: " + size + " landscape; } }";
            default -> "";
        };
    }

    private void appendCssChunk(StringBuilder builder, String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(' ');
        }

        builder.append(chunk.trim());
    }

    private String stripCssVariables(String cssContent) {
        if (cssContent == null || cssContent.isBlank()) {
            return cssContent;
        }

        String withoutRoot = cssContent.replaceAll(":root\\s*\\{[^{}]*\\}", "");

        java.util.regex.Pattern fallbackPattern = java.util.regex.Pattern.compile("var\\(([^,\\)]+),\\s*([^\\)]+)\\)");
        java.util.regex.Matcher fallbackMatcher = fallbackPattern.matcher(withoutRoot);
        StringBuffer resolvedWithFallback = new StringBuffer();
        while (fallbackMatcher.find()) {
            String fallback = fallbackMatcher.group(2).trim();
            fallbackMatcher.appendReplacement(resolvedWithFallback, java.util.regex.Matcher.quoteReplacement(fallback));
        }
        fallbackMatcher.appendTail(resolvedWithFallback);

        return resolvedWithFallback.toString().replaceAll("var\\([^)]*\\)", "initial");
    }
}