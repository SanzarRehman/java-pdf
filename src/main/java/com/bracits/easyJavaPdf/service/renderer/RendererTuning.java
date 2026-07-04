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
     * Whether to auto-shrink over-wide content to fit the printable page width. Playwright
     * renderer only; ignored by chromium (native print shrink-to-fit) and itext (always-on
     * Chromium-parity column fitting). Null = configured default.
     */
    Boolean fitToWidth;
    /**
     * Explicit print scale override (0.1-2.0; itext clamps to at most 1.0). Honored by the
     * itext, chromium and playwright renderers; when set it replaces any automatic fitting.
     */
    Double scale;
    /**
     * Playwright only: when {@code true} ({@code mode=fast}), reuse a warm page across renders
     * for lower latency at the cost of higher RAM (page recycled every N renders). Null/false =
     * close the page after each render (lowest RAM). Ignored by other renderers.
     */
    Boolean fast;
}
