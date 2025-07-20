package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.FileProcessingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImageToPdfConverterTest {

    @InjectMocks
    private ImageToPdfConverter imageToPdfConverter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testIsImageFile_WithValidImageExtensions() {

        assertTrue(imageToPdfConverter.isImageFile("test.jpg"));
        assertTrue(imageToPdfConverter.isImageFile("test.jpeg"));
        assertTrue(imageToPdfConverter.isImageFile("test.png"));
        assertTrue(imageToPdfConverter.isImageFile("test.bmp"));
        assertTrue(imageToPdfConverter.isImageFile("test.gif"));
        

        assertTrue(imageToPdfConverter.isImageFile("test.JPG"));
        assertTrue(imageToPdfConverter.isImageFile("test.PNG"));
    }

    @Test
    void testIsImageFile_WithInvalidExtensions() {
        assertFalse(imageToPdfConverter.isImageFile("test.pdf"));
        assertFalse(imageToPdfConverter.isImageFile("test.txt"));
        assertFalse(imageToPdfConverter.isImageFile("test.doc"));
        assertFalse(imageToPdfConverter.isImageFile("test"));
    }

    @Test
    void testIsImageFile_WithNullOrEmptyPath() {
        assertFalse(imageToPdfConverter.isImageFile(null));
        assertFalse(imageToPdfConverter.isImageFile(""));
        assertFalse(imageToPdfConverter.isImageFile("   "));
    }

    @Test
    void testGetSupportedExtensions() {
        List<String> supportedExtensions = imageToPdfConverter.getSupportedExtensions();
        
        assertNotNull(supportedExtensions);
        assertFalse(supportedExtensions.isEmpty());
        assertTrue(supportedExtensions.contains("jpg"));
        assertTrue(supportedExtensions.contains("jpeg"));
        assertTrue(supportedExtensions.contains("png"));
        assertTrue(supportedExtensions.contains("bmp"));
        assertTrue(supportedExtensions.contains("gif"));
    }

    @Test
    void testConvertImageToPdf_WithNullPath_ThrowsException() {
        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                imageToPdfConverter.convertImageToPdf(null));
        
        assertEquals("Image file path cannot be null or empty", exception.getMessage());
    }

    @Test
    void testConvertImageToPdf_WithEmptyPath_ThrowsException() {
        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                imageToPdfConverter.convertImageToPdf(""));
        
        assertEquals("Image file path cannot be null or empty", exception.getMessage());
    }

    @Test
    void testConvertImageToPdf_WithNonExistentFile_ThrowsException() {
        String nonExistentPath = tempDir.resolve("nonexistent.jpg").toString();
        
        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                imageToPdfConverter.convertImageToPdf(nonExistentPath));
        
        assertTrue(exception.getMessage().contains("Image file does not exist"));
    }

    @Test
    void testConvertImageToPdf_WithDirectory_ThrowsException() throws IOException {
        Path dirPath = tempDir.resolve("testdir");
        Files.createDirectory(dirPath);
        
        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                imageToPdfConverter.convertImageToPdf(dirPath.toString()));
        
        assertTrue(exception.getMessage().contains("Path is not a file"));
    }

    @Test
    void testConvertImageToPdf_WithUnsupportedFormat_ThrowsException() throws IOException {
        Path textFile = tempDir.resolve("test.txt");
        Files.write(textFile, "test content".getBytes());
        
        FileProcessingException exception = assertThrows(FileProcessingException.class, () ->
                imageToPdfConverter.convertImageToPdf(textFile.toString()));
        
        assertTrue(exception.getMessage().contains("Unsupported image format"));
    }

    @Test
    void testConvertImageToPdf_WithValidJpegImage() throws IOException {

        Path jpegFile = tempDir.resolve("test.jpg");
        byte[] jpegHeader = {
            (byte)0xFF, (byte)0xD8,
            (byte)0xFF, (byte)0xE0,
            0x00, 0x10,
            'J', 'F', 'I', 'F', 0x00,
            0x01, 0x01,
            0x00,
            0x00, 0x01,
            0x00, 0x01,
            0x00, 0x00,
            (byte)0xFF, (byte)0xD9
        };
        Files.write(jpegFile, jpegHeader);
        

        assertThrows(IOException.class, () -> 
                imageToPdfConverter.convertImageToPdf(jpegFile.toString()));
    }
}