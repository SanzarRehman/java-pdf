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
}
