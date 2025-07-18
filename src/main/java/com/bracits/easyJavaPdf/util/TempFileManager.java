package com.bracits.easyJavaPdf.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for managing temporary file lifecycle with automatic cleanup support.
 * Implements AutoCloseable to support try-with-resources pattern.
 */
public interface TempFileManager extends AutoCloseable {
    
    /**
     * Creates a temporary file with the specified prefix and suffix.
     * The file will be automatically cleaned up when the manager is closed.
     *
     * @param prefix the prefix string to be used in generating the file's name
     * @param suffix the suffix string to be used in generating the file's name
     * @return the path to the created temporary file
     * @throws IOException if an I/O error occurs
     */
    Path createTempFile(String prefix, String suffix) throws IOException;
    
    /**
     * Creates a temporary directory with the specified prefix.
     * The directory and its contents will be automatically cleaned up when the manager is closed.
     *
     * @param prefix the prefix string to be used in generating the directory's name
     * @return the path to the created temporary directory
     * @throws IOException if an I/O error occurs
     */
    Path createTempDirectory(String prefix) throws IOException;
    
    /**
     * Registers an existing file or directory for cleanup when the manager is closed.
     *
     * @param path the path to register for cleanup
     */
    void registerForCleanup(Path path);
    
    /**
     * Gets all paths currently registered for cleanup.
     *
     * @return a list of paths that will be cleaned up
     */
    List<Path> getRegisteredPaths();
    
    /**
     * Manually cleans up all registered temporary files and directories.
     * This method is called automatically when the manager is closed.
     *
     * @throws IOException if an I/O error occurs during cleanup
     */
    void cleanup() throws IOException;
    
    /**
     * Closes the manager and cleans up all registered temporary files and directories.
     * This method is called automatically in try-with-resources blocks.
     */
    @Override
    void close();
}