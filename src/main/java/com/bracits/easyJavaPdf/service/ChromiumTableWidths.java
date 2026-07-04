package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.model.PageOrientation;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.styledxmlparser.jsoup.Jsoup;
import com.itextpdf.styledxmlparser.jsoup.nodes.Document;
import com.itextpdf.styledxmlparser.jsoup.nodes.Element;
import com.itextpdf.styledxmlparser.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emulates Chromium's automatic table layout for the iText renderer, so over-wide tables fit the
 * printable page width the same way they do in a browser — by compressing column widths, never by
 * shrinking fonts.
 *
 * <p>Chromium's algorithm for a {@code width:100%} table whose declared column widths exceed the
 * page: each column has a <em>preferred</em> width (declared {@code <col>} width or widest
 * unwrapped cell content) and a <em>minimum</em> width (its widest unbreakable token — words never
 * break mid-token). Available width is distributed proportionally to the preferred widths but no
 * column goes below its minimum; text then wraps at word boundaries inside the assigned widths.
 * When even the minimum widths exceed the paper, Chromium's print pipeline (printToPDF) applies
 * its built-in <em>shrink-to-fit</em>: the whole page — fonts included — is scaled down by
 * pageWidth/contentWidth (verified against reference PDFs, which carry an ~0.8 page scale for
 * these spreadsheet exports). iText's own table layout can do neither (it lays the table out at
 * the sum of the content widths and lets the page clip it), so this class computes the Chromium
 * distribution up front, writes the resulting widths into the HTML as fixed per-column widths
 * that iText honors literally, and reports the equivalent shrink-to-fit font scale for the caller
 * to apply.
 *
 * <p>The rewrite appends a {@code <style>} block with {@code !important} per-column width rules
 * (higher specificity than, and after, the generic table-fit rules from {@link CssProcessor}), and
 * tags each processed table/col with generated classes. Returns {@code null} when no table needed
 * rewriting, so callers can fall back to the legacy behavior.
 *
 * <p>Column text is measured with iText's Helvetica metrics (the face substituted for
 * Arial/Calibri-style sheets when no font file is supplied), resolving per-cell font size and
 * weight from inline styles and the document's class rules. When the sum of minimum widths exceeds
 * the printable width even Chromium would overflow the page; in that case columns get their
 * minimums and the table overflows equivalently.
 */
public final class ChromiumTableWidths {

    private static final Logger logger = LoggerFactory.getLogger(ChromiumTableWidths.class);

    /** A4 in points. */
    private static final double A4_PORTRAIT_W = 595.0;
    private static final double A4_LANDSCAPE_W = 842.0;
    /** Used when the document declares no @page margins (pdfHTML's default page margin). */
    private static final double DEFAULT_PAGE_MARGIN_PT = 36.0;
    /**
     * Per-cell slack for collapsed borders and rounding, in points. Calibrated against Chromium
     * reference PDFs: raw Helvetica token widths track Chromium's Arial min-content within ~1%,
     * so only the ~1px collapsed border needs covering.
     */
    private static final double CELL_EXTRA_PT = 1.0;
    /** Safety factor on the shrink-to-fit scale (metric drift between measurement and layout). */
    private static final double FIT_SAFETY = 0.99;
    /** Never shrink below this, to avoid microscopic text on pathological input. */
    private static final double MIN_FONT_SCALE = 0.5;
    /** Default document font size (browser default 16px = 12pt) when no html/body rule declares one. */
    private static final double DEFAULT_FONT_PT = 12.0;

    private static final Pattern CSS_RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
    private static final Pattern CSS_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern MEDIA_SCREEN = Pattern.compile(
            "@media\\s+screen\\s*\\{(?:[^{}]*\\{[^{}]*\\})*[^{}]*\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_PRINT_WRAPPER = Pattern.compile(
            "@media\\s+print\\s*\\{((?:[^{}]*\\{[^{}]*\\})*[^{}]*)\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern AT_PAGE = Pattern.compile(
            "@page[^{]*\\{([^}]*)\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern LENGTH = Pattern.compile(
            "([0-9]*\\.?[0-9]+)\\s*(pt|px|in|cm|mm)", Pattern.CASE_INSENSITIVE);

    private ChromiumTableWidths() {
    }

    /**
     * Result of the rewrite: the modified HTML plus the whole-document font scale that replicates
     * Chromium's built-in print shrink-to-fit. {@code fontScale} is 1.0 whenever every table's
     * minimum (longest-token) column widths fit the printable width — fonts then stay at their
     * authored size, exactly as in Chromium.
     */
    public static final class Result {
        public final String html;
        public final double fontScale;

        Result(String html, double fontScale) {
            this.html = html;
            this.fontScale = fontScale;
        }
    }

    /**
     * Rewrites over-wide tables in {@code html} with Chromium-style computed column widths.
     *
     * @return the rewrite result, or {@code null} when nothing needed rewriting (caller keeps the
     *         original HTML and its legacy fit behavior)
     */
    public static Result apply(String html, PageOrientation orientation) {
        if (html == null || html.isEmpty() || !html.toLowerCase(Locale.ROOT).contains("<table")) {
            return null;
        }
        try {
            Document doc = Jsoup.parse(html);
            String css = collectCss(doc);
            Map<String, Map<String, String>> classRules = parseClassRules(css);
            double defaultFontPt = resolveDefaultFontSize(classRules);
            double printableWidth = printableWidth(css, orientation);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            // Pass 1: measure min/preferred column widths of every eligible table.
            java.util.List<TableStats> tables = new java.util.ArrayList<>();
            for (Element table : doc.select("table")) {
                // select() matches the element itself, so >1 means a nested table exists.
                if (table.select("table").size() > 1) {
                    continue; // nested tables: leave to the generic fit CSS
                }
                TableStats stats = measureTable(table, classRules, defaultFontPt, regular, bold);
                if (stats != null) {
                    tables.add(stats);
                }
            }
            if (tables.isEmpty()) {
                return null;
            }

            // Chromium's print shrink-to-fit: when even the minimum column widths overflow the
            // paper, the whole page is scaled down by pageWidth/contentWidth. Compute the global
            // factor from the widest table's minimums (small safety margin for metric drift).
            double fontScale = 1.0;
            for (TableStats stats : tables) {
                double sumMin = 0;
                for (double m : stats.minW) {
                    sumMin += m;
                }
                if (sumMin > printableWidth) {
                    fontScale = Math.min(fontScale, printableWidth / sumMin * FIT_SAFETY);
                }
            }
            fontScale = Math.max(fontScale, MIN_FONT_SCALE);

            // Pass 2: distribute the printable width per table in the scaled coordinate space.
            StringBuilder overrideCss = new StringBuilder();
            int tableIndex = 0;
            for (TableStats stats : tables) {
                writeTableWidths(stats, tableIndex++, printableWidth, fontScale, overrideCss);
            }

            Element styleHost = doc.head() != null ? doc.head() : doc.body();
            if (styleHost == null) {
                return null;
            }
            styleHost.appendElement("style").text(overrideCss.toString());
            logger.info("Chromium-style column widths applied to {} table(s), printable width {}pt, "
                            + "shrink-to-fit scale {}",
                    tableIndex, String.format(Locale.US, "%.1f", printableWidth),
                    String.format(Locale.US, "%.3f", fontScale));
            return new Result(doc.outerHtml(), fontScale);
        } catch (Exception e) {
            logger.warn("Chromium column width computation failed; keeping original HTML: {}",
                    e.getMessage());
            return null;
        }
    }

    /** Measured column widths of one table, in unscaled points. */
    private static final class TableStats {
        final Element table;
        final double[] minW;
        final double[] prefW;

        TableStats(Element table, double[] minW, double[] prefW) {
            this.table = table;
            this.minW = minW;
            this.prefW = prefW;
        }
    }

    /**
     * Measures one table's minimum (longest token) and preferred (declared or unwrapped content)
     * column widths. Returns null when the table is skipped (fewer than 2 rows or columns).
     */
    private static TableStats measureTable(Element table,
            Map<String, Map<String, String>> classRules, double defaultFontPt,
            PdfFont regular, PdfFont bold) {

        Elements rows = table.select("tr");
        if (rows.size() < 2) {
            return null;
        }
        int colCount = 0;
        for (Element row : rows) {
            int cols = 0;
            for (Element cell : row.children()) {
                if (isCell(cell)) {
                    cols += spanOf(cell);
                }
            }
            colCount = Math.max(colCount, cols);
        }
        if (colCount < 2) {
            return null;
        }

        double[] minW = new double[colCount];
        double[] maxContentW = new double[colCount];
        for (Element row : rows) {
            int col = 0;
            for (Element cell : row.children()) {
                if (!isCell(cell)) {
                    continue;
                }
                int span = spanOf(cell);
                if (span == 1 && col < colCount) {
                    CellFont cf = resolveCellFont(cell, classRules, defaultFontPt);
                    PdfFont font = cf.bold ? bold : regular;
                    String text = cell.text();
                    double extra = cf.paddingPt + CELL_EXTRA_PT;
                    maxContentW[col] = Math.max(maxContentW[col],
                            measure(font, text, cf.sizePt) + extra);
                    for (String token : text.split("[ \\t\\n\\r]+")) {
                        if (!token.isEmpty()) {
                            minW[col] = Math.max(minW[col], measure(font, token, cf.sizePt) + extra);
                        }
                    }
                }
                col += span;
            }
        }

        double[] preferredW = new double[colCount];
        Double[] declaredW = declaredColWidths(table, classRules, colCount);
        for (int i = 0; i < colCount; i++) {
            double declared = declaredW[i] != null ? declaredW[i] : maxContentW[i];
            preferredW[i] = Math.max(minW[i], declared);
        }

        double sumPref = 0;
        for (double p : preferredW) {
            sumPref += p;
        }
        if (sumPref <= 0) {
            return null;
        }
        return new TableStats(table, minW, preferredW);
    }

    /**
     * Distributes the printable width over one table's columns in the font-scaled coordinate
     * space and appends the width override CSS. With {@code fontScale} applied to the document's
     * text, the effective minimum widths are {@code minW*fontScale}, which by construction of the
     * global scale fit the printable width — so this reduces to Chromium's fit-or-compress cases.
     */
    private static void writeTableWidths(TableStats stats, int tableIndex, double printableWidth,
            double fontScale, StringBuilder overrideCss) {
        int colCount = stats.minW.length;
        double sumMin = 0;
        double sumPref = 0;
        double[] minW = new double[colCount];
        double[] prefW = new double[colCount];
        for (int i = 0; i < colCount; i++) {
            minW[i] = stats.minW[i] * fontScale;
            prefW[i] = stats.prefW[i] * fontScale;
            sumMin += minW[i];
            sumPref += prefW[i];
        }

        // Chromium distribution for a width:100% table.
        double[] finalW = new double[colCount];
        if (sumPref <= printableWidth) {
            // Table's declared/content widths already fit: expand proportionally to fill 100%.
            double f = printableWidth / sumPref;
            for (int i = 0; i < colCount; i++) {
                finalW[i] = prefW[i] * f;
            }
        } else if (sumMin <= printableWidth) {
            // Compress preferred widths proportionally, but never below a column's minimum.
            double f = (printableWidth - sumMin) / (sumPref - sumMin);
            for (int i = 0; i < colCount; i++) {
                finalW[i] = minW[i] + (prefW[i] - minW[i]) * f;
            }
        } else {
            // Only reachable when MIN_FONT_SCALE clamped the global scale: give each column its
            // minimum and let the table overflow, as Chromium would at its scale floor.
            System.arraycopy(minW, 0, finalW, 0, colCount);
            logger.warn("Table {} minimum column widths ({}pt) exceed printable width ({}pt) even "
                            + "at the minimum font scale; the table will overflow",
                    tableIndex, String.format(Locale.US, "%.0f", sumMin),
                    String.format(Locale.US, "%.0f", printableWidth));
        }

        String tableClass = "cwfit-t" + tableIndex;
        stats.table.addClass(tableClass);
        rebuildColgroup(stats.table, tableClass, colCount);

        double total = 0;
        for (double w : finalW) {
            total += w;
        }
        overrideCss.append(String.format(Locale.US,
                "table.%s { table-layout: fixed !important; width: %.2fpt !important; "
                        + "max-width: none !important; } ", tableClass, total));
        for (int i = 0; i < colCount; i++) {
            overrideCss.append(String.format(Locale.US,
                    "col.%s-c%d { width: %.2fpt !important; } ", tableClass, i, finalW[i]));
        }
    }

    /** Replaces the table's col/colgroup elements with one col per computed column. */
    private static void rebuildColgroup(Element table, String tableClass, int colCount) {
        table.select("colgroup, col").remove();
        StringBuilder cols = new StringBuilder("<colgroup>");
        for (int i = 0; i < colCount; i++) {
            cols.append("<col class=\"").append(tableClass).append("-c").append(i).append("\" />");
        }
        cols.append("</colgroup>");
        table.prepend(cols.toString());
    }

    private static boolean isCell(Element el) {
        String tag = el.tagName();
        return "td".equals(tag) || "th".equals(tag);
    }

    private static int spanOf(Element cell) {
        try {
            int span = Integer.parseInt(cell.attr("colspan").trim());
            return Math.max(1, span);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static double measure(PdfFont font, String text, double sizePt) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String t = text.replace('\u00A0', ' ');
        double w = font.getWidth(t, (float) sizePt);
        if (w <= 0) {
            // Glyphs missing from Helvetica (e.g. non-Latin scripts): rough estimate.
            w = 0.55 * sizePt * t.length();
        }
        return w;
    }

    /** Font size (pt), boldness and horizontal padding resolved for one cell. */
    private static final class CellFont {
        final double sizePt;
        final boolean bold;
        final double paddingPt;

        CellFont(double sizePt, boolean bold, double paddingPt) {
            this.sizePt = sizePt;
            this.bold = bold;
            this.paddingPt = paddingPt;
        }
    }

    private static CellFont resolveCellFont(Element cell, Map<String, Map<String, String>> classRules,
            double defaultFontPt) {
        double size = defaultFontPt;
        boolean bold = "th".equals(cell.tagName());
        double padLeft = 0;
        double padRight = 0;

        for (String cls : cell.classNames()) {
            Map<String, String> decls = classRules.get(cls);
            if (decls == null) {
                continue;
            }
            Double s = parseLengthPt(decls.get("font-size"));
            if (s != null) {
                size = s;
            }
            String weight = decls.get("font-weight");
            if (weight != null) {
                bold = weight.contains("bold") || weight.matches(".*[6-9]00.*");
            }
            Double pl = parseLengthPt(decls.get("padding-left"));
            if (pl != null) {
                padLeft = pl;
            }
            Double pr = parseLengthPt(decls.get("padding-right"));
            if (pr != null) {
                padRight = pr;
            }
        }

        Map<String, String> inline = parseDeclarations(cell.attr("style"));
        Double s = parseLengthPt(inline.get("font-size"));
        if (s != null) {
            size = s;
        }
        String weight = inline.get("font-weight");
        if (weight != null) {
            bold = weight.contains("bold") || weight.matches(".*[6-9]00.*");
        }
        Double pl = parseLengthPt(inline.get("padding-left"));
        if (pl != null) {
            padLeft = pl;
        }
        Double pr = parseLengthPt(inline.get("padding-right"));
        if (pr != null) {
            padRight = pr;
        }
        return new CellFont(size, bold, padLeft + padRight);
    }

    /**
     * Declared width per column from the table's {@code <col>} elements (inline style, width
     * attribute, or a class rule such as {@code table.sheet0 col.col3 { width:114pt }}).
     */
    private static Double[] declaredColWidths(Element table, Map<String, Map<String, String>> classRules,
            int colCount) {
        Double[] widths = new Double[colCount];
        int i = 0;
        for (Element col : table.select("col")) {
            if (i >= colCount) {
                break;
            }
            Double w = parseLengthPt(parseDeclarations(col.attr("style")).get("width"));
            if (w == null && col.hasAttr("width")) {
                w = parseLengthPt(col.attr("width") + "px");
            }
            if (w == null) {
                for (String cls : col.classNames()) {
                    Map<String, String> decls = classRules.get(cls);
                    if (decls != null) {
                        w = parseLengthPt(decls.get("width"));
                        if (w != null) {
                            break;
                        }
                    }
                }
            }
            int span = 1;
            try {
                span = Math.max(1, Integer.parseInt(col.attr("span").trim()));
            } catch (NumberFormatException ignored) {
            }
            for (int s = 0; s < span && i < colCount; s++, i++) {
                widths[i] = w;
            }
        }
        return widths;
    }

    private static String collectCss(Document doc) {
        StringBuilder css = new StringBuilder();
        for (Element style : doc.select("style")) {
            css.append(style.data()).append('\n');
            css.append(style.text()).append('\n');
        }
        return css.toString();
    }

    /**
     * Flattens the document CSS into class name → declarations. Screen-only media rules are
     * dropped and print media rules inlined, mirroring print rendering. Element selectors
     * ({@code html}, {@code body}) are stored under those names for default font lookup.
     */
    private static Map<String, Map<String, String>> parseClassRules(String css) {
        Map<String, Map<String, String>> rules = new HashMap<>();
        if (css == null) {
            return rules;
        }
        String cleaned = CSS_COMMENT.matcher(css).replaceAll("");
        cleaned = MEDIA_SCREEN.matcher(cleaned).replaceAll("");
        Matcher printBlock = MEDIA_PRINT_WRAPPER.matcher(cleaned);
        StringBuilder unwrapped = new StringBuilder();
        while (printBlock.find()) {
            unwrapped.append(printBlock.group(1)).append('\n');
        }
        printBlock.reset();
        cleaned = printBlock.replaceAll("") + unwrapped;
        cleaned = AT_PAGE.matcher(cleaned).replaceAll("");

        Matcher rule = CSS_RULE.matcher(cleaned);
        while (rule.find()) {
            String selectors = rule.group(1).trim();
            Map<String, String> decls = parseDeclarations(rule.group(2));
            if (decls.isEmpty()) {
                continue;
            }
            for (String selector : selectors.split(",")) {
                String key = classKey(selector.trim());
                if (key == null) {
                    continue;
                }
                rules.computeIfAbsent(key, k -> new HashMap<>()).putAll(decls);
            }
        }
        return rules;
    }

    /**
     * Maps a selector to its lookup key: the trailing class name of a simple class selector
     * ({@code td.style5}, {@code .b}, {@code table.sheet0 col.col3} → {@code col3}), or the
     * element name itself for bare {@code html}/{@code body} selectors.
     */
    private static String classKey(String selector) {
        if (selector.isEmpty() || selector.contains(":")) {
            return null;
        }
        String last = selector;
        int space = selector.lastIndexOf(' ');
        if (space >= 0) {
            last = selector.substring(space + 1);
        }
        int dot = last.lastIndexOf('.');
        if (dot >= 0) {
            String cls = last.substring(dot + 1).trim();
            return cls.isEmpty() ? null : cls;
        }
        if ("html".equalsIgnoreCase(last) || "body".equalsIgnoreCase(last)) {
            return last.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static Map<String, String> parseDeclarations(String block) {
        Map<String, String> decls = new HashMap<>();
        if (block == null || block.isEmpty()) {
            return decls;
        }
        for (String decl : block.split(";")) {
            int colon = decl.indexOf(':');
            if (colon > 0) {
                decls.put(decl.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        decl.substring(colon + 1).trim().toLowerCase(Locale.ROOT));
            }
        }
        return decls;
    }

    private static double resolveDefaultFontSize(Map<String, Map<String, String>> classRules) {
        for (String key : new String[] {"body", "html"}) {
            Map<String, String> decls = classRules.get(key);
            if (decls != null) {
                Double size = parseLengthPt(decls.get("font-size"));
                if (size != null) {
                    return size;
                }
            }
        }
        return DEFAULT_FONT_PT;
    }

    /** Printable width: page width (from @page size or the request orientation) minus side margins. */
    private static double printableWidth(String css, PageOrientation orientation) {
        boolean landscape = orientation == PageOrientation.LANDSCAPE
                || orientation == PageOrientation.SEASCAPE;
        double marginLeft = DEFAULT_PAGE_MARGIN_PT;
        double marginRight = DEFAULT_PAGE_MARGIN_PT;

        // Merge every @page block in document order — the processed HTML can contain both an
        // injected default rule and the document's own rule, and later declarations win.
        Map<String, String> decls = new HashMap<>();
        Matcher page = AT_PAGE.matcher(css == null ? "" : css);
        while (page.find()) {
            decls.putAll(parseDeclarations(page.group(1)));
        }
        String size = decls.get("size");
        if (size != null) {
            if (size.contains("landscape")) {
                landscape = true;
            } else if (size.contains("portrait")) {
                landscape = false;
            }
        }
        Double ml = parseLengthPt(decls.get("margin-left"));
        Double mr = parseLengthPt(decls.get("margin-right"));
        Double m = parseLengthPt(decls.get("margin"));
        if (ml != null) {
            marginLeft = ml;
        } else if (m != null) {
            marginLeft = m;
        }
        if (mr != null) {
            marginRight = mr;
        } else if (m != null) {
            marginRight = m;
        }
        double pageWidth = landscape ? A4_LANDSCAPE_W : A4_PORTRAIT_W;
        return pageWidth - marginLeft - marginRight;
    }

    private static Double parseLengthPt(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = LENGTH.matcher(value);
        if (!m.find()) {
            return null;
        }
        double v = Double.parseDouble(m.group(1));
        switch (m.group(2).toLowerCase(Locale.ROOT)) {
            case "px":
                return v * 0.75;
            case "in":
                return v * 72.0;
            case "cm":
                return v * 28.3464567;
            case "mm":
                return v * 2.83464567;
            default:
                return v;
        }
    }
}
