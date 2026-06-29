package com.bracits.easyJavaPdf.service.renderer;

import com.bracits.easyJavaPdf.model.PageOrientation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Pre-warms the Playwright renderer at startup so the FIRST real request doesn't pay the
 * Chromium cold-launch cost (~2-3s).
 *
 * <p>Why this exists: the {@code chromium} renderer's Node {@code renderer-server} is launched
 * (and waited on) by {@code entrypoint.sh} before Spring Boot starts, so its browser is warm
 * on the first request. The {@link PlaywrightPdfRenderer} browser, by contrast, is thread-local
 * and launched LAZILY on first use — so the first request on each {@code pdf-worker-*} thread
 * ate the launch, making Playwright look ~2x slower than chromium for a single request.
 *
 * <p>Fix: after the app is ready, push one trivial render onto every worker thread so each
 * thread launches its thread-local browser and JITs the navigate/evaluate/pdf path up front.
 * A start barrier ({@link CountDownLatch}) forces the warm-up tasks onto DISTINCT threads so we
 * actually warm all of them, not the same one repeatedly. Runs only when
 * {@code pdf.renderer=playwright} and the renderer is available, on a background thread so
 * container health is not delayed.
 */
@Component
public class PlaywrightWarmup {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightWarmup.class);

    private static final String WARM_HTML =
            "<!doctype html><html><head><meta charset=\"utf-8\"></head><body>warmup</body></html>";

    private final PlaywrightPdfRenderer renderer;
    private final Executor executor;
    private final String selectedRenderer;
    private final int coreSize;

    public PlaywrightWarmup(PlaywrightPdfRenderer renderer,
                            @Qualifier("executor") Executor executor,
                            @Value("${pdf.renderer:chromium}") String selectedRenderer,
                            @Value("${pdf.executor.core-size:4}") int coreSize) {
        this.renderer = renderer;
        this.executor = executor;
        this.selectedRenderer = selectedRenderer;
        this.coreSize = coreSize;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!"playwright".equalsIgnoreCase(selectedRenderer)) {
            return; // only relevant when Playwright is the active renderer
        }
        if (!renderer.isAvailable()) {
            logger.info("Playwright pre-warm skipped: renderer not available");
            return;
        }
        // Warm on a daemon thread so readiness/health is not blocked by browser launches.
        Thread t = new Thread(this::warmAllWorkerThreads, "playwright-warmup");
        t.setDaemon(true);
        t.start();
    }

    private void warmAllWorkerThreads() {
        int n = Math.max(1, coreSize);
        long start = System.currentTimeMillis();
        logger.info("Pre-warming Playwright on {} worker thread(s)...", n);

        // Park n tasks on distinct threads (start barrier), then release them together so each
        // pdf-worker-* thread launches its own thread-local browser.
        CountDownLatch occupied = new CountDownLatch(n);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            try {
                executor.execute(() -> {
                    occupied.countDown();
                    try {
                        release.await();
                        renderer.render(WARM_HTML, null, null, null, PageOrientation.PORTRAIT, null);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        logger.warn("Playwright warm-up render failed on {}: {}",
                                Thread.currentThread().getName(), e.getMessage());
                    } finally {
                        done.countDown();
                    }
                });
            } catch (Exception e) {
                // Pool saturated (unlikely at startup) — release the barrier and stop adding.
                logger.warn("Playwright warm-up could not occupy all threads: {}", e.getMessage());
                occupied.countDown();
                done.countDown();
            }
        }

        try {
            // Wait until n threads are parked (or give up after a bit), then release them all.
            occupied.await(30, TimeUnit.SECONDS);
            release.countDown();
            done.await(120, TimeUnit.SECONDS);
            logger.info("Playwright pre-warm complete in {} ms ({} thread(s) warm)",
                    System.currentTimeMillis() - start, n);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            release.countDown();
        }
    }
}
