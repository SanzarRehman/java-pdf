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

        ReflectionTestUtils.setField(resourceCleanupService, "tempFileMaxAgeHours", 1);
        ReflectionTestUtils.setField(resourceCleanupService, "orphanedFileMaxAgeHours", 1);
    }
    
    @Test
    void shouldTrackResource() throws IOException {

        Path testFile = Files.createTempFile(tempDirectory, "track", ".tmp");
        

        resourceCleanupService.trackResource(testFile);
        

        assertEquals(1, resourceCleanupService.getTrackedResourceCount());
        

        Files.deleteIfExists(testFile);
    }
    
    @Test
    void shouldNotTrackNullResource() {

        resourceCleanupService.trackResource(null);
        

        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
    }
    
    @Test
    void shouldNotTrackNonExistentResource() throws IOException {

        Path nonExistentFile = tempDirectory.resolve("nonexistent.tmp");
        

        resourceCleanupService.trackResource(nonExistentFile);
        

        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
    }
    
    @Test
    void shouldUntrackResource() throws IOException {

        Path testFile = Files.createTempFile(tempDirectory, "untrack", ".tmp");
        resourceCleanupService.trackResource(testFile);
        assertEquals(1, resourceCleanupService.getTrackedResourceCount());
        

        resourceCleanupService.untrackResource(testFile);
        

        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
        

        Files.deleteIfExists(testFile);
    }
    
    @Test
    void shouldHandleUntrackingNullResource() {

        assertDoesNotThrow(() -> resourceCleanupService.untrackResource(null));
    }
    
    @Test
    void shouldCleanupOrphanedResources() throws IOException, InterruptedException {

        Path testFile = Files.createTempFile(tempDirectory, "orphaned", ".tmp");
        resourceCleanupService.trackResource(testFile);
        

        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        

        trackedResources.put(testFile, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertTrue(Files.exists(testFile));
        

        int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
        

        assertEquals(1, cleanedCount);
        assertFalse(Files.exists(testFile));
        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
    }
    
    @Test
    void shouldNotCleanupRecentResources() throws IOException {

        Path testFile = Files.createTempFile(tempDirectory, "recent", ".tmp");
        resourceCleanupService.trackResource(testFile);
        
        assertTrue(Files.exists(testFile));
        

        int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
        

        assertEquals(0, cleanedCount);
        assertTrue(Files.exists(testFile));
        assertEquals(1, resourceCleanupService.getTrackedResourceCount());
        

        Files.deleteIfExists(testFile);
        resourceCleanupService.untrackResource(testFile);
    }
    
    @Test
    void shouldCleanupOrphanedDirectories() throws IOException {

        Path testDir = Files.createTempDirectory(tempDirectory, "orphaneddir");
        Path fileInDir = Files.createTempFile(testDir, "file", ".tmp");
        Path subDir = Files.createTempDirectory(testDir, "subdir");
        Path fileInSubDir = Files.createTempFile(subDir, "subfile", ".tmp");
        
        resourceCleanupService.trackResource(testDir);
        

        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(testDir, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertTrue(Files.exists(testDir));
        assertTrue(Files.exists(fileInDir));
        assertTrue(Files.exists(subDir));
        assertTrue(Files.exists(fileInSubDir));
        

        int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
        

        assertEquals(1, cleanedCount);
        assertFalse(Files.exists(testDir));
        assertFalse(Files.exists(fileInDir));
        assertFalse(Files.exists(subDir));
        assertFalse(Files.exists(fileInSubDir));
    }
    
    @Test
    void shouldHandleCleanupOfAlreadyDeletedResources() throws IOException {

        Path testFile = Files.createTempFile(tempDirectory, "deleted", ".tmp");
        resourceCleanupService.trackResource(testFile);
        

        Files.delete(testFile);
        

        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(testFile, Instant.now().minus(2, ChronoUnit.HOURS));
        

        assertDoesNotThrow(() -> {
            int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
            assertEquals(0, cleanedCount);
            assertEquals(0, resourceCleanupService.getTrackedResourceCount());
        });
    }
    
    @Test
    void shouldRunScheduledCleanup() throws IOException {

        Path testFile = Files.createTempFile(tempDirectory, "scheduled", ".tmp");
        resourceCleanupService.trackResource(testFile);
        

        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(testFile, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertTrue(Files.exists(testFile));
        

        assertDoesNotThrow(() -> resourceCleanupService.scheduledCleanup());
        

        assertFalse(Files.exists(testFile));
        assertEquals(0, resourceCleanupService.getTrackedResourceCount());
    }
    
    @Test
    void shouldHandleExceptionsDuringScheduledCleanup() {

        assertDoesNotThrow(() -> resourceCleanupService.scheduledCleanup());
    }
    
    @Test
    void shouldShutdownGracefully() throws IOException {

        Path testFile = Files.createTempFile(tempDirectory, "shutdown", ".tmp");
        resourceCleanupService.trackResource(testFile);
        

        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(testFile, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertTrue(Files.exists(testFile));
        

        assertDoesNotThrow(() -> resourceCleanupService.shutdown());
        

        assertFalse(Files.exists(testFile));
    }
    
    @Test
    void shouldHandleMultipleResourcesCorrectly() throws IOException {

        Path oldFile = Files.createTempFile(tempDirectory, "old", ".tmp");
        Path recentFile = Files.createTempFile(tempDirectory, "recent", ".tmp");
        
        resourceCleanupService.trackResource(oldFile);
        resourceCleanupService.trackResource(recentFile);
        

        @SuppressWarnings("unchecked")
        ConcurrentMap<Path, Instant> trackedResources = 
            (ConcurrentMap<Path, Instant>) ReflectionTestUtils.getField(resourceCleanupService, "trackedResources");
        trackedResources.put(oldFile, Instant.now().minus(2, ChronoUnit.HOURS));
        
        assertEquals(2, resourceCleanupService.getTrackedResourceCount());
        

        int cleanedCount = resourceCleanupService.cleanupOrphanedResources();
        

        assertEquals(1, cleanedCount);
        assertFalse(Files.exists(oldFile));
        assertTrue(Files.exists(recentFile));
        assertEquals(1, resourceCleanupService.getTrackedResourceCount());
        

        Files.deleteIfExists(recentFile);
        resourceCleanupService.untrackResource(recentFile);
    }
}