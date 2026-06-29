package com.bracits.easyJavaPdf.service.renderer;

import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Third renderer option: Playwright for Java, driving headless Chromium directly from the
 * JVM via CDP (no Node sidecar). Selected with {@code renderer=playwright}.
 *
 * <p>Lifecycle: Playwright objects are NOT thread-safe, so each worker thread gets its own
 * warm {@link Playwright}+{@link Browser} (thread-local). Page handling depends on request mode:
 * <ul>
 *   <li><b>default</b> (mode unset) — a fresh {@link Page} is created and CLOSED per render,
 *       so the document's memory is freed immediately. Lowest RAM, safe under tight limits.</li>
 *   <li><b>{@code mode=fast}</b> — reuse a warm thread-local page across renders (Gotenberg-style):
 *       lower latency, but the page holds memory. To stop unbounded growth (a documented Playwright
 *       behaviour, issues #6319/#286), the reused page is RECYCLED — closed and recreated — every
 *       {@code pdf.playwright.reuse-recycle-after} renders.</li>
 * </ul>
 *
 * <p>Per-render overhead is minimized for light documents: no fixed sleep, print media set
 * once per page, and a SINGLE CDP round-trip that both awaits fonts and measures content
 * width for fit-to-width. On Alpine, Playwright uses the system musl Node
 * ({@code PLAYWRIGHT_NODEJS_PATH}) and system Chromium ({@code pdf.playwright.executable-path}).
 *
 * <p>Render options mirror {@link ChromiumPdfRenderer}: A4, printBackground, 20px margins,
 * auto fit-to-width (wkhtmltopdf-style shrink), tagged=false (lean output).
 */
@Component
public class PlaywrightPdfRenderer implements HtmlToPdfRenderer {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightPdfRenderer.class);

    private static final double A4_PORTRAIT_W_PX = 8.27 * 96;
    private static final double A4_LANDSCAPE_W_PX = 11.69 * 96;
    private static final long NAV_TIMEOUT_MS = 600_000;

    @Value("${pdf.playwright.enabled:true}")
    private boolean enabled;

    @Value("${pdf.playwright.executable-path:/usr/bin/chromium-browser}")
    private String executablePath;

    @Value("${pdf.chromium.fit-to-width:true}")
    private boolean fitToWidthDefault;

    // mode=fast: recycle the reused page after this many renders to bound its memory.
    @Value("${pdf.playwright.reuse-recycle-after:50}")
    private int recycleAfter;

    // Warm per-thread Playwright/Browser (Playwright is not thread-safe).
    private final ThreadLocal<Browser> threadBrowser = new ThreadLocal<>();
    private final List<Playwright> playwrights = new CopyOnWriteArrayList<>();
    private final List<Browser> browsers = new CopyOnWriteArrayList<>();

    // mode=fast only: a warm page reused across renders on the owning worker thread, plus a
    // per-thread render counter driving recycle-after-N. Default mode never touches these.
    private final ThreadLocal<Page> reusablePage = new ThreadLocal<>();
    private final ThreadLocal<Integer> reuseCount = ThreadLocal.withInitial(() -> 0);

    public String getName() {
        return "playwright";
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        boolean ok = executablePath != null && !executablePath.isBlank() && Files.exists(Paths.get(executablePath));
        if (!ok) {
            logger.warn("Playwright renderer unavailable: executable not found at '{}'", executablePath);
        }
        return ok;
    }

    private Browser browser() {
        Browser b = threadBrowser.get();
        if (b != null && b.isConnected()) {
            return b;
        }
        Playwright pw = Playwright.create();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setExecutablePath(Paths.get(executablePath))
                .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--disable-software-rasterizer",
                        "--no-zygote",
                        "--font-render-hinting=none",
                        "--disable-background-networking"));
        b = pw.chromium().launch(opts);
        playwrights.add(pw);
        browsers.add(b);
        threadBrowser.set(b);
        logger.info("Launched Playwright Chromium (thread {}) using {}", Thread.currentThread().getName(), executablePath);
        return b;
    }

    /** File-based entry point mirroring {@link ChromiumPdfRenderer#renderFromFile}. */
    public byte[] renderFromFile(Path htmlFile, Path cssFile, List<Path> fontFiles,
                                 String password, PageOrientation orientation, Path resourceBasePath,
                                 RendererTuning tuning) {
        if (htmlFile == null) {
            throw new PdfGenerationException("HTML file is required for Playwright rendering");
        }
        String css = null;
        try {
            if (cssFile != null && Files.exists(cssFile)) {
                String c = Files.readString(cssFile, StandardCharsets.UTF_8);
                if (c != null && !c.isBlank()) {
                    css = c;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to read CSS file for Playwright: {}", e.getMessage());
        }
        return renderFileToPdf(htmlFile, css, orientation, password, tuning);
    }

    @Override
    public byte[] render(String htmlContent, String cssContent, List<Path> fontFiles,
                         String password, PageOrientation orientation, Path resourceBasePath) {
        return render(htmlContent, cssContent, fontFiles, password, orientation, resourceBasePath, null);
    }

    @Override
    public byte[] render(String htmlContent, String cssContent, List<Path> fontFiles,
                         String password, PageOrientation orientation, Path resourceBasePath,
                         RendererTuning tuning) {
        Path tempHtml = null;
        try {
            tempHtml = Files.createTempFile("playwright-input-", ".html");
            Files.writeString(tempHtml, htmlContent == null ? "" : htmlContent, StandardCharsets.UTF_8);
            return renderFileToPdf(tempHtml, cssContent, orientation, password, tuning);
        } catch (Exception e) {
            throw new PdfGenerationException("Failed to render content with Playwright", e);
        } finally {
            if (tempHtml != null) {
                try { Files.deleteIfExists(tempHtml); } catch (Exception ignore) { }
            }
        }
    }

    private byte[] renderFileToPdf(Path htmlFile, String cssContent, PageOrientation orientation,
                                   String password, RendererTuning tuning) {
        boolean landscape = orientation == PageOrientation.LANDSCAPE || orientation == PageOrientation.SEASCAPE;

        if (password != null && !password.isBlank()) {
            logger.warn("PDF password requested but Playwright page.pdf() does not encrypt; ignoring (post-process if needed).");
        }

        boolean fast = tuning != null && Boolean.TRUE.equals(tuning.getFast());

        if (fast) {
            // mode=fast: reuse a warm thread-local page (lower latency, higher RAM). The page is
            // recycled every recycleAfter renders so memory can't grow without bound.
            Page page = acquireReusablePage();
            try {
                byte[] bytes = renderOnPage(page, htmlFile, cssContent, landscape, tuning);
                recycleIfDue();
                return bytes;
            } catch (RuntimeException e) {
                discardReusablePage(); // page may be in a bad state; force a fresh one next time
                throw e;
            }
        }

        // Default: fresh page per render, closed in finally so the document's memory is freed
        // immediately (reusing pages ballooned RAM ~2x under heavy load).
        Page page = newConfiguredPage();
        try {
            return renderOnPage(page, htmlFile, cssContent, landscape, tuning);
        } finally {
            try { page.close(); } catch (Exception ignore) { }
        }
    }

    /** A page with the per-render-invariant settings applied (viewport, timeout, print media). */
    private Page newConfiguredPage() {
        Page page = browser().newPage(new Browser.NewPageOptions().setViewportSize(1200, 1600));
        page.setDefaultTimeout(NAV_TIMEOUT_MS);
        page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
        return page;
    }

    /** Render one document on an already-configured page (navigation onward). */
    private byte[] renderOnPage(Page page, Path htmlFile, String cssContent, boolean landscape, RendererTuning tuning) {
        page.navigate("file://" + htmlFile.toAbsolutePath(),
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(NAV_TIMEOUT_MS));

        if (cssContent != null && !cssContent.isBlank()) {
            try {
                page.addStyleTag(new Page.AddStyleTagOptions().setContent(cssContent));
            } catch (Exception e) {
                logger.warn("Playwright addStyleTag failed: {}", e.getMessage());
            }
        }

        // ONE round-trip: await fonts AND measure content width (no fixed sleep).
        double scale = awaitFontsAndComputeScale(page, landscape, tuning);

        Page.PdfOptions pdf = new Page.PdfOptions()
                .setFormat("A4")
                .setLandscape(landscape)
                .setPrintBackground(true)
                .setScale(scale)
                .setPreferCSSPageSize(scale == 1.0 && fitOff(tuning))
                .setTagged(false)
                .setMargin(new Margin().setTop("20px").setRight("20px").setBottom("20px").setLeft("20px"));

        byte[] bytes = page.pdf(pdf);
        logger.debug("Playwright PDF generated, size: {} bytes (scale={})", bytes.length, scale);
        return bytes;
    }

    /** mode=fast: get the thread's reused page, creating it on first use. */
    private Page acquireReusablePage() {
        Page page = reusablePage.get();
        if (page == null) {
            page = newConfiguredPage();
            reusablePage.set(page);
            reuseCount.set(0);
        }
        return page;
    }

    /** mode=fast: count this render and recycle the page once it has done recycleAfter renders. */
    private void recycleIfDue() {
        int n = reuseCount.get() + 1;
        if (n >= Math.max(1, recycleAfter)) {
            discardReusablePage();
            logger.debug("Recycled reused Playwright page after {} renders (thread {})",
                    n, Thread.currentThread().getName());
        } else {
            reuseCount.set(n);
        }
    }

    /** mode=fast: close and forget the thread's reused page so the next render starts fresh. */
    private void discardReusablePage() {
        Page page = reusablePage.get();
        if (page != null) {
            try { page.close(); } catch (Exception ignore) { }
        }
        reusablePage.remove();
        reuseCount.remove();
    }

    private boolean fitOff(RendererTuning tuning) {
        boolean fit = fitToWidthDefault;
        if (tuning != null && tuning.getFitToWidth() != null) {
            fit = tuning.getFitToWidth();
        }
        return !fit;
    }

    /**
     * Single CDP evaluate that awaits document.fonts.ready and returns the max content width,
     * then computes the wkhtmltopdf-style shrink scale. Print media is already emulated on the
     * page, so the measurement matches the generated PDF.
     */
    private double awaitFontsAndComputeScale(Page page, boolean landscape, RendererTuning tuning) {
        double contentWidth = 0;
        try {
            Object res = page.evaluate(
                    "async () => {"
                    + " try { if (document.fonts) await document.fonts.ready; } catch (e) {}"
                    + " let m = Math.max(document.documentElement.scrollWidth, document.body ? document.body.scrollWidth : 0);"
                    + " for (const t of document.querySelectorAll('table')) m = Math.max(m, t.scrollWidth, t.offsetWidth);"
                    + " return m; }");
            contentWidth = res instanceof Number ? ((Number) res).doubleValue() : 0;
        } catch (Exception e) {
            logger.warn("Playwright fonts/measure step failed: {}", e.getMessage());
        }

        // Explicit override wins.
        if (tuning != null && tuning.getScale() != null && tuning.getScale() > 0) {
            return Math.min(2.0, Math.max(0.1, tuning.getScale()));
        }
        boolean fit = fitToWidthDefault;
        if (tuning != null && tuning.getFitToWidth() != null) {
            fit = tuning.getFitToWidth();
        }
        if (!fit) {
            return 1.0;
        }
        double printableWidth = (landscape ? A4_LANDSCAPE_W_PX : A4_PORTRAIT_W_PX) - 40.0;
        if (contentWidth > printableWidth && contentWidth > 0) {
            return Math.max(0.1, Math.min(1.0, printableWidth / contentWidth));
        }
        return 1.0;
    }

    @PreDestroy
    public void shutdown() {
        for (Browser b : browsers) {
            try { b.close(); } catch (Exception ignore) { }
        }
        for (Playwright pw : playwrights) {
            try { pw.close(); } catch (Exception ignore) { }
        }
        logger.info("Playwright renderer shut down ({} browsers)", browsers.size());
    }
}
