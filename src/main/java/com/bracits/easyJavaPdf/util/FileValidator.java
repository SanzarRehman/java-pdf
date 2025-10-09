package com.bracits.easyJavaPdf.util;

import com.bracits.easyJavaPdf.exception.ValidationException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Utility class for validating file uploads and file-related operations.
 * Provides methods for file type validation, size validation, and content validation.
 */
public final class FileValidator {


    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;


    private static final Set<String> SUPPORTED_HTML_TYPES = Set.of(
            "text/html", "text/plain", "application/octet-stream"
    );

    private static final Set<String> SUPPORTED_CSS_TYPES = Set.of(
            "text/css", "text/plain", "application/octet-stream"
    );

    private static final Set<String> SUPPORTED_PDF_TYPES = Set.of(
            "application/pdf", "application/octet-stream"
    );

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp", 
            "image/tiff", "image/webp", "application/octet-stream"
    );

    private static final Set<String> SUPPORTED_FONT_TYPES = Set.of(
            "font/ttf", "font/otf", "application/font-ttf", "application/font-otf",
            "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"
    );


    private static final Set<String> HTML_EXTENSIONS = Set.of(".html", ".htm");
    private static final Set<String> CSS_EXTENSIONS = Set.of(".css");
    private static final Set<String> PDF_EXTENSIONS = Set.of(".pdf");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".tif", ".webp",".html"
    );
    private static final Set<String> FONT_EXTENSIONS = Set.of(".ttf", ".otf");

    private FileValidator() {

    }

    /**
     * Validates if the file is not null and not empty.
     *
     * @param file the file to validate
     * @param fieldName the name of the field for error messages
     * @throws ValidationException if the file is null or empty
     */
    public static void validateFileNotEmpty(MultipartFile file, String fieldName) {
        if (file == null) {
            throw new ValidationException(fieldName + " cannot be null");
        }
        if (file.isEmpty()) {
            throw new ValidationException(fieldName + " cannot be empty");
        }
    }

    /**
     * Validates the file size against the maximum allowed size.
     *
     * @param file the file to validate
     * @param fieldName the name of the field for error messages
     * @throws ValidationException if the file size exceeds the limit
     */
    public static void validateFileSize(MultipartFile file, String fieldName) {
        if (file != null && file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException(
                    fieldName + " size (" + formatFileSize(file.getSize()) + 
                    ") exceeds maximum allowed size (" + formatFileSize(MAX_FILE_SIZE) + ")"
            );
        }
    }

    /**
     * Validates if the file is a valid HTML file.
     *
     * @param file the file to validate
     * @param fieldName the name of the field for error messages
     * @throws ValidationException if the file is not a valid HTML file
     */
    public static void validateHtmlFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            return;
        }

        validateFileSize(file, fieldName);
        
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        
        if (!isValidFileType(contentType, fileName, SUPPORTED_HTML_TYPES, HTML_EXTENSIONS)) {
            throw new ValidationException(
                    fieldName + " must be an HTML file (.html, .htm)"
            );
        }
    }

    /**
     * Validates if the file is a valid CSS file.
     *
     * @param file the file to validate
     * @param fieldName the name of the field for error messages
     * @throws ValidationException if the file is not a valid CSS file
     */
    public static void validateCssFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            return;
        }

        validateFileSize(file, fieldName);
        
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        
        if (!isValidFileType(contentType, fileName, SUPPORTED_CSS_TYPES, CSS_EXTENSIONS)) {
            throw new ValidationException(
                    fieldName + " must be a CSS file (.css)"
            );
        }
    }

    /**
     * Validates if the file is a valid PDF file.
     *
     * @param file the file to validate
     * @param fieldName the name of the field for error messages
     * @throws ValidationException if the file is not a valid PDF file
     */
    public static void validatePdfFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            return;
        }

        validateFileSize(file, fieldName);
        
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        
        if (!isValidFileType(contentType, fileName, SUPPORTED_PDF_TYPES, PDF_EXTENSIONS)) {
            throw new ValidationException(
                    fieldName + " must be a PDF file (.pdf)"
            );
        }
    }

    /**
     * Validates if the file is a valid image file.
     *
     * @param file the file to validate
     * @param fieldName the name of the field for error messages
     * @throws ValidationException if the file is not a valid image file
     */
    public static void validateImageFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            return;
        }

        validateFileSize(file, fieldName);
        
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        
        if (!isValidFileType(contentType, fileName, SUPPORTED_IMAGE_TYPES, IMAGE_EXTENSIONS)) {
            throw new ValidationException(
                    fieldName + " must be a valid image file (.jpg, .jpeg, .png, .gif, .bmp, .tiff, .webp)"
            );
        }
    }

    /**
     * Validates if the file is a valid font file.
     *
     * @param file the file to validate
     * @param fieldName the name of the field for error messages
     * @throws ValidationException if the file is not a valid font file
     */
    public static void validateFontFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            return;
        }

        validateFileSize(file, fieldName);
        
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        
        if (!isValidFileType(contentType, fileName, SUPPORTED_FONT_TYPES, FONT_EXTENSIONS)) {
            throw new ValidationException(
                    fieldName + " must be a valid font file (.ttf, .otf)"
            );
        }
    }

    /**
     * Validates if the file is either a PDF or an image file (for merge operations).
     *
     * @param file the file to validate
     * @param fieldName the name of the field for error messages
     * @throws ValidationException if the file is not a valid PDF or image file
     */
    public static void validatePdfOrImageFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(fieldName + " cannot be null or empty");
        }

        validateFileSize(file, fieldName);
        
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        
        boolean isPdf = isValidFileType(contentType, fileName, SUPPORTED_PDF_TYPES, PDF_EXTENSIONS);
        boolean isImage = isValidFileType(contentType, fileName, SUPPORTED_IMAGE_TYPES, IMAGE_EXTENSIONS);
        
        if (!isPdf && !isImage) {
            throw new ValidationException(
                    fieldName + " must be either a PDF file (.pdf) or an image file " +
                    "(.jpg, .jpeg, .png, .gif, .bmp, .tiff, .webp)"
            );
        }
    }

    /**
     * Validates a list of files for merge operations.
     *
     * @param files the list of files to validate
     * @throws ValidationException if any file is invalid
     */
    public static void validateMergeFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException("At least one file is required for merging");
        }

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            validatePdfOrImageFile(file, "File " + (i + 1));
        }
    }

    /**
     * Validates asset files (fonts, images, etc.) for PDF generation.
     *
     * @param assets the list of asset files to validate
     * @throws ValidationException if any asset file is invalid
     */
    public static void validateAssetFiles(List<MultipartFile> assets) {
        if (assets == null || assets.isEmpty()) {
            return;
        }

        for (int i = 0; i < assets.size(); i++) {
            MultipartFile asset = assets.get(i);
            if (asset != null && !asset.isEmpty()) {
                validateFileSize(asset, "Asset " + (i + 1));
                
                String contentType = asset.getContentType();
                String fileName = asset.getOriginalFilename();
                
                boolean isImage = isValidFileType(contentType, fileName, SUPPORTED_IMAGE_TYPES, IMAGE_EXTENSIONS);
                boolean isFont = isValidFileType(contentType, fileName, SUPPORTED_FONT_TYPES, FONT_EXTENSIONS);
                
                if (!isImage && !isFont) {
                    throw new ValidationException(
                            "Asset " + (i + 1) + " must be either an image file " +
                            "(.jpg, .jpeg, .png, .gif, .bmp, .tiff, .webp) or a font file (.ttf, .otf)"
                    );
                }
            }
        }
    }

    /**
     * Checks if a file type is valid based on content type and file extension.
     *
     * @param contentType the MIME content type
     * @param fileName the original filename
     * @param supportedTypes the set of supported MIME types
     * @param supportedExtensions the set of supported file extensions
     * @return true if the file type is valid, false otherwise
     */
    private static boolean isValidFileType(String contentType, String fileName, 
                                         Set<String> supportedTypes, Set<String> supportedExtensions) {

        if (contentType != null && supportedTypes.contains(contentType.toLowerCase())) {
            return true;
        }
        

        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase();
            return supportedExtensions.stream().anyMatch(lowerFileName::endsWith);
        }
        
        return false;
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