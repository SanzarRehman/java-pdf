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

        Path tempFile = tempFileManager.createTempFile("test", ".tmp");
        

        assertNotNull(tempFile);
        assertTrue(Files.exists(tempFile));
        assertTrue(tempFile.getFileName().toString().startsWith("test"));
        assertTrue(tempFile.getFileName().toString().endsWith(".tmp"));
        

        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertTrue(registeredPaths.contains(tempFile));
    }
    
    @Test
    void shouldCreateTempDirectory() throws IOException {

        Path tempDir = tempFileManager.createTempDirectory("testdir");
        

        assertNotNull(tempDir);
        assertTrue(Files.exists(tempDir));
        assertTrue(Files.isDirectory(tempDir));
        assertTrue(tempDir.getFileName().toString().startsWith("testdir"));
        

        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertTrue(registeredPaths.contains(tempDir));
    }
    
    @Test
    void shouldRegisterPathForCleanup() throws IOException {

        Path testFile = Files.createTempFile(tempDirectory, "manual", ".tmp");
        

        tempFileManager.registerForCleanup(testFile);
        

        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertTrue(registeredPaths.contains(testFile));
    }
    
    @Test
    void shouldNotRegisterNullPath() {

        tempFileManager.registerForCleanup(null);
        

        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertTrue(registeredPaths.isEmpty());
    }
    
    @Test
    void shouldNotRegisterDuplicatePaths() throws IOException {

        Path testFile = Files.createTempFile(tempDirectory, "duplicate", ".tmp");
        

        tempFileManager.registerForCleanup(testFile);
        tempFileManager.registerForCleanup(testFile);
        

        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        assertEquals(1, registeredPaths.size());
        assertTrue(registeredPaths.contains(testFile));
    }
    
    @Test
    void shouldCleanupRegisteredFiles() throws IOException {

        Path tempFile1 = tempFileManager.createTempFile("cleanup1", ".tmp");
        Path tempFile2 = tempFileManager.createTempFile("cleanup2", ".tmp");
        
        assertTrue(Files.exists(tempFile1));
        assertTrue(Files.exists(tempFile2));
        

        tempFileManager.cleanup();
        

        assertFalse(Files.exists(tempFile1));
        assertFalse(Files.exists(tempFile2));
        assertTrue(tempFileManager.getRegisteredPaths().isEmpty());
    }
    
    @Test
    void shouldCleanupRegisteredDirectories() throws IOException {

        Path tempDir = tempFileManager.createTempDirectory("cleanupdir");
        Path fileInDir = Files.createTempFile(tempDir, "file", ".tmp");
        Path subDir = Files.createTempDirectory(tempDir, "subdir");
        Path fileInSubDir = Files.createTempFile(subDir, "subfile", ".tmp");
        
        assertTrue(Files.exists(tempDir));
        assertTrue(Files.exists(fileInDir));
        assertTrue(Files.exists(subDir));
        assertTrue(Files.exists(fileInSubDir));
        

        tempFileManager.cleanup();
        

        assertFalse(Files.exists(tempDir));
        assertFalse(Files.exists(fileInDir));
        assertFalse(Files.exists(subDir));
        assertFalse(Files.exists(fileInSubDir));
    }
    
    @Test
    void shouldHandleCleanupOfNonExistentFiles() throws IOException {

        Path tempFile = tempFileManager.createTempFile("nonexistent", ".tmp");
        Files.delete(tempFile);
        

        assertDoesNotThrow(() -> tempFileManager.cleanup());
    }
    
    @Test
    void shouldCloseAndCleanupAutomatically() throws IOException {

        Path tempFile = tempFileManager.createTempFile("autoclose", ".tmp");
        assertTrue(Files.exists(tempFile));
        

        tempFileManager.close();
        

        assertFalse(Files.exists(tempFile));
    }
    
    @Test
    void shouldWorkWithTryWithResources() throws IOException {
        Path tempFile;
        

        try (TempFileManager manager = new TempFileManagerImpl()) {
            tempFile = manager.createTempFile("trywith", ".tmp");
            assertTrue(Files.exists(tempFile));
        }
        

        assertFalse(Files.exists(tempFile));
    }
    
    @Test
    void shouldThrowExceptionWhenUsedAfterClose() throws IOException {

        tempFileManager.close();
        

        assertThrows(IllegalStateException.class, 
            () -> tempFileManager.createTempFile("afterclose", ".tmp"));
        
        assertThrows(IllegalStateException.class, 
            () -> tempFileManager.createTempDirectory("afterclose"));
    }
    
    @Test
    void shouldHandleMultipleCloseCallsGracefully() {

        assertDoesNotThrow(() -> {
            tempFileManager.close();
            tempFileManager.close();
            tempFileManager.close();
        });
    }
    
    @Test
    void shouldReturnUnmodifiableListOfRegisteredPaths() throws IOException {

        Path tempFile = tempFileManager.createTempFile("readonly", ".tmp");
        

        List<Path> registeredPaths = tempFileManager.getRegisteredPaths();
        

        assertThrows(UnsupportedOperationException.class, 
            () -> registeredPaths.add(tempFile));
    }
    
    @Test
    void shouldWarnWhenRegisteringAfterClose() throws IOException {

        Path testFile = Files.createTempFile(tempDirectory, "afterclose", ".tmp");
        tempFileManager.close();
        

        assertDoesNotThrow(() -> tempFileManager.registerForCleanup(testFile));
        

        assertTrue(tempFileManager.getRegisteredPaths().isEmpty());
        

        Files.deleteIfExists(testFile);
    }
}