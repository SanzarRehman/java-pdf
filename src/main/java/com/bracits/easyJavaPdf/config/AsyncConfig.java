package com.bracits.easyJavaPdf.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor for PDF generation work.
 *
 * <p>This is the single async boundary for a request and the admission point for
 * load-shedding. It is a bounded pool with a bounded queue and an abort rejection
 * policy: when both the pool and queue are saturated, new work is rejected so the
 * controller can return HTTP 503 instead of queuing unboundedly or hanging.
 *
 * <p>History: the previous design used an 8-thread pool combined with nested
 * {@code @Async} + blocking {@code CompletableFuture.get()} layers, which caused
 * thread-pool starvation deadlock at moderate concurrency (~8+ simultaneous
 * requests). The nesting has been removed; this pool now bounds real render
 * concurrency. Size it to roughly match downstream render capacity
 * (RENDERER_CONCURRENCY / pdf.chromium.max-processes).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean
  @Qualifier("executor")
  public Executor taskExecutor(
      @Value("${pdf.executor.core-size:4}") int coreSize,
      @Value("${pdf.executor.max-size:4}") int maxSize,
      @Value("${pdf.executor.queue-capacity:50}") int queueCapacity) {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(coreSize);
    ex.setMaxPoolSize(maxSize);
    ex.setQueueCapacity(queueCapacity);
    ex.setThreadNamePrefix("pdf-worker-");
    // Load-shedding: reject when saturated (mapped to HTTP 503 by the controller)
    // instead of an unbounded queue. Prevents the previous deadlock/backlog growth.
    ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    ex.setWaitForTasksToCompleteOnShutdown(true);
    ex.setAwaitTerminationSeconds(60);
    ex.initialize();
    return ex;
  }
}
