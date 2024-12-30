package com.bracits.pdftester;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean
  public Executor taskExecutor() {
    return Executors.newFixedThreadPool(8);  // Define the size of the thread pool
  }
}
