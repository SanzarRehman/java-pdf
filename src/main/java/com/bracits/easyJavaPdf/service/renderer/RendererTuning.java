package com.bracits.easyJavaPdf.service.renderer;

import lombok.Builder;
import lombok.Value;

/**
 * Optional renderer tuning parameters passed from API requests.
 */
@Value
@Builder
public class RendererTuning {
    /** Chunk size in MB for chunked Chromium pipeline. */
    Integer chunkSizeMb;
    /** Parallelism (number of concurrent chunk workers) for chunked Chromium pipeline. */
    Integer parallelism;
    /**
     * Whether to auto-shrink over-wide content to fit the printable page width
     * (wkhtmltopdf "smart shrinking" equivalent). Playwright renderer only; ignored by the
     * chromium renderer, which never auto-shrinks. Null = use configured default.
     */
    Boolean fitToWidth;
    /**
     * Explicit print scale override (Chromium range 0.1-2.0). Honored by both the chromium
     * and playwright renderers; for playwright it wins over auto fit-to-width. Null = scale 1
     * for chromium, or let playwright compute the fit-to-width scale.
     */
    Double scale;
    /**
     * Playwright only: when {@code true} ({@code mode=fast}), reuse a warm page across renders
     * for lower latency at the cost of higher RAM (page recycled every N renders). Null/false =
     * close the page after each render (lowest RAM). Ignored by other renderers.
     */
    Boolean fast;
}
