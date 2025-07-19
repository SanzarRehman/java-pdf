package com.bracits.easyJavaPdf.controller;

import com.bracits.easyJavaPdf.dto.PageRange;
import com.bracits.easyJavaPdf.handler.PageRangeParser;
import com.bracits.easyJavaPdf.service.PdfMergerService;
import com.bracits.easyJavaPdf.util.TempFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(MergeController.class)
public class MergeControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockBean
    private PdfMergerService pdfMergerService;

    @MockBean
    private TempFileManager tempFileManager;

    @Captor
    private ArgumentCaptor<List<Path>> pathsCaptor;

    @Captor
    private ArgumentCaptor<List<PageRange>> pageRangesCaptor;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void testMergePdf_Success() throws Exception {
        // Prepare test data
        byte[] pdfContent = "PDF content".getBytes();
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "test1.pdf", MediaType.APPLICATION_PDF_VALUE, "PDF content 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "test2.pdf", MediaType.APPLICATION_PDF_VALUE, "PDF content 2".getBytes());

        // Mock temp directory
        Path mockTempDir = mock(Path.class);
        Path mockFile1Path = mock(Path.class);
        Path mockFile2Path = mock(Path.class);
        
        when(tempFileManager.createTempDirectory(anyString())).thenReturn(mockTempDir);
        when(mockTempDir.resolve(eq("test1.pdf"))).thenReturn(mockFile1Path);
        when(mockTempDir.resolve(eq("test2.pdf"))).thenReturn(mockFile2Path);
        when(mockTempDir.toString()).thenReturn("/temp/dir");
        
        // Mock PageRangeParser
        List<PageRange> mockPageRanges = Arrays.asList(
                new PageRange("test1.pdf", 0, 1, 1),
                new PageRange("test2.pdf", 0, 2, 1)
        );
        
        try (MockedStatic<PageRangeParser> mockedParser = Mockito.mockStatic(PageRangeParser.class)) {
            mockedParser.when(() -> PageRangeParser.parse(anyString(), anyString()))
                    .thenReturn(mockPageRanges);
            
            // Mock service response
            when(pdfMergerService.mergePdfs(anyList(), anyList(), anyString(), any(Path.class), anyString()))
                    .thenReturn(pdfContent);
            
            // Perform request
            mockMvc.perform(multipart("/api/v1.0/merge")
                    .file((MockMultipartFile) file1)
                    .file((MockMultipartFile) file2)
                    .param("pagesDefinition", "test1.pdf~0:1 test2.pdf~0:2")
                    .param("disposition", "attachment")
                    .param("fileName", "merged.pdf")
                    .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(pdfContent))
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(header().string("Content-Disposition", containsString("merged.pdf")))
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF));
            
            // Verify service was called with correct parameters
            verify(pdfMergerService).mergePdfs(anyList(), eq(mockPageRanges), isNull(), eq(mockTempDir), isNull());
            verify(tempFileManager).createTempDirectory(anyString());
            verify(tempFileManager).registerForCleanup(mockFile1Path);
            verify(tempFileManager).registerForCleanup(mockFile2Path);
        }
    }

    @Test
    public void testMergePdf_WithPassword() throws Exception {
        // Prepare test data
        byte[] pdfContent = "PDF content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "files", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "PDF content".getBytes());

        // Mock temp directory
        Path mockTempDir = mock(Path.class);
        Path mockFilePath = mock(Path.class);
        
        when(tempFileManager.createTempDirectory(anyString())).thenReturn(mockTempDir);
        when(mockTempDir.resolve(eq("test.pdf"))).thenReturn(mockFilePath);
        when(mockTempDir.toString()).thenReturn("/temp/dir");
        
        // Mock PageRangeParser
        List<PageRange> mockPageRanges = List.of(new PageRange("test.pdf", 0, -1, 1));
        
        try (MockedStatic<PageRangeParser> mockedParser = Mockito.mockStatic(PageRangeParser.class)) {
            mockedParser.when(() -> PageRangeParser.parse(anyString(), anyString()))
                    .thenReturn(mockPageRanges);
            
            // Mock service response
            when(pdfMergerService.mergePdfs(anyList(), anyList(), anyString(), any(Path.class), anyString()))
                    .thenReturn(pdfContent);
            
            // Perform request
            mockMvc.perform(multipart("/api/v1.0/merge")
                    .file((MockMultipartFile) file)
                    .param("pagesDefinition", "test.pdf~0:-1")
                    .param("password", "secret123")
                    .param("resourceOptimizer", "true")
                    .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(pdfContent));
            
            // Verify service was called with correct parameters
            verify(pdfMergerService).mergePdfs(anyList(), eq(mockPageRanges), eq("secret123"), eq(mockTempDir), eq("true"));
        }
    }

    @Test
    public void testMergePdf_MissingFiles() throws Exception {
        mockMvc.perform(multipart("/api/v1.0/merge")
                .param("pagesDefinition", "test.pdf~0:1")
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
        
        verifyNoInteractions(pdfMergerService);
    }

    @Test
    public void testMergePdf_InvalidDisposition() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "PDF content".getBytes());
        
        mockMvc.perform(multipart("/api/v1.0/merge")
                .file((MockMultipartFile) file)
                .param("pagesDefinition", "test.pdf~0:1")
                .param("disposition", "invalid")
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
        
        verifyNoInteractions(pdfMergerService);
    }
}