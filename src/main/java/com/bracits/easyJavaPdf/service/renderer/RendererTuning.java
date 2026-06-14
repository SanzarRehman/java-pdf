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
     * (wkhtmltopdf "smart shrinking" equivalent). Null = use configured default.
     */
    Boolean fitToWidth;
    /**
     * Explicit print scale override (Chromium range 0.1-2.0). When set, it wins over
     * auto fit-to-width. Null = let the renderer compute the scale.
     */
    Double scale;
}
