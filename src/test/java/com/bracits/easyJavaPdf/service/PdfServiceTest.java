package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.PdfGenerationRequest;
import com.bracits.easyJavaPdf.dto.PdfResponse;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.bracits.easyJavaPdf.service.strategy.PdfGenerationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdfServiceTest {

    @Mock
    private Executor executor;

    @Mock
    private PdfGenerationStrategy highPriorityStrategy;

    @Mock
    private PdfGenerationStrategy lowPriorityStrategy;

    private PdfService pdfService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        pdfService = new PdfService(executor, List.of(lowPriorityStrategy, highPriorityStrategy));
    }

    @Test
    void generatePdfUsesHighestPrioritySupportingStrategy() throws Exception {
        PdfGenerationRequest request = new PdfGenerationRequest();

        when(highPriorityStrategy.supports(request)).thenReturn(true);
        when(highPriorityStrategy.getPriority()).thenReturn(5);
        when(lowPriorityStrategy.supports(request)).thenReturn(true);
        when(lowPriorityStrategy.getPriority()).thenReturn(10);

        PdfResponse expectedResponse = PdfResponse.builder()
                .content(new byte[]{1, 2, 3})
                .contentLength(3L)
                .fileName("generated.pdf")
                .build();

        when(highPriorityStrategy.generate(request, PageOrientation.PORTRAIT)).thenReturn(expectedResponse);

        PdfResponse actual = pdfService.generatePdf(request).get();

        assertSame(expectedResponse, actual, "Should return response from highest priority strategy");
        verify(highPriorityStrategy).generate(request, PageOrientation.PORTRAIT);
        verify(lowPriorityStrategy, never()).generate(any(), any());
    }

    @Test
    void generatePdfPropagatesOrientationToStrategy() throws Exception {
        PdfGenerationRequest request = new PdfGenerationRequest();
        request.setPageOrientation("landscape");

        when(highPriorityStrategy.supports(request)).thenReturn(true);
        when(highPriorityStrategy.getPriority()).thenReturn(1);

        PdfResponse response = PdfResponse.builder().content(new byte[0]).contentLength(0L).build();
        when(highPriorityStrategy.generate(any(), any())).thenReturn(response);

        pdfService.generatePdf(request).get();

        ArgumentCaptor<PageOrientation> orientationCaptor = ArgumentCaptor.forClass(PageOrientation.class);
        verify(highPriorityStrategy).generate(eq(request), orientationCaptor.capture());
        assertEquals(PageOrientation.LANDSCAPE, orientationCaptor.getValue());
    }

    @Test
    void generatePdfThrowsWhenNoStrategySupportsRequest() {
        PdfGenerationRequest request = new PdfGenerationRequest();

        when(highPriorityStrategy.supports(request)).thenReturn(false);
        when(lowPriorityStrategy.supports(request)).thenReturn(false);

        CompletableFuture<PdfResponse> future = pdfService.generatePdf(request);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof PdfGenerationException);
        assertEquals("Failed to generate PDF", exception.getCause().getMessage());
    }
}