package com.bracits.easyJavaPdf.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of TempFileManager that provides automatic cleanup
 * of temporary files and directories using try-with-resources pattern.
 */
@Component
public class TempFileManagerImpl implements TempFileManager {
    
    private static final Logger logger = LoggerFactory.getLogger(TempFileManagerImpl.class);
    
    private final List<Path> registeredPaths = new CopyOnWriteArrayList<>();
    private volatile boolean closed = false;
    
    @Override
    public Path createTempFile(String prefix, String suffix) throws IOException {
        if (closed) {
            throw new IllegalStateException("TempFileManager has been closed");
        }
        
        Path tempFile = Files.createTempFile(prefix, suffix);
        registerForCleanup(tempFile);
        
        logger.debug("Created temporary file: {}", tempFile);
        return tempFile;
    }
    
    @Override
    public Path createTempDirectory(String prefix) throws IOException {
        if (closed) {
            throw new IllegalStateException("TempFileManager has been closed");
        }
        
        Path tempDir = Files.createTempDirectory(prefix);
        registerForCleanup(tempDir);
        
        logger.debug("Created temporary directory: {}", tempDir);
        return tempDir;
    }
    
    @Override
    public void registerForCleanup(Path path) {
        if (closed) {
            logger.warn("Attempted to register path for cleanup after manager was closed: {}", path);
            return;
        }
        
        if (path != null && !registeredPaths.contains(path)) {
            registeredPaths.add(path);
            logger.debug("Registered path for cleanup: {}", path);
        }
    }
    
    @Override
    public List<Path> getRegisteredPaths() {
        return Collections.unmodifiableList(new ArrayList<>(registeredPaths));
    }
    
    @Override
    public void cleanup() throws IOException {
        List<IOException> exceptions = new ArrayList<>();
        
        for (Path path : registeredPaths) {
            try {
                if (Files.exists(path)) {
                    if (Files.isDirectory(path)) {
                        deleteDirectoryRecursively(path);
                        logger.debug("Cleaned up temporary directory: {}", path);
                    } else {
                        Files.delete(path);
                        logger.debug("Cleaned up temporary file: {}", path);
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to cleanup path: {}", path, e);
                exceptions.add(e);
            }
        }
        
        registeredPaths.clear();
        
        if (!exceptions.isEmpty()) {
            IOException combinedException = new IOException("Failed to cleanup some temporary files");
            exceptions.forEach(combinedException::addSuppressed);
            throw combinedException;
        }
        
        logger.debug("Cleanup completed for {} paths", registeredPaths.size());
    }
    
    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        closed = true;
        
        try {
            cleanup();
            logger.debug("TempFileManager closed successfully");
        } catch (IOException e) {
            logger.error("Error during TempFileManager cleanup", e);
        }
    }
    
    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param directory the directory to delete
     * @throws IOException if an I/O error occurs
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        
        Files.walk(directory)
                .sorted((path1, path2) -> path2.compareTo(path1)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete path during recursive cleanup: {}", path, e);
                    }
                });
    }
}