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


    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 128;
    

    private static final int MAX_ASSET_FILES = 50;

    private PdfGenerationRequestValidator() {

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

        validateHtmlInput(request);
        validateCssInput(request);
        validateOptionalHeaderFile(request.getHeaderFile());
        validateOptionalFooterFile(request.getFooterFile());
        validateOptionalBanglaFooterFile(request.getBanglaFooter());
        validateAssetFiles(request.getAssets());
        validatePassword(request.getPassword());
        validateJsEnabledFlag(request.isJsEnabled());
        

        validateHeaderFooterConsistency(request);
        validateAssetFilesWithHtmlContent(request);
    }

    /**
     * Validates HTML input (either file or content string).
     *
     * @param request the PDF generation request
     * @throws ValidationException if HTML input is invalid
     */
    private static void validateHtmlInput(PdfGenerationRequest request) {
        MultipartFile htmlFile = request.getHtmlFile();
        String htmlContent = request.getHtmlContent();
        
        // Either htmlFile or htmlContent must be provided, but not both
        if ((htmlFile == null || htmlFile.isEmpty()) && (htmlContent == null || htmlContent.trim().isEmpty())) {
            throw new ValidationException("Either HTML file or HTML content must be provided");
        }
        
        if (htmlFile != null && !htmlFile.isEmpty() && htmlContent != null && !htmlContent.trim().isEmpty()) {
            throw new ValidationException("Cannot provide both HTML file and HTML content. Please choose one");
        }
        
        // Validate HTML file if provided
        if (htmlFile != null && !htmlFile.isEmpty()) {
            validateHtmlFile(htmlFile);
        }
        
        // Validate HTML content if provided
        if (htmlContent != null && !htmlContent.trim().isEmpty()) {
            validateHtmlContent(htmlContent);
        }
    }

    /**
     * Validates CSS input (either file or content string).
     *
     * @param request the PDF generation request
     * @throws ValidationException if CSS input is invalid
     */
    private static void validateCssInput(PdfGenerationRequest request) {
        MultipartFile cssFile = request.getCssFile();
        String cssContent = request.getCssContent();
        
        // Both cssFile and cssContent are optional, but if both are provided, that's an error
        if (cssFile != null && !cssFile.isEmpty() && cssContent != null && !cssContent.trim().isEmpty()) {
            throw new ValidationException("Cannot provide both CSS file and CSS content. Please choose one");
        }
        
        // Validate CSS file if provided
        if (cssFile != null && !cssFile.isEmpty()) {
            validateOptionalCssFile(cssFile);
        }
        
        // Validate CSS content if provided
        if (cssContent != null && !cssContent.trim().isEmpty()) {
            validateCssContent(cssContent);
        }
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
        

        String filename = htmlFile.getOriginalFilename();
        if (filename != null && filename.length() > 255) {
            throw new ValidationException("HTML filename is too long (maximum 255 characters)");
        }
    }

    /**
     * Validates HTML content string.
     *
     * @param htmlContent the HTML content to validate
     * @throws ValidationException if the HTML content is invalid
     */
    private static void validateHtmlContent(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            throw new ValidationException("HTML content cannot be empty");
        }
        
        String trimmed = htmlContent.trim();
        
        // Check content length (reasonable limit for HTML content)
        if (trimmed.length() > 10 * 1024 * 1024) { // 10MB limit
            throw new ValidationException("HTML content is too large (maximum 10MB)");
        }
        
        // Basic HTML validation - should contain some HTML-like content
        if (!trimmed.toLowerCase().contains("<") || !trimmed.toLowerCase().contains(">")) {
            throw new ValidationException("HTML content does not appear to contain valid HTML markup");
        }
    }

    /**
     * Validates CSS content string.
     *
     * @param cssContent the CSS content to validate
     * @throws ValidationException if the CSS content is invalid
     */
    private static void validateCssContent(String cssContent) {
        if (cssContent == null || cssContent.trim().isEmpty()) {
            return; // CSS content is optional
        }
        
        String trimmed = cssContent.trim();
        
        // Check content length (reasonable limit for CSS content)
        if (trimmed.length() > 5 * 1024 * 1024) { // 5MB limit
            throw new ValidationException("CSS content is too large (maximum 5MB)");
        }
        
        // Basic CSS validation - check for suspicious content
        if (trimmed.toLowerCase().contains("<script") || trimmed.toLowerCase().contains("javascript:")) {
            throw new ValidationException("CSS content contains potentially unsafe content");
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
            

            if (headerFile.getSize() > 1024 * 1024) {
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
            

            if (footerFile.getSize() > 1024 * 1024) {
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
            

            if (banglaFooter.getSize() > 1024 * 1024) {
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
            return;
        }


        if (assets.size() > MAX_ASSET_FILES) {
            throw new ValidationException(
                    "Too many asset files. Maximum allowed: " + MAX_ASSET_FILES + 
                    ", provided: " + assets.size()
            );
        }


        FileValidator.validateAssetFiles(assets);


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
            return;
        }

        String trimmedPassword = password.trim();
        

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
            return;
        }



        if (request.isJsEnabled() && assets.size() > 20) {
            throw new ValidationException(
                    "When JavaScript is enabled, maximum 20 asset files are allowed for performance reasons. " +
                    "Current count: " + assets.size()
            );
        }


        long totalAssetSize = assets.stream()
                .filter(asset -> asset != null && !asset.isEmpty())
                .mapToLong(MultipartFile::getSize)
                .sum();


        long maxTotalAssetSize = 50 * 1024 * 1024;
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