package com.bracits.easyJavaPdf.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CssProcessorTest {

    private CssProcessor cssProcessor;

    @BeforeEach
    void setUp() {
        cssProcessor = new CssProcessor();
    }

    @Test
    void testProcessCssWithNullInput() {
        // Test with null input
        String result = cssProcessor.processCss(null);
        
        assertNotNull(result);
        assertTrue(result.contains("@page"));
        assertTrue(result.contains("body"));
        assertTrue(result.contains("margin: 0"));
    }

    @Test
    void testProcessCssWithEmptyInput() {
        // Test with empty input
        String result = cssProcessor.processCss("");
        
        assertNotNull(result);
        assertTrue(result.contains("@page"));
        assertTrue(result.contains("body"));
    }

    @Test
    void testProcessCssWithWhitespaceOnlyInput() {
        // Test with whitespace-only input
        String result = cssProcessor.processCss("   \n\t  ");
        
        assertNotNull(result);
        assertTrue(result.contains("@page"));
        assertTrue(result.contains("body"));
    }

    @Test
    void testRemovePageRules() {
        // Test removal of @page rules that cause margin collapse issues
        String cssWithPageRules = """
            @page {
                size: A4;
                margin: 2cm;
                @top-left {
                    content: counter(page);
                }
            }
            @page :first {
                margin-top: 5cm;
            }
            body {
                font-family: Arial;
            }
            """;

        String result = cssProcessor.processCss(cssWithPageRules);
        
        assertNotNull(result);
        // Should not contain the problematic @page rules
        assertFalse(result.contains("@top-left"));
        assertFalse(result.contains("counter(page)"));
        assertFalse(result.contains("@page :first"));
        // Should preserve safe CSS
        assertTrue(result.contains("font-family: Arial"));
        // Should have default safe @page rule
        assertTrue(result.contains("@page { size: A4; margin: 2cm; }"));
    }

    @Test
    void testRemoveUrlReferences() {
        // Test removal of url() references that cause FileNotFoundException
        String cssWithUrls = """
            body {
                background-image: url('background.jpg');
                font-family: Arial;
            }
            .header {
                background: url("header-bg.png") no-repeat;
            }
            .icon {
                background-image: url(icon.svg);
            }
            """;

        String result = cssProcessor.processCss(cssWithUrls);
        
        assertNotNull(result);
        // Should not contain any url() references
        assertFalse(result.toLowerCase().contains("url("));
        // Should preserve safe CSS properties
        assertTrue(result.contains("font-family: Arial"));
    }

    @Test
    void testRemoveImportStatements() {
        // Test removal of @import statements
        String cssWithImports = """
            @import url("external.css");
            @import "another.css";
            @import url('fonts.css');
            
            body {
                color: #333;
            }
            """;

        String result = cssProcessor.processCss(cssWithImports);
        
        assertNotNull(result);
        // Should not contain @import statements
        assertFalse(result.contains("@import"));
        // Should preserve safe CSS
        assertTrue(result.contains("color: #333"));
    }

    @Test
    void testRemoveProblematicProperties() {
        // Test removal of properties that cause PDF generation issues
        String cssWithProblematicProps = """
            .element {
                content: "Generated content";
                counter-increment: section;
                string-set: heading content();
                break-before: page;
                page-break-after: always;
                columns: 2;
                display: flex;
                flex-direction: column;
                grid-template-columns: 1fr 1fr;
                transform: rotate(45deg);
                animation: slide 1s ease;
                transition: all 0.3s;
                filter: blur(5px);
                color: red;
                font-size: 14px;
            }
            """;

        String result = cssProcessor.processCss(cssWithProblematicProps);
        
        assertNotNull(result);
        // Should not contain problematic properties
        assertFalse(result.contains("content:"));
        assertFalse(result.contains("counter-increment"));
        assertFalse(result.contains("string-set"));
        assertFalse(result.contains("break-before"));
        assertFalse(result.contains("page-break-after"));
        assertFalse(result.contains("columns:"));
        assertFalse(result.contains("display: flex"));
        assertFalse(result.contains("flex-direction"));
        assertFalse(result.contains("grid-template"));
        assertFalse(result.contains("transform:"));
        assertFalse(result.contains("animation:"));
        assertFalse(result.contains("transition:"));
        assertFalse(result.contains("filter:"));
        
        // Should preserve safe properties
        assertTrue(result.contains("color: red"));
        assertTrue(result.contains("font-size: 14px"));
    }

    @Test
    void testRemovePseudoElements() {
        // Test removal of pseudo-elements that can cause issues
        String cssWithPseudoElements = """
            .element::before {
                content: "Before";
                display: block;
            }
            .element::after {
                content: "After";
                position: absolute;
            }
            .element:hover {
                color: blue;
            }
            .element {
                color: black;
            }
            """;

        String result = cssProcessor.processCss(cssWithPseudoElements);
        
        assertNotNull(result);
        // Should not contain pseudo-elements
        assertFalse(result.contains("::before"));
        assertFalse(result.contains("::after"));
        
        // Should preserve safe selectors and properties
        assertTrue(result.contains("color: blue"));
        assertTrue(result.contains("color: black"));
    }

    @Test
    void testPreserveSafeFontFaces() {
        // Test preservation of safe @font-face rules
        String cssWithFontFaces = """
            @font-face {
                font-family: 'SafeFont';
                font-weight: 400;
                font-style: normal;
            }
            @font-face {
                font-family: 'UnsafeFont';
                src: url('font.woff2') format('woff2');
                font-weight: 400;
            }
            @font-face {
                src: url('orphan-font.woff');
            }
            """;

        String result = cssProcessor.processCss(cssWithFontFaces);
        
        assertNotNull(result);
        // Should preserve font-face with font-family but no src
        assertTrue(result.contains("font-family: 'SafeFont'"));
        assertTrue(result.contains("font-weight: 400"));
        
        // Should preserve font-family from rules with src, but remove src
        assertTrue(result.contains("font-family: 'UnsafeFont'"));
        assertFalse(result.contains("src: url"));
        
        // Should not preserve font-face without font-family
        assertFalse(result.contains("orphan-font"));
    }

    @Test
    void testCleanupEmptyRules() {
        // Test cleanup of empty CSS rules
        String cssWithEmptyRules = """
            .empty1 { }
            .empty2 {
            }
            .valid {
                color: red;
            }
            .empty3 { /* comment only */ }
            """;

        String result = cssProcessor.processCss(cssWithEmptyRules);
        
        assertNotNull(result);
        // Should preserve valid rules
        assertTrue(result.contains("color: red"));
        // Empty rules should be cleaned up (hard to test exact removal due to whitespace normalization)
        assertTrue(result.length() < cssWithEmptyRules.length());
    }

    @Test
    void testComplexCssProcessing() {
        // Test with complex CSS that has multiple issues
        String complexCss = """
            @charset "UTF-8";
            @import url("external.css");
            
            @page {
                @top-left {
                    content: counter(page);
                    background: #fbc847;
                }
            }
            
            @font-face {
                font-family: 'CustomFont';
                src: url('font.woff2') format('woff2'),
                     url('font.woff') format('woff');
                font-weight: 400;
            }
            
            html {
                color: #393939;
                font-family: 'CustomFont', Arial;
                font-size: 11pt;
                line-height: 1.5;
            }
            
            .header {
                background-image: url('header.jpg');
                display: flex;
                justify-content: center;
                transform: translateY(-10px);
            }
            
            .content::before {
                content: "Generated content";
                display: block;
            }
            
            .sidebar {
                columns: 2;
                column-gap: 1cm;
                break-inside: avoid;
            }
            
            @media print {
                .no-print {
                    display: none;
                }
            }
            """;

        String result = cssProcessor.processCss(complexCss);
        
        assertNotNull(result);
        
        // Should remove problematic elements
        assertFalse(result.contains("@import"));
        assertFalse(result.contains("@top-left"));
        assertFalse(result.contains("counter(page)"));
        assertFalse(result.contains("url("));
        assertFalse(result.contains("::before"));
        assertFalse(result.contains("content:"));
        assertFalse(result.contains("display: flex"));
        assertFalse(result.contains("transform:"));
        assertFalse(result.contains("columns:"));
        assertFalse(result.contains("break-inside"));
        
        // Should preserve safe CSS
        assertTrue(result.contains("color: #393939"));
        assertTrue(result.contains("font-family: 'CustomFont'"));
        assertTrue(result.contains("font-size: 11pt"));
        assertTrue(result.contains("line-height: 1.5"));
        assertTrue(result.contains("display: none")); // from @media print
        
        // Should have default safe styles
        assertTrue(result.contains("@page { size: A4; margin: 2cm; }"));
        assertTrue(result.contains("body { margin: 0"));
    }

    @Test
    void testCaseSensitivity() {
        // Test that processing works with different case variations
        String mixedCaseCss = """
            BODY {
                BACKGROUND-IMAGE: URL('bg.jpg');
                COLOR: red;
            }
            .Element::BEFORE {
                CONTENT: "test";
            }
            @PAGE {
                SIZE: A4;
            }
            """;

        String result = cssProcessor.processCss(mixedCaseCss);
        
        assertNotNull(result);
        // Should remove problematic elements regardless of case
        assertFalse(result.toLowerCase().contains("url("));
        assertFalse(result.toLowerCase().contains("::before"));
        assertFalse(result.toLowerCase().contains("content:"));
        
        // Should preserve safe properties
        assertTrue(result.toLowerCase().contains("color: red"));
    }

    @Test
    void testPerformanceWithLargeCss() {
        // Test performance with a large CSS string
        StringBuilder largeCss = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeCss.append(String.format("""
                .class%d {
                    color: #%06x;
                    background-image: url('bg%d.jpg');
                    font-size: %dpx;
                    display: flex;
                    transform: rotate(%ddeg);
                }
                """, i, i * 1000, i, 10 + (i % 20), i % 360));
        }

        long startTime = System.currentTimeMillis();
        String result = cssProcessor.processCss(largeCss.toString());
        long endTime = System.currentTimeMillis();
        
        assertNotNull(result);
        assertTrue(endTime - startTime < 5000); // Should complete within 5 seconds
        
        // Should still remove problematic elements from large CSS
        assertFalse(result.contains("url("));
        assertFalse(result.contains("display: flex"));
        assertFalse(result.contains("transform:"));
        
        // Should preserve safe properties
        assertTrue(result.contains("color: #"));
        assertTrue(result.contains("font-size:"));
    }

    @Test
    void testMalformedCssHandling() {
        // Test handling of malformed CSS
        String malformedCss = """
            .unclosed {
                color: red;
            /* missing closing brace */
            
            .invalid-property: value-without-colon;
            
            @page {
                margin: 2cm
                /* missing semicolon */
                size: A4;
            }
            
            .valid {
                color: blue;
            }
            """;

        // Should not throw exception
        assertDoesNotThrow(() -> {
            String result = cssProcessor.processCss(malformedCss);
            assertNotNull(result);
            // Should still preserve some valid CSS
            assertTrue(result.contains("color: blue") || result.contains("color:blue"));
        });
    }

    @Test
    void testDefaultPageCssGeneration() {
        // Test that default CSS is properly generated
        String result = cssProcessor.processCss("");
        
        assertNotNull(result);
        assertFalse(result.trim().isEmpty());
        
        // Should contain essential default styles
        assertTrue(result.contains("@page"));
        assertTrue(result.contains("size: A4"));
        assertTrue(result.contains("margin: 2cm"));
        assertTrue(result.contains("body"));
        assertTrue(result.contains("box-sizing: border-box"));
        assertTrue(result.contains("line-height: 1.4"));
    }
}