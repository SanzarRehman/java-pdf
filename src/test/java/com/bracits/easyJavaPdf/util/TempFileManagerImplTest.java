package com.bracits.easyJavaPdf.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TempFileManagerImplTest {
    
    private TempFileManagerImpl tempFileManager;
    
    @TempDir
    Path tempDirectory;
    
    @BeforeEach
    void setUp() {
        tempFileManager = new TempFileManagerImpl();
    }
    
    @Test
    void shouldCreateTempFile() throws IOException {
        // When
        Path tempFile = tempFileManager.createTempFile("test", ".tmp");
        
        // Then
        assertNotNull(tempFile);
        assertTrue(Files.exists(tempFile));
        assertTrue(tempFile.getFileName().toString().startsWith("test"));
        assertTrue(tempFile.getFileName().toString().endsWith(".tmp"));
        
        // Verify it's registered for cleanup
        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertTrue(registeredPaths.contains(tempFile));
    }
    
    @Test
    void shouldCreateTempDirectory() throws IOException {
        // When
        Path tempDir = tempFileManager.createTempDirectory("testdir");
        
        // Then
        assertNotNull(tempDir);
        assertTrue(Files.exists(tempDir));
        assertTrue(Files.isDirectory(tempDir));
        assertTrue(tempDir.getFileName().toString().startsWith("testdir"));
        
        // Verify it's registered for cleanup
        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertTrue(registeredPaths.contains(tempDir));
    }
    
    @Test
    void shouldRegisterPathForCleanup() throws IOException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "manual", ".tmp");
        
        // When
        tempFileManager.registerForCleanup(testFile);
        
        // Then
        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertTrue(registeredPaths.contains(testFile));
    }
    
    @Test
    void shouldNotRegisterNullPath() {
        // When
        tempFileManager.registerForCleanup(null);
        
        // Then
        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertTrue(registeredPaths.isEmpty());
    }
    
    @Test
    void shouldNotRegisterDuplicatePaths() throws IOException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "duplicate", ".tmp");
        
        // When
        tempFileManager.registerForCleanup(testFile);
        tempFileManager.registerForCleanup(testFile);
        
        // Then
        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertEquals(1, registeredPaths.size());
        assertTrue(registeredPaths.contains(testFile));
    }
    
    @Test
    void shouldCleanupRegisteredFiles() throws IOException {
        // Given
        Path tempFile1 = tempFileManager.createTempFile("cleanup1", ".tmp");
        Path tempFile2 = tempFileManager.createTempFile("cleanup2", ".tmp");
        
        assertTrue(Files.exists(tempFile1));
        assertTrue(Files.exists(tempFile2));
        
        // When
        tempFileManager.cleanup();
        
        // Then
        assertFalse(Files.exists(tempFile1));
        assertFalse(Files.exists(tempFile2));
        assertTrue(tempFileManager.getRegisteredPaths().isEmpty());
    }
    
    @Test
    void shouldCleanupRegisteredDirectories() throws IOException {
        // Given
        Path tempDir = tempFileManager.createTempDirectory("cleanupdir");
        Path fileInDir = Files.createTempFile(tempDir, "file", ".tmp");
        Path subDir = Files.createTempDirectory(tempDir, "subdir");
        Path fileInSubDir = Files.createTempFile(subDir, "subfile", ".tmp");
        
        assertTrue(Files.exists(tempDir));
        assertTrue(Files.exists(fileInDir));
        assertTrue(Files.exists(subDir));
        assertTrue(Files.exists(fileInSubDir));
        
        // When
        tempFileManager.cleanup();
        
        // Then
        assertFalse(Files.exists(tempDir));
        assertFalse(Files.exists(fileInDir));
        assertFalse(Files.exists(subDir));
        assertFalse(Files.exists(fileInSubDir));
    }
    
    @Test
    void shouldHandleCleanupOfNonExistentFiles() throws IOException {
        // Given
        Path tempFile = tempFileManager.createTempFile("nonexistent", ".tmp");
        Files.delete(tempFile); // Delete manually before cleanup
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> tempFileManager.cleanup());
    }
    
    @Test
    void shouldCloseAndCleanupAutomatically() throws IOException {
        // Given
        Path tempFile = tempFileManager.createTempFile("autoclose", ".tmp");
        assertTrue(Files.exists(tempFile));
        
        // When
        tempFileManager.close();
        
        // Then
        assertFalse(Files.exists(tempFile));
    }
    
    @Test
    void shouldWorkWithTryWithResources() throws IOException {
        Path tempFile;
        
        // Given & When
        try (TempFileManager manager = new TempFileManagerImpl()) {
            tempFile = manager.createTempFile("trywith", ".tmp");
            assertTrue(Files.exists(tempFile));
        }
        
        // Then - file should be cleaned up automatically
        assertFalse(Files.exists(tempFile));
    }
    
    @Test
    void shouldThrowExceptionWhenUsedAfterClose() throws IOException {
        // Given
        tempFileManager.close();
        
        // When & Then
        assertThrows(IllegalStateException.class, 
            () -> tempFileManager.createTempFile("afterclose", ".tmp"));
        
        assertThrows(IllegalStateException.class, 
            () -> tempFileManager.createTempDirectory("afterclose"));
    }
    
    @Test
    void shouldHandleMultipleCloseCallsGracefully() {
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            tempFileManager.close();
            tempFileManager.close();
            tempFileManager.close();
        });
    }
    
    @Test
    void shouldReturnUnmodifiableListOfRegisteredPaths() throws IOException {
        // Given
        Path tempFile = tempFileManager.createTempFile("readonly", ".tmp");
        
        // When
        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        
        // Then
        assertThrows(UnsupportedOperationException.class, 
            () -> registeredPaths.add(tempFile));
    }
    
    @Test
    void shouldWarnWhenRegisteringAfterClose() throws IOException {
        // Given
        Path testFile = Files.createTempFile(tempDirectory, "afterclose", ".tmp");
        tempFileManager.close();
        
        // When - should not throw but should warn (tested via logs)
        assertDoesNotThrow(() -> tempFileManager.registerForCleanup(testFile));
        
        // Then - path should not be registered
        assertTrue(tempFileManager.getRegisteredPaths().isEmpty());
        
        // Cleanup manually since it wasn't registered
        Files.deleteIfExists(testFile);
    }
}