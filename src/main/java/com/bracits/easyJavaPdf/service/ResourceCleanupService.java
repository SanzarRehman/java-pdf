package com.bracits.easyJavaPdf.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service responsible for managing resource cleanup, including scheduled cleanup
 * of orphaned temporary files and directories.
 */
@Service
public class ResourceCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceCleanupService.class);
    
    private final ConcurrentMap<Path, Instant> trackedResources = new ConcurrentHashMap<>();
    
    @Value("${app.cleanup.temp-file-max-age-hours:24}")
    private int tempFileMaxAgeHours;
    
    @Value("${app.cleanup.orphaned-file-max-age-hours:1}")
    private int orphanedFileMaxAgeHours;
    
    /**
     * Registers a resource for tracking. This helps identify orphaned resources
     * that may need cleanup if not properly managed.
     *
     * @param resourcePath the path to track
     */
    public void trackResource(Path resourcePath) {
        if (resourcePath != null && Files.exists(resourcePath)) {
            trackedResources.put(resourcePath, Instant.now());
            logger.debug("Started tracking resource: {}", resourcePath);
        }
    }
    
    /**
     * Unregisters a resource from tracking, indicating it has been properly cleaned up.
     *
     * @param resourcePath the path to stop tracking
     */
    public void untrackResource(Path resourcePath) {
        if (resourcePath != null) {
            trackedResources.remove(resourcePath);
            logger.debug("Stopped tracking resource: {}", resourcePath);
        }
    }
    
    /**
     * Gets the count of currently tracked resources.
     *
     * @return the number of tracked resources
     */
    public int getTrackedResourceCount() {
        return trackedResources.size();
    }
    
    /**
     * Manually triggers cleanup of orphaned resources.
     * This method can be called programmatically in addition to scheduled execution.
     *
     * @return the number of resources cleaned up
     */
    public int cleanupOrphanedResources() {
        logger.info("Starting manual cleanup of orphaned resources");
        
        int cleanedCount = 0;
        List<Path> toRemove = new ArrayList<>();
        Instant cutoffTime = Instant.now().minus(orphanedFileMaxAgeHours, ChronoUnit.HOURS);
        
        for (var entry : trackedResources.entrySet()) {
            Path resourcePath = entry.getKey();
            Instant trackingTime = entry.getValue();
            
            if (trackingTime.isBefore(cutoffTime)) {
                try {
                    if (Files.exists(resourcePath)) {
                        if (Files.isDirectory(resourcePath)) {
                            deleteDirectoryRecursively(resourcePath);
                        } else {
                            Files.delete(resourcePath);
                        }
                        cleanedCount++;
                        logger.info("Cleaned up orphaned resource: {}", resourcePath);
                    }
                    toRemove.add(resourcePath);
                } catch (IOException e) {
                    logger.error("Failed to cleanup orphaned resource: {}", resourcePath, e);
                }
            }
        }
        

        toRemove.forEach(trackedResources::remove);
        
        logger.info("Manual cleanup completed. Cleaned {} orphaned resources", cleanedCount);
        return cleanedCount;
    }
    
    /**
     * Scheduled cleanup task that runs periodically to clean up orphaned temporary files.
     * Runs every hour by default.
     */
    @Scheduled(fixedRateString = "${app.cleanup.schedule-rate-ms:3600000}")
    public void scheduledCleanup() {
        logger.debug("Starting scheduled cleanup of orphaned resources");
        
        try {
            int cleanedCount = cleanupOrphanedResources();
            cleanupSystemTempFiles();
            
            logger.info("Scheduled cleanup completed. {} tracked resources remaining", 
                       trackedResources.size());
        } catch (Exception e) {
            logger.error("Error during scheduled cleanup", e);
        }
    }
    
    /**
     * Cleans up old temporary files from the system temp directory that match
     * common patterns used by the application.
     */
    private void cleanupSystemTempFiles() {
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
            Instant cutoffTime = Instant.now().minus(tempFileMaxAgeHours, ChronoUnit.HOURS);
            
            int cleanedCount = 0;
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir, 
                    path -> isApplicationTempFile(path) && isOlderThan(path, cutoffTime))) {
                
                for (Path tempFile : stream) {
                    try {
                        if (Files.isDirectory(tempFile)) {
                            deleteDirectoryRecursively(tempFile);
                        } else {
                            Files.delete(tempFile);
                        }
                        cleanedCount++;
                        logger.debug("Cleaned up old temp file: {}", tempFile);
                    } catch (IOException e) {
                        logger.warn("Failed to cleanup temp file: {}", tempFile, e);
                    }
                }
            }
            
            if (cleanedCount > 0) {
                logger.info("Cleaned up {} old temporary files from system temp directory", cleanedCount);
            }
            
        } catch (IOException e) {
            logger.error("Error during system temp file cleanup", e);
        }
    }
    
    /**
     * Checks if a file is likely created by this application based on naming patterns.
     */
    private boolean isApplicationTempFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("pdf") || 
               fileName.startsWith("html") || 
               fileName.startsWith("merge") ||
               fileName.startsWith("temp") ||
               fileName.contains("easyjavapdf");
    }
    
    /**
     * Checks if a file is older than the specified cutoff time.
     */
    private boolean isOlderThan(Path path, Instant cutoffTime) {
        try {
            Instant fileTime = Files.getLastModifiedTime(path).toInstant();
            return fileTime.isBefore(cutoffTime);
        } catch (IOException e) {
            logger.debug("Could not get modification time for: {}", path);
            return false;
        }
    }
    
    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        
        Files.walk(directory)
                .sorted((path1, path2) -> path2.compareTo(path1))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete path during recursive cleanup: {}", path, e);
                    }
                });
    }
    
    /**
     * Shuts down the service and performs final cleanup.
     */
    public void shutdown() {
        logger.info("Shutting down ResourceCleanupService");
        
        try {
            cleanupOrphanedResources();
            logger.info("Final cleanup completed during shutdown");
        } catch (Exception e) {
            logger.error("Error during shutdown cleanup", e);
        }
    }
}