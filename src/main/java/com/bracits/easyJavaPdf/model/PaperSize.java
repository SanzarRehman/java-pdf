package com.bracits.easyJavaPdf.model;

import com.itextpdf.kernel.geom.PageSize;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents the paper size for generated PDF pages.
 *
 * <p>Each value knows how to express itself for both rendering backends:
 * <ul>
 *   <li>{@link #toPuppeteerFormat()} — the format name passed to Puppeteer's {@code page.pdf()}
 *       (Chromium renderer).</li>
 *   <li>{@link #toITextPageSize()} — the corresponding iText {@link PageSize} (iText renderer).</li>
 * </ul>
 */
public enum PaperSize {
    A4(PageSize.A4),
    A3(PageSize.A3),
    A5(PageSize.A5),
    LETTER(PageSize.LETTER),
    LEGAL(PageSize.LEGAL);

    private final PageSize iTextPageSize;

    PaperSize(PageSize iTextPageSize) {
        this.iTextPageSize = iTextPageSize;
    }

    /**
     * @return the format name understood by Puppeteer's {@code page.pdf()} (e.g. "A4", "LETTER").
     *         Puppeteer matches these case-insensitively.
     */
    public String toPuppeteerFormat() {
        return name();
    }

    /**
     * @return the iText {@link PageSize} for this paper size (portrait orientation).
     */
    public PageSize toITextPageSize() {
        return iTextPageSize;
    }

    /**
     * @return the CSS {@code @page size} keyword for this paper size (e.g. "A4", "letter").
     *         These are the named sizes defined by CSS Paged Media.
     */
    public String toCssPageSize() {
        return switch (this) {
            case LETTER -> "letter";
            case LEGAL -> "legal";
            default -> name(); // A4, A3, A5
        };
    }

    /**
     * Attempts to parse the provided value into a {@link PaperSize}.
     *
     * @param value the raw value to parse
     * @return an {@link Optional} containing the parsed paper size if successful
     */
    public static Optional<PaperSize> parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            return Optional.of(PaperSize.valueOf(normalized));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the paper size or falls back to the provided default.
     *
     * @param value        the raw paper size value
     * @param defaultValue the default to use when parsing fails or input is blank
     * @return the resolved paper size
     */
    public static PaperSize fromOrDefault(String value, PaperSize defaultValue) {
        return parse(value).orElse(defaultValue);
    }

    /**
     * @return all supported paper size names for validation/error messaging.
     */
    public static Set<String> supportedNames() {
        return Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
