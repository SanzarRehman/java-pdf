package com.bracits.easyJavaPdf.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean
  @Qualifier("executor")
  public Executor taskExecutor() {
    return Executors.newFixedThreadPool(8);
  }
}
