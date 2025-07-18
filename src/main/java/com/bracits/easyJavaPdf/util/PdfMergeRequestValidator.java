package com.bracits.easyJavaPdf.util;

import com.bracits.easyJavaPdf.dto.PdfMergeRequest;
import com.bracits.easyJavaPdf.exception.ValidationException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator for PDF merge requests containing business logic validation
 * and cross-field validation rules specific to PDF merging operations.
 */
public final class PdfMergeRequestValidator {

    // Password constraints
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 128;
    
    // File limits
    private static final int MAX_MERGE_FILES = 100;
    private static final int MIN_MERGE_FILES = 1;
    
    // Filename constraints
    private static final int MAX_FILENAME_LENGTH = 255;
    
    // Page range patterns - more permissive to allow various formats
    private static final Pattern PAGE_RANGE_PATTERN = Pattern.compile(
        "^(\\[.*\\]|[\\w\\s~:,.\\-]+)$"
    );
    
    // Simple page range syntax patterns - allow dots in filenames
    private static final Pattern SIMPLE_RANGE_PATTERN = Pattern.compile(
        "^[\\w.\\-]+(?:~[\\d:,\\-]*)?(?:\\s+[\\w.\\-]+(?:~[\\d:,\\-]*)?)*$"
    );
    
    // JSON page range pattern
    private static final Pattern JSON_RANGE_PATTERN = Pattern.compile(
        "^\\[\\s*\\{.*\\}\\s*(?:,\\s*\\{.*\\}\\s*)*\\]$"
    );

    private PdfMergeRequestValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates a complete PDF merge request with all business rules.
     *
     * @param request the PDF merge request to validate
     * @throws ValidationException if any validation rule is violated
     */
    public static void validate(PdfMergeRequest request) {
        if (request == null) {
            throw new ValidationException("PDF merge request cannot be null");
        }

        validateFiles(request.getFiles());
        validatePagesDefinition(request.getPagesDefinition());
        validateDisposition(request.getDisposition());
        validateFileName(request.getFileName());
        validatePassword(request.getPassword());
        validateResourceOptimizer(request.isResourceOptimizer());
        
        // Cross-field validations
        validateFilesWithPageRanges(request);
        validateResourceOptimizerWithFileCount(request);
    }

    /**
     * Validates the list of files to be merged.
     *
     * @param files the list of files to validate
     * @throws ValidationException if the files are invalid
     */
    private static void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException("At least one file is required for merging");
        }

        // Business rule: Limit the number of files that can be merged
        if (files.size() > MAX_MERGE_FILES) {
            throw new ValidationException(
                    "Too many files for merging. Maximum allowed: " + MAX_MERGE_FILES + 
                    ", provided: " + files.size()
            );
        }

        if (files.size() < MIN_MERGE_FILES) {
            throw new ValidationException(
                    "At least " + MIN_MERGE_FILES + " file is required for merging"
            );
        }

        // Validate each file using FileValidator
        FileValidator.validateMergeFiles(files);

        // Additional business logic validations
        validateNoDuplicateFilenames(files);
        validateTotalFilesSize(files);
    }

    /**
     * Validates the page range definition string.
     *
     * @param pagesDefinition the page range definition to validate
     * @throws ValidationException if the page definition is invalid
     */
    private static void validatePagesDefinition(String pagesDefinition) {
        if (pagesDefinition == null || pagesDefinition.trim().isEmpty()) {
            return; // Page definition is optional
        }

        String trimmedPages = pagesDefinition.trim();
        
        // Business rule: Page definition should not be too long
        if (trimmedPages.length() > 1000) {
            throw new ValidationException(
                    "Page definition is too long. Maximum 1000 characters allowed"
            );
        }

        // Basic format validation - just check for obviously invalid characters
        if (trimmedPages.contains("<") || trimmedPages.contains(">") || 
            trimmedPages.contains("&") || trimmedPages.contains("|")) {
            throw new ValidationException(
                    "Invalid page range format. Use either JSON format [{'file':'name','range':'1:3'}] " +
                    "or simple format 'file1~1:3 file2~2:5'"
            );
        }

        // Additional validation based on format type
        if (trimmedPages.startsWith("[")) {
            validateJsonPageRangeFormat(trimmedPages);
        } else {
            validateSimplePageRangeFormat(trimmedPages);
        }
    }

    /**
     * Validates the content disposition parameter.
     *
     * @param disposition the disposition to validate
     * @throws ValidationException if the disposition is invalid
     */
    private static void validateDisposition(String disposition) {
        if (disposition == null || disposition.trim().isEmpty()) {
            return; // Will use default value
        }

        String trimmedDisposition = disposition.trim().toLowerCase();
        
        if (!trimmedDisposition.equals("inline") && !trimmedDisposition.equals("attachment")) {
            throw new ValidationException(
                    "Invalid disposition. Must be either 'inline' or 'attachment'"
            );
        }
    }

    /**
     * Validates the output filename.
     *
     * @param fileName the filename to validate
     * @throws ValidationException if the filename is invalid
     */
    private static void validateFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return; // Will use default value
        }

        String trimmedFileName = fileName.trim();
        
        // Business rule: Filename length limit
        if (trimmedFileName.length() > MAX_FILENAME_LENGTH) {
            throw new ValidationException(
                    "Filename is too long. Maximum " + MAX_FILENAME_LENGTH + " characters allowed"
            );
        }

        // Business rule: Filename should end with .pdf
        if (!trimmedFileName.toLowerCase().endsWith(".pdf")) {
            throw new ValidationException(
                    "Filename must end with .pdf extension"
            );
        }

        // Business rule: Filename should not contain invalid characters
        if (containsInvalidFilenameCharacters(trimmedFileName)) {
            throw new ValidationException(
                    "Filename contains invalid characters. Use only alphanumeric characters, " +
                    "hyphens, underscores, and dots"
            );
        }
    }

    /**
     * Validates the optional password parameter.
     *
     * @param password the password to validate (can be null)
     * @throws ValidationException if the password is invalid
     */
    private static void validatePassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return; // Password is optional
        }

        String trimmedPassword = password.trim();
        
        // Business rules for password (same as PDF generation)
        if (trimmedPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "Password is too short. Minimum length: " + MIN_PASSWORD_LENGTH + " characters"
            );
        }
        
        if (trimmedPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "Password is too long. Maximum length: " + MAX_PASSWORD_LENGTH + " characters"
            );
        }

        // Check for invalid characters that might cause issues with PDF encryption
        if (containsInvalidPasswordCharacters(trimmedPassword)) {
            throw new ValidationException(
                    "Password contains invalid characters. Use only alphanumeric characters and common symbols"
            );
        }
    }

    /**
     * Validates the resource optimizer flag.
     *
     * @param resourceOptimizer the resource optimizer flag
     * @throws ValidationException if the flag configuration is invalid
     */
    private static void validateResourceOptimizer(boolean resourceOptimizer) {
        // Currently no specific validation needed for boolean flag
        // This method is here for future business rules if needed
    }

    /**
     * Validates files in relation to page range definitions.
     *
     * @param request the PDF merge request
     * @throws ValidationException if the configuration is inconsistent
     */
    private static void validateFilesWithPageRanges(PdfMergeRequest request) {
        String pagesDefinition = request.getPagesDefinition();
        List<MultipartFile> files = request.getFiles();
        
        if (pagesDefinition == null || pagesDefinition.trim().isEmpty()) {
            return; // No page ranges to validate
        }

        // Business rule: If page ranges are specified, validate they don't exceed file count
        if (pagesDefinition.startsWith("[")) {
            // JSON format - basic validation (detailed validation would require parsing)
            validateJsonPageRangeConsistency(pagesDefinition, files);
        } else {
            // Simple format - validate file references
            validateSimplePageRangeConsistency(pagesDefinition, files);
        }
    }

    /**
     * Validates resource optimizer setting with file count.
     *
     * @param request the PDF merge request
     * @throws ValidationException if the configuration is suboptimal
     */
    private static void validateResourceOptimizerWithFileCount(PdfMergeRequest request) {
        List<MultipartFile> files = request.getFiles();
        boolean resourceOptimizer = request.isResourceOptimizer();
        
        // Business rule: Recommend resource optimizer for large merges
        if (files.size() > 20 && !resourceOptimizer) {
            // This is a warning-level validation - we could log this instead of throwing
            // For now, we'll allow it but could add logging in the future
        }

        // Business rule: Resource optimizer might not be beneficial for small merges
        if (files.size() == 1 && resourceOptimizer) {
            throw new ValidationException(
                    "Resource optimizer is not beneficial when merging only one file"
            );
        }
    }

    /**
     * Validates that there are no duplicate filenames in the files list.
     *
     * @param files the list of files
     * @throws ValidationException if duplicate filenames are found
     */
    private static void validateNoDuplicateFilenames(List<MultipartFile> files) {
        java.util.Set<String> filenames = new java.util.HashSet<>();
        
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file != null && !file.isEmpty()) {
                String filename = file.getOriginalFilename();
                if (filename != null) {
                    String normalizedFilename = filename.toLowerCase().trim();
                    if (!filenames.add(normalizedFilename)) {
                        throw new ValidationException(
                                "Duplicate filename found: '" + filename + "'. " +
                                "All files must have unique names for merging"
                        );
                    }
                }
            }
        }
    }

    /**
     * Validates the total size of all files to be merged.
     *
     * @param files the list of files
     * @throws ValidationException if the total size exceeds limits
     */
    private static void validateTotalFilesSize(List<MultipartFile> files) {
        long totalSize = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .mapToLong(MultipartFile::getSize)
                .sum();

        // Business rule: Total size limit for merge operations (100MB)
        long maxTotalSize = 100 * 1024 * 1024; // 100MB
        if (totalSize > maxTotalSize) {
            throw new ValidationException(
                    "Total size of all files exceeds maximum allowed size for merging. " +
                    "Maximum: " + formatFileSize(maxTotalSize) + 
                    ", Current: " + formatFileSize(totalSize)
            );
        }
    }

    /**
     * Validates JSON page range format.
     *
     * @param pagesDefinition the JSON page range definition
     * @throws ValidationException if the format is invalid
     */
    private static void validateJsonPageRangeFormat(String pagesDefinition) {
        if (!JSON_RANGE_PATTERN.matcher(pagesDefinition).matches()) {
            throw new ValidationException(
                    "Invalid JSON page range format. Expected format: " +
                    "[{\"file\":\"filename\",\"range\":\"1:3\"},{\"file\":\"filename2\",\"range\":\"2:5\"}]"
            );
        }
    }

    /**
     * Validates simple page range format.
     *
     * @param pagesDefinition the simple page range definition
     * @throws ValidationException if the format is invalid
     */
    private static void validateSimplePageRangeFormat(String pagesDefinition) {
        // More lenient validation - just check for basic structure
        String[] tokens = pagesDefinition.trim().split("\\s+");
        for (String token : tokens) {
            if (token.trim().isEmpty()) {
                continue;
            }
            // Basic validation - ensure token doesn't contain obviously invalid characters
            if (token.contains("<") || token.contains(">") || token.contains("&")) {
                throw new ValidationException(
                        "Invalid simple page range format. Expected format: " +
                        "'file1~1:3 file2~2:5' or 'file1 file2' (for full files)"
                );
            }
        }
    }

    /**
     * Validates JSON page range consistency with files.
     *
     * @param pagesDefinition the JSON page range definition
     * @param files the list of files
     * @throws ValidationException if inconsistencies are found
     */
    private static void validateJsonPageRangeConsistency(String pagesDefinition, List<MultipartFile> files) {
        // Basic validation - count braces to ensure it's not malformed
        long openBraces = pagesDefinition.chars().filter(ch -> ch == '{').count();
        long closeBraces = pagesDefinition.chars().filter(ch -> ch == '}').count();
        
        if (openBraces != closeBraces) {
            throw new ValidationException(
                    "Malformed JSON page range definition. Mismatched braces"
            );
        }

        // Business rule: JSON should not reference more files than provided
        if (openBraces > files.size()) {
            throw new ValidationException(
                    "Page range definition references more files (" + openBraces + 
                    ") than provided (" + files.size() + ")"
            );
        }
    }

    /**
     * Validates simple page range consistency with files.
     *
     * @param pagesDefinition the simple page range definition
     * @param files the list of files
     * @throws ValidationException if inconsistencies are found
     */
    private static void validateSimplePageRangeConsistency(String pagesDefinition, List<MultipartFile> files) {
        String[] tokens = pagesDefinition.trim().split("\\s+");
        
        // Filter out empty tokens
        long nonEmptyTokens = Arrays.stream(tokens)
                .filter(token -> !token.trim().isEmpty())
                .count();
        
        // Business rule: Should not reference more files than provided
        if (nonEmptyTokens > files.size()) {
            throw new ValidationException(
                    "Page range definition references more files (" + nonEmptyTokens + 
                    ") than provided (" + files.size() + ")"
            );
        }
    }

    /**
     * Checks if the filename contains invalid characters.
     *
     * @param filename the filename to check
     * @return true if the filename contains invalid characters
     */
    private static boolean containsInvalidFilenameCharacters(String filename) {
        // Allow alphanumeric characters, hyphens, underscores, dots, and spaces
        String allowedPattern = "^[a-zA-Z0-9._\\-\\s]+$";
        return !filename.matches(allowedPattern);
    }

    /**
     * Checks if the password contains invalid characters.
     *
     * @param password the password to check
     * @return true if the password contains invalid characters
     */
    private static boolean containsInvalidPasswordCharacters(String password) {
        // Allow alphanumeric characters and common symbols
        // Exclude characters that might cause issues with PDF encryption
        String allowedPattern = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\",./<>?|`~\\s]*$";
        return !password.matches(allowedPattern);
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param size the file size in bytes
     * @return formatted file size string
     */
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " bytes";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}