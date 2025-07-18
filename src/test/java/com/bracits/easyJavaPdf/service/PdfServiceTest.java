package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.FileProcessingException;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfServiceTest {

    @Mock
    private Executor mockExecutor;

    private PdfService pdfService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pdfService = new PdfService(mockExecutor);
    }

    @Test
    void testGenerateWithValidHtmlFile() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body>Test Content</body></html>");
        Path cssFile = createTempFile("test.css", "body { font-family: Arial; }");
        
        // Mock executor to run synchronously for testing
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(mockExecutor).execute(any());

        // When
        CompletableFuture<byte[]> result = pdfService.generate(
            htmlFile, cssFile, null, null, null, null, null, "false"
        );

        // Then
        assertNotNull(result);
        // Note: Full PDF generation test would require more complex setup
        // This test verifies the method structure and basic flow
    }

    @Test
    void testGenerateWithNonExistentHtmlFile() {
        // Given
        Path nonExistentFile = tempDir.resolve("nonexistent.html");
        
        // Mock executor to run synchronously for testing
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(mockExecutor).execute(any());

        // When & Then
        CompletableFuture<byte[]> result = pdfService.generate(
            nonExistentFile, null, null, null, null, null, null, "false"
        );
        
        Exception exception = assertThrows(Exception.class, result::join);
        assertTrue(exception.getCause() instanceof PdfGenerationException);
    }

    @Test
    void testProcessHtmlContentWithValidFiles() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body>Test</body></html>");
        Path cssFile = createTempFile("test.css", "body { color: red; }");

        // When
        String result = invokePrivateMethod("processHtmlContent", htmlFile, cssFile, "false");

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Test"));
        assertTrue(result.contains("color: red"));
        assertTrue(result.contains("@page { size: A4 portrait; margin: 1cm; }"));
    }

    @Test
    void testProcessHtmlContentWithNullCssFile() throws Exception {
        // Given
        Path htmlFile = createTempFile("test.html", "<html><body>Test</body></html>");

        // When
        String result = invokePrivateMethod("processHtmlContent", htmlFile, null, "false");

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Test"));
        assertTrue(result.contains("@page { size: A4 portrait; margin: 1cm; }"));
    }

    @Test
    void testProcessHtmlContentWithInvalidFile() {
        // Given
        Path invalidFile = tempDir.resolve("invalid.html");

        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () -> 
            invokePrivateMethod("processHtmlContent", invalidFile, null, "false"));
        assertTrue(exception.getCause() instanceof FileProcessingException);
    }

    @Test
    void testWrapHtmlWithCss() {
        // When
        String result = invokePrivateMethod("wrapHtmlWithCss", 
            "<div>Content</div>", "body { margin: 0; }");

        // Then
        String expected = "<html><head><style>body { margin: 0; }</style></head><body><div>Content</div></body></html>";
        assertEquals(expected, result);
    }

    @Test
    void testIsJavaScriptEnabledWithTrue() {
        // When
        boolean result = invokePrivateMethod("isJavaScriptEnabled", "true");

        // Then
        assertTrue(result);
    }

    @Test
    void testIsJavaScriptEnabledWithFalse() {
        // When
        boolean result = invokePrivateMethod("isJavaScriptEnabled", "false");

        // Then
        assertFalse(result);
    }

    @Test
    void testIsJavaScriptEnabledWithNull() {
        // When
        boolean result = invokePrivateMethod("isJavaScriptEnabled", (String) null);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsJavaScriptEnabledCaseInsensitive() {
        // When
        boolean resultUpper = invokePrivateMethod("isJavaScriptEnabled", "TRUE");
        boolean resultMixed = invokePrivateMethod("isJavaScriptEnabled", "True");

        // Then
        assertTrue(resultUpper);
        assertTrue(resultMixed);
    }

    @Test
    void testSetupConverterPropertiesWithNullFonts() {
        // When
        Object result = invokePrivateMethod("setupConverterProperties", (List<Path>) null);

        // Then
        assertNotNull(result);
    }

    @Test
    void testSetupConverterPropertiesWithEmptyFonts() {
        // When
        Object result = invokePrivateMethod("setupConverterProperties", Arrays.asList());

        // Then
        assertNotNull(result);
    }

    @Test
    void testSetupConverterPropertiesWithValidFonts() throws Exception {
        // Given
        Path fontFile = createTempFile("test.ttf", "fake font content");
        List<Path> fontFiles = Arrays.asList(fontFile);

        // When
        Object result = invokePrivateMethod("setupConverterProperties", fontFiles);

        // Then
        assertNotNull(result);
    }

    @Test
    void testCreateFontProviderWithValidFonts() throws Exception {
        // Given
        Path fontFile1 = createTempFile("font1.ttf", "fake font content 1");
        Path fontFile2 = createTempFile("font2.ttf", "fake font content 2");
        List<Path> fontFiles = Arrays.asList(fontFile1, fontFile2);

        // When
        Object result = invokePrivateMethod("createFontProvider", fontFiles);

        // Then
        assertNotNull(result);
    }

    // Helper methods

    private Path createTempFile(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.write(file, content.getBytes());
        return file;
    }

    @SuppressWarnings("unchecked")
    private <T> T invokePrivateMethod(String methodName, Object... args) {
        try {
            Class<?>[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    // Handle null arguments - you may need to specify the exact type
                    paramTypes[i] = Object.class;
                } else {
                    paramTypes[i] = args[i].getClass();
                    // Handle primitive wrapper classes
                    if (paramTypes[i] == String.class && methodName.equals("isJavaScriptEnabled")) {
                        paramTypes[i] = String.class;
                    } else if (List.class.isAssignableFrom(paramTypes[i])) {
                        paramTypes[i] = List.class;
                    }
                }
            }
            
            return (T) ReflectionTestUtils.invokeMethod(pdfService, methodName, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method: " + methodName, e);
        }
    }
}