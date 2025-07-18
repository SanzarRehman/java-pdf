package com.bracits.easyJavaPdf.util;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.exception.ValidationException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Validator for PDF generation requests containing business logic validation
 * and cross-field validation rules specific to PDF generation operations.
 */
public final class PdfGenerationRequestValidator {

    // Password constraints
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 128;
    
    // Asset file limits
    private static final int MAX_ASSET_FILES = 50;

    private PdfGenerationRequestValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates a complete PDF generation request with all business rules.
     *
     * @param request the PDF generation request to validate
     * @throws ValidationException if any validation rule is violated
     */
    public static void validate(PdfGenerationRequest request) {
        if (request == null) {
            throw new ValidationException("PDF generation request cannot be null");
        }

        validateHtmlFile(request.getHtmlFile());
        validateOptionalCssFile(request.getCssFile());
        validateOptionalHeaderFile(request.getHeaderFile());
        validateOptionalFooterFile(request.getFooterFile());
        validateOptionalBanglaFooterFile(request.getBanglaFooter());
        validateAssetFiles(request.getAssets());
        validatePassword(request.getPassword());
        validateJsEnabledFlag(request.isJsEnabled());
        
        // Cross-field validations
        validateHeaderFooterConsistency(request);
        validateAssetFilesWithHtmlContent(request);
    }

    /**
     * Validates the required HTML file.
     *
     * @param htmlFile the HTML file to validate
     * @throws ValidationException if the HTML file is invalid
     */
    private static void validateHtmlFile(MultipartFile htmlFile) {
        FileValidator.validateFileNotEmpty(htmlFile, "HTML file");
        FileValidator.validateHtmlFile(htmlFile, "HTML file");
        
        // Additional business logic validation for HTML files
        String filename = htmlFile.getOriginalFilename();
        if (filename != null && filename.length() > 255) {
            throw new ValidationException("HTML filename is too long (maximum 255 characters)");
        }
    }

    /**
     * Validates the optional CSS file.
     *
     * @param cssFile the CSS file to validate (can be null)
     * @throws ValidationException if the CSS file is invalid
     */
    private static void validateOptionalCssFile(MultipartFile cssFile) {
        if (cssFile != null && !cssFile.isEmpty()) {
            FileValidator.validateCssFile(cssFile, "CSS file");
            
            // Additional business logic validation
            String filename = cssFile.getOriginalFilename();
            if (filename != null && filename.length() > 255) {
                throw new ValidationException("CSS filename is too long (maximum 255 characters)");
            }
        }
    }

    /**
     * Validates the optional header file.
     *
     * @param headerFile the header file to validate (can be null)
     * @throws ValidationException if the header file is invalid
     */
    private static void validateOptionalHeaderFile(MultipartFile headerFile) {
        if (headerFile != null && !headerFile.isEmpty()) {
            FileValidator.validateHtmlFile(headerFile, "Header file");
            
            // Business rule: Header files should be relatively small
            if (headerFile.getSize() > 1024 * 1024) { // 1MB limit for headers
                throw new ValidationException("Header file size should not exceed 1MB");
            }
        }
    }

    /**
     * Validates the optional footer file.
     *
     * @param footerFile the footer file to validate (can be null)
     * @throws ValidationException if the footer file is invalid
     */
    private static void validateOptionalFooterFile(MultipartFile footerFile) {
        if (footerFile != null && !footerFile.isEmpty()) {
            FileValidator.validateHtmlFile(footerFile, "Footer file");
            
            // Business rule: Footer files should be relatively small
            if (footerFile.getSize() > 1024 * 1024) { // 1MB limit for footers
                throw new ValidationException("Footer file size should not exceed 1MB");
            }
        }
    }

    /**
     * Validates the optional Bengali footer file.
     *
     * @param banglaFooter the Bengali footer file to validate (can be null)
     * @throws ValidationException if the Bengali footer file is invalid
     */
    private static void validateOptionalBanglaFooterFile(MultipartFile banglaFooter) {
        if (banglaFooter != null && !banglaFooter.isEmpty()) {
            FileValidator.validateHtmlFile(banglaFooter, "Bengali footer file");
            
            // Business rule: Bengali footer files should be relatively small
            if (banglaFooter.getSize() > 1024 * 1024) { // 1MB limit for Bengali footers
                throw new ValidationException("Bengali footer file size should not exceed 1MB");
            }
        }
    }

    /**
     * Validates the optional asset files (fonts, images, etc.).
     *
     * @param assets the list of asset files to validate (can be null)
     * @throws ValidationException if any asset file is invalid
     */
    private static void validateAssetFiles(List<MultipartFile> assets) {
        if (assets == null || assets.isEmpty()) {
            return; // Assets are optional
        }

        // Business rule: Limit the number of asset files
        if (assets.size() > MAX_ASSET_FILES) {
            throw new ValidationException(
                    "Too many asset files. Maximum allowed: " + MAX_ASSET_FILES + 
                    ", provided: " + assets.size()
            );
        }

        // Validate each asset file
        FileValidator.validateAssetFiles(assets);

        // Additional business logic: Check for duplicate filenames
        validateNoDuplicateAssetFilenames(assets);
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
        
        // Business rules for password
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
     * Validates the JavaScript enabled flag.
     *
     * @param jsEnabled the JavaScript enabled flag
     * @throws ValidationException if the flag configuration is invalid
     */
    private static void validateJsEnabledFlag(boolean jsEnabled) {
        // Currently no specific validation needed for boolean flag
        // This method is here for future business rules if needed
        // For example, we might want to restrict JS execution based on file size or other factors
    }

    /**
     * Validates consistency between header and footer files.
     *
     * @param request the PDF generation request
     * @throws ValidationException if header/footer configuration is inconsistent
     */
    private static void validateHeaderFooterConsistency(PdfGenerationRequest request) {
        MultipartFile footerFile = request.getFooterFile();
        MultipartFile banglaFooter = request.getBanglaFooter();
        
        // Business rule: Cannot have both regular footer and Bengali footer
        if (footerFile != null && !footerFile.isEmpty() && 
            banglaFooter != null && !banglaFooter.isEmpty()) {
            throw new ValidationException(
                    "Cannot specify both regular footer and Bengali footer. Please choose one"
            );
        }
    }

    /**
     * Validates asset files in relation to HTML content requirements.
     *
     * @param request the PDF generation request
     * @throws ValidationException if asset configuration is invalid
     */
    private static void validateAssetFilesWithHtmlContent(PdfGenerationRequest request) {
        List<MultipartFile> assets = request.getAssets();
        
        if (assets == null || assets.isEmpty()) {
            return; // No assets to validate
        }

        // Business rule: If JavaScript is enabled and we have many assets, 
        // it might cause performance issues
        if (request.isJsEnabled() && assets.size() > 20) {
            throw new ValidationException(
                    "When JavaScript is enabled, maximum 20 asset files are allowed for performance reasons. " +
                    "Current count: " + assets.size()
            );
        }

        // Calculate total asset size for performance validation
        long totalAssetSize = assets.stream()
                .filter(asset -> asset != null && !asset.isEmpty())
                .mapToLong(MultipartFile::getSize)
                .sum();

        // Business rule: Total asset size should not exceed 50MB
        long maxTotalAssetSize = 50 * 1024 * 1024; // 50MB
        if (totalAssetSize > maxTotalAssetSize) {
            throw new ValidationException(
                    "Total size of all asset files exceeds maximum allowed size. " +
                    "Maximum: " + formatFileSize(maxTotalAssetSize) + 
                    ", Current: " + formatFileSize(totalAssetSize)
            );
        }
    }

    /**
     * Validates that there are no duplicate filenames in asset files.
     *
     * @param assets the list of asset files
     * @throws ValidationException if duplicate filenames are found
     */
    private static void validateNoDuplicateAssetFilenames(List<MultipartFile> assets) {
        java.util.Set<String> filenames = new java.util.HashSet<>();
        
        for (int i = 0; i < assets.size(); i++) {
            MultipartFile asset = assets.get(i);
            if (asset != null && !asset.isEmpty()) {
                String filename = asset.getOriginalFilename();
                if (filename != null) {
                    String normalizedFilename = filename.toLowerCase().trim();
                    if (!filenames.add(normalizedFilename)) {
                        throw new ValidationException(
                                "Duplicate asset filename found: '" + filename + "'. " +
                                "All asset files must have unique names"
                        );
                    }
                }
            }
        }
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