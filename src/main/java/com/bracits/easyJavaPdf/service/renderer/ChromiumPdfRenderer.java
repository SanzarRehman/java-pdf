package com.bracits.easyJavaPdf.service.renderer;

import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bracits.easyJavaPdf.service.renderer.RendererTuning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.*;

/**
 * PDF renderer using Chromium headless via Puppeteer (Node.js).
 * Provides high-fidelity PDF generation matching browser rendering exactly.
 * 
 * Optimized for:
 * - GPU acceleration on macOS (Metal)
 * - Parallel batch processing with configurable thread pool
 * - Memory-efficient large document handling
 */
@Component
public class ChromiumPdfRenderer implements HtmlToPdfRenderer {

    private static final Logger logger = LoggerFactory.getLogger(ChromiumPdfRenderer.class);
    private static final String RENDERER_NAME = "chromium";

    @Value("${pdf.chromium.node-path:node}")
    private String nodePath;

    @Value("${pdf.chromium.timeout-seconds:600}")
    private int timeoutSeconds;

    @Value("${pdf.chromium.puppeteer-script:classpath:scripts/puppeteer-pdf.js}")
    private String puppeteerScriptPath;

    @Value("${pdf.chromium.parallel-workers:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
    private int parallelWorkers;

    @Value("${pdf.chromium.high-quality:false}")
    private boolean highQuality;

    @Value("${pdf.chromium.chunked-threshold-mb:10}")
    private int chunkedThresholdMb;

    private final ObjectMapper objectMapper;
    private ExecutorService executorService;
    private Semaphore renderSemaphore;

    private static final Pattern HEAD_OPEN_TAG = Pattern.compile("(?i)<head\\b[^>]*>");
    private static final Pattern HTML_OPEN_TAG = Pattern.compile("(?i)<html\\b[^>]*>");

    public ChromiumPdfRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // Create thread pool for parallel PDF generation
        int workers = Math.max(1, Math.min(parallelWorkers, Runtime.getRuntime().availableProcessors()));
        logger.info("Initializing Chromium PDF renderer with {} parallel workers", workers);
        this.executorService = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "chromium-pdf-worker");
            t.setDaemon(true);
            return t;
        });

        // Enforce a global concurrency cap for Chromium rendering.
        // Without this, concurrent HTTP requests can spawn too many Node/Chromium processes.
        this.renderSemaphore = new Semaphore(workers);
    }

    @PreDestroy
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
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

        Path tempHtmlFile = null;
        Path tempOutputFile = null;
        Path tempConfigFile = null;

        boolean permitAcquired = false;

        try {
            if (renderSemaphore != null) {
                renderSemaphore.acquire();
                permitAcquired = true;
            }

            // Create temporary files
            tempHtmlFile = Files.createTempFile("chromium-render-", ".html");
            tempOutputFile = Files.createTempFile("chromium-output-", ".pdf");
            tempConfigFile = Files.createTempFile("chromium-config-", ".json");

            // Prepare HTML with embedded CSS without duplicating large strings in memory.
            writeHtmlWithCss(tempHtmlFile, htmlContent, cssContent);

            // Check file size to determine which script to use
            long htmlSizeBytes = Files.size(tempHtmlFile);
            long htmlSizeMb = htmlSizeBytes / (1024 * 1024);
            boolean useChunked = htmlSizeMb >= chunkedThresholdMb;
            
            if (useChunked) {
                logger.info("Large HTML detected ({}MB), using chunked parallel PDF generation", htmlSizeMb);
            }

            // Prepare configuration
            Map<String, Object> config = buildConfig(tempHtmlFile, tempOutputFile, orientation, password, tuning);
            Files.writeString(tempConfigFile, objectMapper.writeValueAsString(config), StandardCharsets.UTF_8);

            // Get the appropriate Puppeteer script path
            Path scriptPath = useChunked ? resolveChunkedPuppeteerScript() : resolvePuppeteerScript();

            // Execute Puppeteer
            logPreChromiumLaunch(scriptPath, tempConfigFile, htmlSizeBytes, useChunked);
            executePuppeteer(scriptPath, tempConfigFile);

            // Read and return the generated PDF
            if (!Files.exists(tempOutputFile) || Files.size(tempOutputFile) == 0) {
                throw new PdfGenerationException("Chromium PDF generation produced no output");
            }

            byte[] pdfBytes = Files.readAllBytes(tempOutputFile);
            logger.info("Chromium PDF generation completed, size: {} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (IOException e) {
            throw new PdfGenerationException("Failed to generate PDF with Chromium", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PdfGenerationException("Chromium PDF generation was interrupted", e);
        } finally {
            // Cleanup temporary files
            cleanup(tempHtmlFile, tempOutputFile, tempConfigFile);

            if (permitAcquired && renderSemaphore != null) {
                renderSemaphore.release();
            }
        }
    }

    @Override
    public String getName() {
        return RENDERER_NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(nodePath, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            if (completed && process.exitValue() == 0) {
                logger.debug("Node.js is available for Chromium PDF rendering");
                return true;
            }
        } catch (Exception e) {
            logger.debug("Node.js is not available: {}", e.getMessage());
        }
        return false;
    }

    private void writeHtmlWithCss(Path targetFile, String htmlContent, String cssContent) throws IOException {
        if (htmlContent == null) {
            Files.writeString(targetFile, "", StandardCharsets.UTF_8);
            return;
        }

        if (cssContent == null || cssContent.isBlank()) {
            Files.writeString(targetFile, htmlContent, StandardCharsets.UTF_8);
            return;
        }

        // Stream-write to avoid building a second huge HTML string.
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            Matcher headMatcher = HEAD_OPEN_TAG.matcher(htmlContent);
            if (headMatcher.find()) {
                int insertPos = headMatcher.end();
                writer.write(htmlContent, 0, insertPos);
                writer.write("<style>");
                writer.write(cssContent);
                writer.write("</style>");
                writer.write(htmlContent, insertPos, htmlContent.length() - insertPos);
                return;
            }

            Matcher htmlMatcher = HTML_OPEN_TAG.matcher(htmlContent);
            if (htmlMatcher.find()) {
                int insertPos = htmlMatcher.end();
                writer.write(htmlContent, 0, insertPos);
                writer.write("<head><style>");
                writer.write(cssContent);
                writer.write("</style></head>");
                writer.write(htmlContent, insertPos, htmlContent.length() - insertPos);
                return;
            }

            // Wrap in complete HTML document
            writer.write("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>");
            writer.write(cssContent);
            writer.write("</style></head><body>");
            writer.write(htmlContent);
            writer.write("</body></html>");
        }
    }

    private Map<String, Object> buildConfig(Path htmlFile, Path outputFile,
                                            PageOrientation orientation, String password,
                                            RendererTuning tuning) {
        Map<String, Object> config = new HashMap<>();
        config.put("htmlPath", htmlFile.toAbsolutePath().toString());
        config.put("outputPath", outputFile.toAbsolutePath().toString());
        config.put("format", "A4");
        config.put("printBackground", true);
        config.put("preferCSSPageSize", true);

        // Set orientation
        boolean isLandscape = orientation == PageOrientation.LANDSCAPE
                || orientation == PageOrientation.SEASCAPE;
        config.put("landscape", isLandscape);

        // High quality mode (2x device scale factor)
        config.put("highQuality", highQuality);

        // Margins (in pixels, Puppeteer default)
        Map<String, String> margin = new HashMap<>();
        margin.put("top", "20px");
        margin.put("right", "20px");
        margin.put("bottom", "20px");
        margin.put("left", "20px");
        config.put("margin", margin);

        // Password protection (if needed - note: Puppeteer doesn't support this directly,
        // would need post-processing with a PDF library)
        if (password != null && !password.isBlank()) {
            config.put("password", password);
            logger.warn("PDF password protection requested but Puppeteer doesn't support it directly. " +
                    "Consider post-processing the PDF for encryption.");
        }

        // Apply optional tuning overrides for chunked renderer
        if (tuning != null) {
            Integer chunkSize = tuning.getChunkSizeMb();
            Integer parallelism = tuning.getParallelism();

            // Guardrails: avoid extreme values that can explode memory
            if (chunkSize != null && chunkSize > 0) {
                double clampedChunk = Math.min(chunkSize.doubleValue(), 20.0);
                if (clampedChunk != chunkSize.doubleValue()) {
                    logger.info("Clamping chunkSizeMb from {} to {} for safety", chunkSize, clampedChunk);
                }
                config.put("chunkSizeMb", clampedChunk);
            }
            if (parallelism != null && parallelism > 0) {
                int clampedParallel = Math.min(parallelism, 8);
                if (clampedParallel != parallelism) {
                    logger.info("Clamping parallelism from {} to {} for safety", parallelism, clampedParallel);
                }
                config.put("parallelism", clampedParallel);
            }
        }

        return config;
    }

    private Path resolvePuppeteerScript() throws IOException {
        // Check environment variable first (set in Docker)
        String envScriptsPath = System.getenv("PDF_SCRIPTS_PATH");
        if (envScriptsPath != null) {
            Path dockerScript = Path.of(envScriptsPath, "puppeteer-pdf.js");
            if (Files.exists(dockerScript)) {
                logger.debug("Using Puppeteer script from PDF_SCRIPTS_PATH: {}", dockerScript);
                return dockerScript;
            }
        }

        // Docker path
        Path dockerPath = Path.of("/app/scripts/puppeteer-pdf.js");
        if (Files.exists(dockerPath)) {
            logger.debug("Using Puppeteer script from Docker path: {}", dockerPath);
            return dockerPath;
        }

        // Local development path
        Path localPath = Path.of("src/main/resources/scripts/puppeteer-pdf.js");
        if (Files.exists(localPath)) {
            logger.debug("Using Puppeteer script from local path: {}", localPath);
            return localPath;
        }

        // Fallback: Create a temporary script file with embedded Puppeteer code
        logger.warn("No external Puppeteer script found, using embedded fallback");
        Path tempScript = Files.createTempFile("puppeteer-pdf-", ".js");
        Files.writeString(tempScript, getPuppeteerScriptContent(), StandardCharsets.UTF_8);
        return tempScript;
    }

    private Path resolveChunkedPuppeteerScript() throws IOException {
        // Check environment variable first (set in Docker)
        String envScriptsPath = System.getenv("PDF_SCRIPTS_PATH");
        if (envScriptsPath != null) {
            Path dockerScript = Path.of(envScriptsPath, "puppeteer-pdf-chunked.js");
            if (Files.exists(dockerScript)) {
                logger.debug("Using chunked Puppeteer script from PDF_SCRIPTS_PATH: {}", dockerScript);
                return dockerScript;
            }
        }

        // Docker path
        Path dockerPath = Path.of("/app/scripts/puppeteer-pdf-chunked.js");
        if (Files.exists(dockerPath)) {
            logger.debug("Using chunked Puppeteer script from Docker path: {}", dockerPath);
            return dockerPath;
        }

        // Local development path
        Path localPath = Path.of("src/main/resources/scripts/puppeteer-pdf-chunked.js");
        if (Files.exists(localPath)) {
            logger.debug("Using chunked Puppeteer script from local path: {}", localPath);
            return localPath;
        }

        // Fallback to regular script if chunked not found
        logger.warn("Chunked Puppeteer script not found, falling back to regular script");
        return resolvePuppeteerScript();
    }

    private String getPuppeteerScriptContent() {
        return """
            const puppeteer = require('puppeteer');
            const fs = require('fs');
            
            async function generatePdf() {
                const configPath = process.argv[2];
                if (!configPath) {
                    console.error('Config file path required');
                    process.exit(1);
                }
            
                const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
            
                const browser = await puppeteer.launch({
                    headless: 'new',
                    args: [
                        '--no-sandbox',
                        '--disable-setuid-sandbox',
                        '--disable-dev-shm-usage',
                        '--disable-gpu'
                    ]
                });
            
                try {
                    const page = await browser.newPage();
                    
                    // Load HTML file
                    await page.goto('file://' + config.htmlPath, {
                        waitUntil: 'networkidle0',
                        timeout: 30000
                    });
            
                    // Wait for fonts and images to load
                    await page.evaluate(() => document.fonts.ready);
            
                    // Generate PDF
                    await page.pdf({
                        path: config.outputPath,
                        format: config.format || 'A4',
                        landscape: config.landscape || false,
                        printBackground: config.printBackground !== false,
                        preferCSSPageSize: config.preferCSSPageSize || false,
                        margin: config.margin || {
                            top: '20px',
                            right: '20px',
                            bottom: '20px',
                            left: '20px'
                        }
                    });
            
                    console.log('PDF generated successfully');
                } finally {
                    await browser.close();
                }
            }
            
            generatePdf().catch(err => {
                console.error('Error:', err.message);
                process.exit(1);
            });
            """;
    }

    private void executePuppeteer(Path scriptPath, Path configFile) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(nodePath);
        command.add(scriptPath.toAbsolutePath().toString());
        command.add(configFile.toAbsolutePath().toString());

        logger.debug("Executing Puppeteer: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        boolean logStdout = "true".equalsIgnoreCase(System.getenv("LOG_PUPPETEER_STDOUT"));

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                // Make memory diagnostics visible by default.
                if (line.startsWith("[mem]")) {
                    logger.info("Puppeteer {}", line);
                } else if (logStdout) {
                    logger.info("Puppeteer: {}", line);
                } else {
                    logger.debug("Puppeteer: {}", line);
                }
            }
        }

        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new PdfGenerationException("Chromium PDF generation timed out after " + timeoutSeconds + " seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new PdfGenerationException("Chromium PDF generation failed with exit code " + exitCode +
                    ": " + output.toString().trim());
        }
    }

    private void logPreChromiumLaunch(Path scriptPath, Path configFile, long htmlSizeBytes, boolean useChunked) {
        logger.info("==================== CHROMIUM LAUNCH ====================" );
        logger.info("About to spawn Node/Chromium via Puppeteer");
        logger.info("chunked={} htmlSize={} nodePath={} timeoutSeconds={} script={} config={}",
                useChunked,
                formatBytes(htmlSizeBytes),
                nodePath,
                timeoutSeconds,
                safePath(scriptPath),
                safePath(configFile));

        logMemorySnapshot("pre-chromium");
        logger.info("=========================================================" );
    }

    private void logMemorySnapshot(String phase) {
        try {
            MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memoryMxBean.getNonHeapMemoryUsage();

            logger.info("[mem][{}] jvm.heap.used={} committed={} max={}",
                    phase,
                    formatBytes(heap.getUsed()),
                    formatBytes(heap.getCommitted()),
                    formatBytes(heap.getMax()));
            logger.info("[mem][{}] jvm.nonheap.used={} committed={} max={}",
                    phase,
                    formatBytes(nonHeap.getUsed()),
                    formatBytes(nonHeap.getCommitted()),
                    formatBytes(nonHeap.getMax()));

            Runtime runtime = Runtime.getRuntime();
            logger.info("[mem][{}] runtime.free={} total={} max={}",
                    phase,
                    formatBytes(runtime.freeMemory()),
                    formatBytes(runtime.totalMemory()),
                    formatBytes(runtime.maxMemory()));

            // Docker/Linux: show cgroup memory usage + limits when present.
            logCgroupMemoryIfPresent(phase);
        } catch (Exception e) {
            logger.warn("[mem][{}] Failed to collect memory snapshot: {}", phase, e.getMessage());
        }
    }

    private void logCgroupMemoryIfPresent(String phase) {
        // cgroup v2
        Path v2Current = Path.of("/sys/fs/cgroup/memory.current");
        Path v2Max = Path.of("/sys/fs/cgroup/memory.max");
        if (Files.exists(v2Current) && Files.exists(v2Max)) {
            try {
                String currentRaw = Files.readString(v2Current, StandardCharsets.UTF_8).trim();
                String maxRaw = Files.readString(v2Max, StandardCharsets.UTF_8).trim();

                Long current = tryParseLong(currentRaw);
                Long max = tryParseLong(maxRaw);

                logger.info("[mem][{}] cgroupv2.current={} cgroupv2.max={}",
                        phase,
                        current == null ? currentRaw : formatBytes(current),
                        max == null ? maxRaw : formatBytes(max));
                return;
            } catch (Exception ignored) {
                // Fall through to v1.
            }
        }

        // cgroup v1
        Path v1Usage = Path.of("/sys/fs/cgroup/memory/memory.usage_in_bytes");
        Path v1Limit = Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes");
        if (Files.exists(v1Usage) && Files.exists(v1Limit)) {
            try {
                String usageRaw = Files.readString(v1Usage, StandardCharsets.UTF_8).trim();
                String limitRaw = Files.readString(v1Limit, StandardCharsets.UTF_8).trim();

                Long usage = tryParseLong(usageRaw);
                Long limit = tryParseLong(limitRaw);

                logger.info("[mem][{}] cgroupv1.usage={} cgroupv1.limit={}",
                        phase,
                        usage == null ? usageRaw : formatBytes(usage),
                        limit == null ? limitRaw : formatBytes(limit));
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private static Long tryParseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String safePath(Path path) {
        if (path == null) {
            return "<null>";
        }
        try {
            return path.toAbsolutePath().toString();
        } catch (Exception e) {
            return path.toString();
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "unknown";
        }
        double value = (double) bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return String.format("%.2f%s", value, units[unitIndex]);
    }

    private void cleanup(Path... files) {
        for (Path file : files) {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    logger.warn("Failed to cleanup temporary file: {}", file, e);
                }
            }
        }
    }

    /**
     * Batch render multiple PDFs in parallel using the thread pool.
     * Ideal for generating many PDFs at once (e.g., bulk reports).
     * 
     * @param requests List of render requests
     * @return List of rendered PDF bytes in the same order as requests
     */
    public List<byte[]> renderBatch(List<RenderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        logger.info("Starting batch PDF generation for {} documents using {} workers",
                requests.size(), parallelWorkers);

        long startTime = System.currentTimeMillis();

        // Submit all tasks
        List<Future<byte[]>> futures = new ArrayList<>();
        for (RenderRequest request : requests) {
            futures.add(executorService.submit(() ->
                    render(request.htmlContent(), request.cssContent(), request.fontFiles(),
                            request.password(), request.orientation(), request.resourceBasePath())
            ));
        }

        // Collect results
        List<byte[]> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                byte[] pdf = futures.get(i).get(timeoutSeconds, TimeUnit.SECONDS);
                results.add(pdf);
                logger.debug("Batch PDF {} completed: {} bytes", i + 1, pdf.length);
            } catch (TimeoutException e) {
                throw new PdfGenerationException("Batch PDF " + (i + 1) + " timed out after " + timeoutSeconds + " seconds");
            } catch (ExecutionException e) {
                throw new PdfGenerationException("Batch PDF " + (i + 1) + " failed: " + e.getCause().getMessage(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PdfGenerationException("Batch PDF generation was interrupted");
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Batch PDF generation completed: {} documents in {}ms ({}ms avg per document)",
                results.size(), duration, duration / results.size());

        return results;
    }

    /**
     * Render request record for batch processing.
     */
    public record RenderRequest(
            String htmlContent,
            String cssContent,
            List<Path> fontFiles,
            String password,
            PageOrientation orientation,
            Path resourceBasePath
    ) {
        public RenderRequest(String htmlContent) {
            this(htmlContent, null, null, null, PageOrientation.PORTRAIT, null);
        }

        public RenderRequest(String htmlContent, String cssContent) {
            this(htmlContent, cssContent, null, null, PageOrientation.PORTRAIT, null);
        }
    }
}
