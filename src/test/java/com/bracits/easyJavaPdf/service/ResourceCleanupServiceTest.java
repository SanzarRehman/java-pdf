package com.bracits.easyJavaPdf.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

class ResourceCleanupServiceTest {
    
    private ResourceCleanupService resourceCleanupService;
    
    @TempDir
    Path tempDirectory;
    
    @BeforeEach
    void setUp() {
        resourceCleanupService = new ResourceCleanupService();
        // Set shorter cleanup times for testing
        ReflectionTestUtils.setField(resourceCleanupService, "tempFileMaxAgeHours", 1);
        ReflectionTestUtils.setField(resourceCleanupService, "orphanedFileMaxAgeHours", 1);
    }
    
    @Test
    void shouldTrackResource() throws IOException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "track", ".tmp");
        
        // When
        resourceCleanupService.trackResource(testFile);
        
        // Then
        assertEquals(1, resourceCleanupService.getTrackedResourceCount());
        
        // Cleanup
        Files.deleteIfExists(testFile);
    }
    
    @Test
    void shouldNotTrackNullResource() {
        // When
        resourceCleanupService.trackResource(null);
        
        // Then
        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
    }
    
    @Test
    void shouldNotTrackNonExistentResource() throws IOException {
        // Given
        Path nonExistentFile = tempDirectory.resolve("nonexistent.tmp");
        
        // When
        resourceCleanupService.trackResource(nonExistentFile);
        
        // Then
        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
    }
    
    @Test
    void shouldUntrackResource() throws IOException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "untrack", ".tmp");
        resourceCleanupService.trackResource(testFile);
        assertEquals(1, resourceCleanupService.getTrackedResourceCount());
        
        // When
        resourceCleanupService.untrackResource(testFile);
        
        // Then
        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
        
        // Cleanup
        Files.deleteIfExists(testFile);
    }
    
    @Test
    void shouldHandleUntrackingNullResource() {
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> resourceCleanupService.untrackResource(null));
    }
    
    @Test
    void shouldCleanupOrphanedResources() throws IOException, InterruptedException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "orphaned", ".tmp");
        resourceCleanupService.trackResource(testFile);
        
        // Simulate old tracking time by manipulating internal state
        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        
        // Set tracking time to 2 hours ago (older than orphanedFileMaxAgeHours)
        trackedResources.put(testFile, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertTrue(Files.exists(testFile));
        
        // When
        int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
        
        // Then
        assertEquals(1, cleanedCount);
        assertFalse(Files.exists(testFile));
        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
    }
    
    @Test
    void shouldNotCleanupRecentResources() throws IOException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "recent", ".tmp");
        resourceCleanupService.trackResource(testFile);
        
        assertTrue(Files.exists(testFile));
        
        // When
        int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
        
        // Then
        assertEquals(0, cleanedCount);
        assertTrue(Files.exists(testFile));
        assertEquals(1, resourceCleanupService.getTrackedResourceCount());
        
        // Cleanup
        Files.deleteIfExists(testFile);
        resourceCleanupService.untrackResource(testFile);
    }
    
    @Test
    void shouldCleanupOrphanedDirectories() throws IOException {
        // Given
        Path testDir = Files.createTempDirectory(tempDirectory, "orphaneddir");
        Path fileInDir = Files.createTempFile(testDir, "file", ".tmp");
        Path subDir = Files.createTempDirectory(testDir, "subdir");
        Path fileInSubDir = Files.createTempFile(subDir, "subfile", ".tmp");
        
        resourceCleanupService.trackResource(testDir);
        
        // Simulate old tracking time
        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(testDir, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertTrue(Files.exists(testDir));
        assertTrue(Files.exists(fileInDir));
        assertTrue(Files.exists(subDir));
        assertTrue(Files.exists(fileInSubDir));
        
        // When
        int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
        
        // Then
        assertEquals(1, cleanedCount);
        assertFalse(Files.exists(testDir));
        assertFalse(Files.exists(fileInDir));
        assertFalse(Files.exists(subDir));
        assertFalse(Files.exists(fileInSubDir));
    }
    
    @Test
    void shouldHandleCleanupOfAlreadyDeletedResources() throws IOException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "deleted", ".tmp");
        resourceCleanupService.trackResource(testFile);
        
        // Delete the file manually
        Files.delete(testFile);
        
        // Simulate old tracking time
        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(testFile, Instant.now().minus(2, ChronoUnit.HOURS));
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
            assertEquals(0, cleanedCount); // No actual cleanup needed
            assertEquals(0, resourceCleanupService.getTrackedResourceCount()); // Should be untracked
        });
    }
    
    @Test
    void shouldRunScheduledCleanup() throws IOException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "scheduled", ".tmp");
        resourceCleanupService.trackResource(testFile);
        
        // Simulate old tracking time
        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(testFile, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertTrue(Files.exists(testFile));
        
        // When
        assertDoesNotThrow(() -> resourceCleanupService.scheduledCleanup());
        
        // Then
        assertFalse(Files.exists(testFile));
        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
    }
    
    @Test
    void shouldHandleExceptionsDuringScheduledCleanup() {
        // When & Then - should not throw exception even if cleanup fails
        assertDoesNotThrow(() -> resourceCleanupService.scheduledCleanup());
    }
    
    @Test
    void shouldShutdownGracefully() throws IOException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "shutdown", ".tmp");
        resourceCleanupService.trackResource(testFile);
        
        // Simulate old tracking time
        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(testFile, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertTrue(Files.exists(testFile));
        
        // When
        assertDoesNotThrow(() -> resourceCleanupService.shutdown());
        
        // Then
        assertFalse(Files.exists(testFile));
    }
    
    @Test
    void shouldHandleMultipleResourcesCorrectly() throws IOException {
        // Given
        Path oldFile = Files.createTempFile(tempDirectory, "old", ".tmp");
        Path recentFile = Files.createTempFile(tempDirectory, "recent", ".tmp");
        
        resourceCleanupService.trackResource(oldFile);
        resourceCleanupService.trackResource(recentFile);
        
        // Simulate old tracking time for one file
        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(oldFile, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertEquals(2, resourceCleanupService.getTrackedResourceCount());
        
        // When
        int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
        
        // Then
        assertEquals(1, cleanedCount);
        assertFalse(Files.exists(oldFile));
        assertTrue(Files.exists(recentFile));
        assertEquals(1, resourceCleanupService.getTrackedResourceCount());
        
        // Cleanup
        Files.deleteIfExists(recentFile);
        resourceCleanupService.untrackResource(recentFile);
    }
}