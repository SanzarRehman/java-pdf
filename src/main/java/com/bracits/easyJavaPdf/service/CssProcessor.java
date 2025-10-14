package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.model.PageOrientation;
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
        boolean hasPageRule = containsPageRule(cssContent);
        if (cssContent == null || cssContent.trim().isEmpty()) {
            return getDefaultPageCss(orientation, hasPageRule);
        }

        logger.debug("Processing CSS content for PDF generation safety");

        try {
            String sanitized = sanitizeCss(cssContent);
            String orientationRule = buildOrientationCss(orientation);
            StringBuilder resultBuilder = new StringBuilder();
            appendCssChunk(resultBuilder, getDefaultPageCss(orientation, hasPageRule));
            appendCssChunk(resultBuilder, orientationRule);
            appendCssChunk(resultBuilder, sanitized);
            String result = resultBuilder.toString().trim();

            logger.debug("CSS processing completed, original length: {}, processed length: {}", 
                        cssContent.length(), result.length());

            return result;

        } catch (Exception e) {
            logger.warn("Error during CSS processing, returning safe defaults", e);
            return getDefaultPageCss(orientation, hasPageRule);
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
     * Removes CSS properties that can cause margin collapse issues in iText.
     */
    private String removeMarginCollapseProperties(String cssContent) {
        // Remove properties that can cause margin collapse issues
        return cssContent

            .replaceAll("list-style[^:]*:[^;]*;", "")

            .replaceAll("margin-(?:top|bottom)\\s*:[^;]*;", "")

            .replaceAll("line-height\\s*:\\s*[^;]*;", "line-height: 1.4;")

            .replaceAll("vertical-align\\s*:[^;]*;", "")

            .replaceAll("overflow[^:]*:[^;]*;", "")

            .replaceAll("(?:min-|max-)?height\\s*:[^;]*;", "")

            .replaceAll("position\\s*:\\s*(?:relative|absolute|fixed|sticky)[^;]*;", "")

            .replaceAll("z-index\\s*:[^;]*;", "")

            .replaceAll("display\\s*:\\s*(?:inline-block|table|table-cell|table-row)[^;]*;", "");
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
     * Returns safe default CSS for PDF generation with defensive styles.
     */
    private String getDefaultPageCss(PageOrientation orientation, boolean hasPageRule) {
        StringBuilder builder = new StringBuilder();

        if (!hasPageRule) {
            if (orientation == PageOrientation.LANDSCAPE || orientation == PageOrientation.SEASCAPE) {
                builder.append("@page { size: A4 landscape; margin: 2cm; } ");
            } else {
                builder.append("@page { size: A4; margin: 2cm; } ");
            }
        }

        builder.append("* { box-sizing: border-box; margin: 0 !important; padding: 0; } ")
               .append("body { margin: 0 !important; padding: 0 !important; line-height: 1.4 !important; } ")
               .append("html { margin: 0 !important; padding: 0 !important; } ")
               .append("p, div, h1, h2, h3, h4, h5, h6 { margin-top: 0 !important; margin-bottom: 0.5em !important; } ")
               .append("p:last-child, div:last-child { margin-bottom: 0 !important; } ")
               .append("ul, ol { margin: 0 !important; padding-left: 1.5em !important; } ")
               .append("li { margin: 0 !important; padding: 0.2em 0 !important; } ")
               .append(".pdf-content-wrapper { margin: 0 !important; padding: 0 !important; } ")
               .append(".li-wrapper { margin: 0 !important; padding: 0 !important; display: block !important; } ")
               .append(".section-wrapper { margin: 0 !important; padding: 0 !important; display: block !important; } ")
               .append(".block-wrapper { margin: 0.5em 0 !important; padding: 0.5em !important; display: block !important; }");

        return builder.toString();
    }

    private boolean containsPageRule(String cssContent) {
        if (cssContent == null) {
            return false;
        }
        return cssContent.toLowerCase().contains("@page");
    }

    private String buildOrientationCss(PageOrientation orientation) {
        if (orientation == null) {
            return "";
        }

        return switch (orientation) {
            case LANDSCAPE -> "@media print { @page { size: A4 landscape; } }";
            case SEASCAPE -> "@media print { @page { size: A4 landscape; } }";
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