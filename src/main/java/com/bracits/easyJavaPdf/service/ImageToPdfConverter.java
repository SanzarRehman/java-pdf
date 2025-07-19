package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.FileProcessingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Component responsible for converting various image formats to PDF documents.
 * Supports JPEG, PNG, BMP, GIF, TIFF, and WebP image formats with proper scaling and positioning.
 */
@Component
public class ImageToPdfConverter {

    private static final Logger logger = LoggerFactory.getLogger(ImageToPdfConverter.class);
    
    // Extended list of supported image formats
    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS = Arrays.asList(
            "jpeg", "jpg", "png", "bmp", "gif", "tiff", "tif", "webp"
    );
    
    // Page size options
    public enum PageSize {
        A4(PDRectangle.A4),
        LETTER(PDRectangle.LETTER),
        LEGAL(PDRectangle.LEGAL),
        A3(PDRectangle.A3),
        A5(PDRectangle.A5);
        
        private final PDRectangle rectangle;
        
        PageSize(PDRectangle rectangle) {
            this.rectangle = rectangle;
        }
        
        public PDRectangle getRectangle() {
            return rectangle;
        }
    }
    
    // Scaling options
    public enum ScalingMode {
        FIT_PAGE,       // Scale to fit page while maintaining aspect ratio
        STRETCH_TO_FIT, // Stretch to fill page (may distort image)
        ACTUAL_SIZE,    // Use actual image size (may be cropped)
        CUSTOM_SCALE    // Use a custom scaling factor
    }

    /**
     * Converts an image file to a PDF document using default settings (A4 page, fit to page).
     *
     * @param imageFilePath the path to the image file to convert
     * @return a PDDocument containing the converted image
     * @throws FileProcessingException if the image cannot be processed or converted
     */
    public PDDocument convertImageToPdf(String imageFilePath) {
        return convertImageToPdf(imageFilePath, PageSize.A4, ScalingMode.FIT_PAGE, 0.9f);
    }
    
    /**
     * Converts an image file to a PDF document with custom page size and scaling options.
     *
     * @param imageFilePath the path to the image file to convert
     * @param pageSize the target PDF page size
     * @param scalingMode how the image should be scaled
     * @param scaleFactor custom scale factor (used only with CUSTOM_SCALE mode)
     * @return a PDDocument containing the converted image
     * @throws FileProcessingException if the image cannot be processed or converted
     */
    public PDDocument convertImageToPdf(String imageFilePath, PageSize pageSize, ScalingMode scalingMode, float scaleFactor) {
        logger.debug("Converting image to PDF: {} with page size: {}, scaling mode: {}", 
                    imageFilePath, pageSize, scalingMode);
        
        validateImageFile(imageFilePath);
        
        try {
            File imageFile = new File(imageFilePath);
            PDDocument document = new PDDocument();
            
            PDImageXObject image = PDImageXObject.createFromFile(imageFile.getAbsolutePath(), document);
            
            // Calculate scaling and positioning based on selected mode
            ImageDimensions dimensions = calculateImageDimensions(
                image, 
                pageSize.getRectangle(), 
                scalingMode, 
                scaleFactor
            );
            
            // Create page and add image
            PDPage page = new PDPage(pageSize.getRectangle());
            document.addPage(page);
            
            addImageToPage(document, page, image, dimensions);
            
            logger.debug("Successfully converted image to PDF: {}", imageFilePath);
            return document;
            
        } catch (IOException e) {
            logger.error("Failed to convert image to PDF: {}", imageFilePath, e);
            throw new FileProcessingException("Failed to convert image to PDF: " + imageFilePath, e);
        }
    }
    
    /**
     * Converts multiple images to a single PDF document, with one image per page.
     *
     * @param imageFilePaths list of paths to image files to convert
     * @param pageSize the target PDF page size
     * @param scalingMode how the images should be scaled
     * @return a PDDocument containing all converted images
     * @throws FileProcessingException if any image cannot be processed or converted
     */
    public PDDocument convertImagesToPdf(List<String> imageFilePaths, PageSize pageSize, ScalingMode scalingMode) {
        logger.debug("Converting {} images to PDF with page size: {}, scaling mode: {}", 
                    imageFilePaths.size(), pageSize, scalingMode);
        
        if (imageFilePaths == null || imageFilePaths.isEmpty()) {
            throw new FileProcessingException("Image file paths list cannot be null or empty");
        }
        
        try {
            PDDocument document = new PDDocument();
            
            for (String imageFilePath : imageFilePaths) {
                validateImageFile(imageFilePath);
                
                PDImageXObject image = PDImageXObject.createFromFile(new File(imageFilePath).getAbsolutePath(), document);
                
                // Calculate dimensions for this image
                ImageDimensions dimensions = calculateImageDimensions(
                    image, 
                    pageSize.getRectangle(), 
                    scalingMode, 
                    0.9f
                );
                
                // Create page and add image
                PDPage page = new PDPage(pageSize.getRectangle());
                document.addPage(page);
                
                addImageToPage(document, page, image, dimensions);
                
                logger.debug("Added image to PDF: {}", imageFilePath);
            }
            
            logger.debug("Successfully converted {} images to PDF", imageFilePaths.size());
            return document;
            
        } catch (IOException e) {
            logger.error("Failed to convert images to PDF", e);
            throw new FileProcessingException("Failed to convert images to PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the given file path represents a supported image format.
     *
     * @param filePath the file path to check
     * @return true if the file is a supported image format, false otherwise
     */
    public boolean isImageFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        
        String fileName = new File(filePath).getName().toLowerCase();
        return SUPPORTED_IMAGE_EXTENSIONS.stream()
                .anyMatch(ext -> fileName.endsWith("." + ext));
    }

    /**
     * Gets the list of supported image file extensions.
     *
     * @return a list of supported image extensions
     */
    public List<String> getSupportedExtensions() {
        return List.copyOf(SUPPORTED_IMAGE_EXTENSIONS);
    }

    /**
     * Validates that the image file exists and has a supported format.
     */
    private void validateImageFile(String imageFilePath) {
        if (imageFilePath == null || imageFilePath.trim().isEmpty()) {
            throw new FileProcessingException("Image file path cannot be null or empty");
        }
        
        File imageFile = new File(imageFilePath);
        if (!imageFile.exists()) {
            throw new FileProcessingException("Image file does not exist: " + imageFilePath);
        }
        
        if (!imageFile.isFile()) {
            throw new FileProcessingException("Path is not a file: " + imageFilePath);
        }
        
        if (!isImageFile(imageFilePath)) {
            throw new FileProcessingException("Unsupported image format: " + imageFilePath);
        }
        
        logger.debug("Image file validation passed: {}", imageFilePath);
    }

    /**
     * Calculates the optimal dimensions and positioning for an image on a PDF page
     * based on the selected scaling mode.
     */
    private ImageDimensions calculateImageDimensions(
            PDImageXObject image, 
            PDRectangle pageSize, 
            ScalingMode scalingMode, 
            float scaleFactor) {
        
        float imageWidth = image.getWidth();
        float imageHeight = image.getHeight();
        float pageWidth = pageSize.getWidth();
        float pageHeight = pageSize.getHeight();
        
        float scaledWidth;
        float scaledHeight;
        float xOffset;
        float yOffset;
        
        switch (scalingMode) {
            case FIT_PAGE:
                // Calculate scaling to fit image within page while maintaining aspect ratio
                float widthScale = pageWidth / imageWidth;
                float heightScale = pageHeight / imageHeight;
                float scale = Math.min(widthScale, heightScale);
                
                // Apply margin factor
                scale *= scaleFactor;
                
                scaledWidth = imageWidth * scale;
                scaledHeight = imageHeight * scale;
                
                // Center the image on the page
                xOffset = (pageWidth - scaledWidth) / 2;
                yOffset = (pageHeight - scaledHeight) / 2;
                break;
                
            case STRETCH_TO_FIT:
                // Stretch to fill the page (with small margin)
                scaledWidth = pageWidth * scaleFactor;
                scaledHeight = pageHeight * scaleFactor;
                
                // Center the image
                xOffset = (pageWidth - scaledWidth) / 2;
                yOffset = (pageHeight - scaledHeight) / 2;
                break;
                
            case ACTUAL_SIZE:
                // Use actual image size (may be cropped if larger than page)
                scaledWidth = Math.min(imageWidth, pageWidth);
                scaledHeight = Math.min(imageHeight, pageHeight);
                
                // Center the image
                xOffset = (pageWidth - scaledWidth) / 2;
                yOffset = (pageHeight - scaledHeight) / 2;
                break;
                
            case CUSTOM_SCALE:
                // Apply custom scale factor directly
                scaledWidth = imageWidth * scaleFactor;
                scaledHeight = imageHeight * scaleFactor;
                
                // Center the image
                xOffset = (pageWidth - scaledWidth) / 2;
                yOffset = (pageHeight - scaledHeight) / 2;
                break;
                
            default:
                // Default to FIT_PAGE if unknown mode
                float defaultScale = Math.min(pageWidth / imageWidth, pageHeight / imageHeight) * 0.9f;
                scaledWidth = imageWidth * defaultScale;
                scaledHeight = imageHeight * defaultScale;
                xOffset = (pageWidth - scaledWidth) / 2;
                yOffset = (pageHeight - scaledHeight) / 2;
        }
        
        logger.debug("Image dimensions calculated - Original: {}x{}, Scaled: {}x{}, Position: ({}, {})", 
                    imageWidth, imageHeight, scaledWidth, scaledHeight, xOffset, yOffset);
        
        return new ImageDimensions(scaledWidth, scaledHeight, xOffset, yOffset);
    }

    /**
     * Adds an image to a PDF page with the specified dimensions and positioning.
     */
    private void addImageToPage(PDDocument document, PDPage page, PDImageXObject image, ImageDimensions dimensions) 
            throws IOException {
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.drawImage(image, 
                                  dimensions.xOffset, 
                                  dimensions.yOffset, 
                                  dimensions.width, 
                                  dimensions.height);
        }
        
        logger.debug("Image added to PDF page successfully");
    }

    /**
     * Data class to hold image dimensions and positioning information.
     */
    private static class ImageDimensions {
        final float width;
        final float height;
        final float xOffset;
        final float yOffset;

        ImageDimensions(float width, float height, float xOffset, float yOffset) {
            this.width = width;
            this.height = height;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }
    }
}