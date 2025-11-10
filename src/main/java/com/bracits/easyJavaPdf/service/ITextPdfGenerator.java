package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.exception.FileProcessingException;
import com.bracits.easyJavaPdf.exception.PdfGenerationException;
import com.bracits.easyJavaPdf.handler.BengaliPageNumberHandler;
import com.bracits.easyJavaPdf.handler.Footer;
import com.bracits.easyJavaPdf.handler.Header;
import com.bracits.easyJavaPdf.handler.QRCodeTagWorkerFactory;
import com.bracits.easyJavaPdf.model.PageOrientation;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.attach.impl.OutlineHandler;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.navigation.PdfDestination;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.kernel.pdf.PdfOutline;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.html2pdf.attach.ITagWorker;
import com.itextpdf.html2pdf.attach.ProcessorContext;
import com.itextpdf.html2pdf.attach.ITagWorkerFactory;
import com.itextpdf.html2pdf.attach.impl.DefaultTagWorkerFactory;
import com.itextpdf.html2pdf.attach.impl.tags.BodyTagWorker;
import com.itextpdf.html2pdf.attach.impl.tags.DivTagWorker;
import com.itextpdf.html2pdf.attach.impl.tags.HTagWorker;
import com.itextpdf.layout.font.FontSet;
import com.itextpdf.styledxmlparser.node.IElementNode;
import com.itextpdf.layout.IPropertyContainer;
import com.itextpdf.layout.element.IElement;
import com.itextpdf.layout.properties.Property;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * iText-based implementation of the PdfGenerator interface.
 * Uses iText library for PDF generation and Selenium WebDriver for JavaScript execution.
 */
@Component
public class ITextPdfGenerator implements PdfGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ITextPdfGenerator.class);
    
    private final CssProcessor cssProcessor;

    public ITextPdfGenerator(CssProcessor cssProcessor) {
        this.cssProcessor = cssProcessor;
    }

    @Override
    public byte[] generatePdf(Path htmlFile, Path cssFile, String headerHtml,
        String footerHtml, String banglaFooterHtml, List<Path> fontFiles,
        String password, String jsEnable, PageOrientation orientation, boolean forceBrowserMode) {
        
        logger.debug("Starting PDF generation from files - HTML: {}, CSS: {}", htmlFile, cssFile);
        
        try {
            String htmlContent = readFileContent(htmlFile);
            String cssContent = cssFile != null ? readFileContent(cssFile) : "";
            Path resourceRoot = htmlFile != null ? htmlFile.getParent() : (cssFile != null ? cssFile.getParent() : null);
            
            return generatePdfInternal(htmlContent, cssContent, headerHtml, footerHtml,
                banglaFooterHtml, fontFiles, password, jsEnable, resourceRoot, orientation, forceBrowserMode);
                
        } catch (IOException e) {
            throw new FileProcessingException("Failed to read HTML or CSS file", e);
        }
    }

    @Override
    public byte[] generatePdfFromContent(String htmlContent, String cssContent,
        List<Path> fontFiles, String password, PageOrientation orientation, boolean forceBrowserMode) {
        
        logger.debug("Starting PDF generation from content strings");
        
        // For content-based generation, we need to determine the base directory from font files
        Path cssFile = null;
        if (fontFiles != null && !fontFiles.isEmpty()) {
            // Use the directory of the first font file as the base directory
            cssFile = fontFiles.get(0).getParent().resolve("styles.css");
            try {
                // Create a temporary CSS file in the same directory as assets
                if (cssContent != null && !cssContent.trim().isEmpty()) {
                    java.nio.file.Files.write(cssFile, cssContent.getBytes());
                }
            } catch (java.io.IOException e) {
                logger.warn("Failed to create temporary CSS file, proceeding without file-based CSS resolution", e);
                cssFile = null;
            }
        }
        
        Path resourceRoot = cssFile != null ? cssFile.getParent() : (fontFiles != null && !fontFiles.isEmpty() ? fontFiles.get(0).getParent() : null);

        return generatePdfInternal(htmlContent, cssContent != null ? cssContent : "",
            null, null, null, fontFiles, password, "false", resourceRoot, orientation, forceBrowserMode);
    }

    /**
     * Internal method that handles the core PDF generation logic.
     */
    private byte[] generatePdfInternal(String htmlContent, String cssContent,
            String headerHtml, String footerHtml, String banglaFooterHtml,
            List<Path> fontFiles, String password, String jsEnable, PageOrientation orientation,
            boolean forceBrowserMode) {

        return generatePdfInternal(htmlContent, cssContent, headerHtml, footerHtml,
            banglaFooterHtml, fontFiles, password, jsEnable, null, orientation, forceBrowserMode);
    }

    /**
     * Internal method that handles the core PDF generation logic with CSS file path.
     */
    private byte[] generatePdfInternal(String htmlContent, String cssContent,
        String headerHtml, String footerHtml, String banglaFooterHtml,
        List<Path> fontFiles, String password, String jsEnable, Path resourceRoot,
        PageOrientation orientation, boolean forceBrowserMode) {
        
        HtmlProcessingResult htmlResult = processHtmlContent(htmlContent, cssContent, jsEnable, orientation, forceBrowserMode);

        Exception lastException = null;
        for (HtmlVariant variant : htmlResult.getHtmlVariants()) {
            try {
                ConverterProperties converterProperties = setupConverterProperties(fontFiles, resourceRoot);
//                converterProperties.setTagWorkerFactory(new MarginSafeBookmarkTagWorkerFactory());
//                converterProperties.setTagWorkerFactory(new QRCodeTagWorkerFactory());

                converterProperties.setTagWorkerFactory(new DefaultTagWorkerFactory() {
                    private final MarginSafeBookmarkTagWorkerFactory bookmarkFactory = new MarginSafeBookmarkTagWorkerFactory();
                    private final QRCodeTagWorkerFactory qrFactory = new QRCodeTagWorkerFactory();

                    @Override
                    public ITagWorker getCustomTagWorker(IElementNode tag, ProcessorContext context) {
                        // 1. Try QR code first
                        ITagWorker qrWorker = qrFactory.getCustomTagWorker(tag, context);
                        if (qrWorker != null) return qrWorker;

                        // 2. Then try bookmark factory
                        ITagWorker bookmarkWorker = bookmarkFactory.getCustomTagWorker(tag, context);
                        if (bookmarkWorker != null) return bookmarkWorker;

                        // 3. Fallback to default behavior
                        return super.getCustomTagWorker(tag, context);
                    }
                });

        byte[] pdfBytes = convertHtmlToPdf(
                        variant.getHtml(),
                        converterProperties,
                        headerHtml,
                        footerHtml,
                        banglaFooterHtml,
            password,
            orientation
                );

                logger.debug("PDF generation completed using {} HTML variant, size: {} bytes",
                        variant.getDescription(), pdfBytes.length);
                return pdfBytes;
            } catch (Exception e) {
                lastException = e;
                logger.warn("PDF generation failed using {} HTML variant: {}",
                        variant.getDescription(), e.getMessage(), e);
            }
        }

    Throwable cause = lastException != null
        ? lastException
        : new IllegalStateException("No HTML variants were available for PDF generation");
    throw new PdfGenerationException("Failed to generate PDF", cause);
    }

    /**
     * Processes HTML content by applying CSS and optionally executing JavaScript.
     */
    private HtmlProcessingResult processHtmlContent(String htmlContent, String cssContent, String jsEnable,
            PageOrientation orientation, boolean forceBrowserMode) {
        if (forceBrowserMode) {
            logger.debug("Force browser mode enabled; bypassing CSS sanitization and margin-safe variants");
            String cssToApply = cssContent != null ? cssContent : "";
            String htmlWithCss = wrapHtmlWithCss(htmlContent, cssToApply, false);
            String browserRenderedHtml = htmlWithCss;

            try {
                browserRenderedHtml = executeJavaScript(htmlWithCss);
            } catch (Exception e) {
                logger.warn("Force browser mode JavaScript execution failed, using raw HTML", e);
            }

            if (browserRenderedHtml == null || browserRenderedHtml.trim().isEmpty()) {
                browserRenderedHtml = htmlWithCss;
            }

            List<HtmlVariant> variants = new ArrayList<>();
            variants.add(new HtmlVariant(browserRenderedHtml, "force-browser rendered"));
            variants.add(new HtmlVariant(htmlWithCss, "force-browser raw fallback"));
            return new HtmlProcessingResult(variants);
        }

        String processedCss = cssProcessor.processCss(cssContent, orientation);
        String sanitizedHtml = wrapHtmlWithCss(htmlContent, processedCss, true);
        String rawHtml = wrapHtmlWithCss(htmlContent, processedCss, false);

        if (isJavaScriptEnabled(jsEnable)) {
            logger.debug("JavaScript execution enabled, processing with Chrome driver");
            sanitizedHtml = executeJavaScript(sanitizedHtml);
            rawHtml = executeJavaScript(rawHtml);
        }

        sanitizedHtml = stripUnsupportedTags(sanitizedHtml);
        rawHtml = stripUnsupportedTags(rawHtml);
        String marginSafeHtml = stripUnsupportedTags(createMarginSafeVariant(sanitizedHtml));

        List<HtmlVariant> variants = new ArrayList<>();
        variants.add(new HtmlVariant(sanitizedHtml, "sanitized"));
        variants.add(new HtmlVariant(rawHtml, "raw fallback"));
        variants.add(new HtmlVariant(marginSafeHtml, "margin-safe fallback"));

        return new HtmlProcessingResult(variants);
    }

    private byte[] convertHtmlToPdf(String html,
            ConverterProperties converterProperties,
            String headerHtml,
            String footerHtml,
            String banglaFooterHtml,
            String password,
            PageOrientation orientation) throws Exception {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = createPdfWriter(outputStream, password);
            try (PdfDocument pdfDocument = new PdfDocument(writer)) {
                configureDefaultPageSize(pdfDocument, orientation);
                configureEventHandlers(pdfDocument, headerHtml, footerHtml, banglaFooterHtml);

                logger.debug("Converting HTML to PDF with bookmark support");
                Document document = HtmlConverter.convertToDocument(html, pdfDocument, converterProperties);
                applyOrientation(pdfDocument, orientation);
                int numberOfPages = pdfDocument.getNumberOfPages();
                document.close();


            }

            return outputStream.toByteArray();
        }
    }

    private void applyOrientation(PdfDocument pdfDocument, PageOrientation orientation) {
        if (orientation == null || orientation == PageOrientation.PORTRAIT) {
            return;
        }

        if (pdfDocument == null || pdfDocument.isClosed()) {
            logger.debug("Skipping orientation adjustment because PdfDocument is null or already closed");
            return;
        }

        if (orientation == PageOrientation.LANDSCAPE) {
            logger.debug("LANDSCAPE orientation handled via page size; skipping page rotation");
            return;
        }

        int rotation = orientation.getRotationDegrees();
        int totalPages = pdfDocument.getNumberOfPages();
        for (int i = 1; i <= totalPages; i++) {
            pdfDocument.getPage(i).setRotation(rotation);
        }
        logger.debug("Applied {} orientation ({}°) to {} pages", orientation.name(), rotation, totalPages);
    }

    private void configureDefaultPageSize(PdfDocument pdfDocument, PageOrientation orientation) {
        if (pdfDocument == null || pdfDocument.isClosed() || orientation == null) {
            return;
        }

        PageSize targetSize = switch (orientation) {
            case LANDSCAPE, SEASCAPE -> PageSize.A4.rotate();
            default -> PageSize.A4;
        };

        PageSize currentDefault = pdfDocument.getDefaultPageSize();
        if (currentDefault == null || !currentDefault.equals(targetSize)) {
            pdfDocument.setDefaultPageSize(targetSize);
            logger.debug("Set default page size to {} for orientation {}", targetSize, orientation);
        }
    }

    /**
     * Sets up converter properties including font providers.
     */
    private ConverterProperties setupConverterProperties(List<Path> fontFiles, Path resourceRoot) {
        ConverterProperties converterProperties = new ConverterProperties();
        
        if (resourceRoot != null) {
            String baseUri = resourceRoot.toUri().toString();
            converterProperties.setBaseUri(baseUri);
            logger.debug("Set base URI to: {}", baseUri);
        } else {
            String tempDir = System.getProperty("java.io.tmpdir");
            if (tempDir != null) {
                String baseUri = "file://" + (tempDir.endsWith("/") ? tempDir : tempDir + "/");
                converterProperties.setBaseUri(baseUri);
                logger.debug("Set base URI to temp directory fallback: {}", baseUri);
            }
        }
        

        converterProperties.setCharset(StandardCharsets.UTF_8.name());

        // Enable automatic bookmark generation based on heading hierarchy
        converterProperties.setOutlineHandler(OutlineHandler.createStandardHandler());
        
        if (fontFiles != null && !fontFiles.isEmpty()) {
            logger.debug("Setting up font provider with {} fonts", fontFiles.size());
            FontProvider fontProvider = createFontProvider(fontFiles);
            converterProperties.setFontProvider(fontProvider);
        }
        
        return converterProperties;
    }

    /**
     * Creates a font provider with the specified font files.
     */
    private FontProvider createFontProvider(List<Path> fontFiles) {
//        FontProvider fontProvider = new FontProvider();
//        for (Path fontFile : fontFiles) {
//            try {
//                logger.debug("Adding font: {}", fontFile);
//                fontProvider.addFont(fontFile.toString());
//            } catch (Exception e) {
//                logger.warn("Failed to add font: {}, continuing without it", fontFile, e);
//            }
//        }

        FontSet fontSet = new FontSet();
        for (Path fontFile : fontFiles) {
            fontSet.addFont(fontFile.toString());
        }
        FontProvider fontProvider = new FontProvider(fontSet);

        return fontProvider;
    }

    /**
     * Creates a PDF writer with optional password encryption.
     */
    private PdfWriter createPdfWriter(ByteArrayOutputStream outputStream, String password) {
        try {
            if (password != null && !password.trim().isEmpty()) {
                logger.debug("Creating encrypted PDF writer");
                return new PdfWriter(outputStream, new WriterProperties().setStandardEncryption(
                    password.getBytes(), null,
                    EncryptionConstants.ALLOW_PRINTING, EncryptionConstants.ENCRYPTION_AES_256));
            } else {
                logger.debug("Creating standard PDF writer");
                return new PdfWriter(outputStream);
            }
        } catch (Exception e) {
            throw new PdfGenerationException("Failed to create PDF writer", e);
        }
    }

    /**
     * Configures event handlers for headers and footers.
     */
    private void configureEventHandlers(PdfDocument pdfDocument, String headerHtml, 
            String footerHtml, String banglaFooterHtml) {
        
        if (headerHtml != null && !headerHtml.trim().isEmpty()) {
            logger.debug("Adding header event handler");
            pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, new Header(headerHtml));
        }

        if (footerHtml != null && !footerHtml.trim().isEmpty()) {
            logger.debug("Adding footer event handler");
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new Footer(footerHtml));
        }

        else if (banglaFooterHtml != null && !banglaFooterHtml.trim().isEmpty()) {
            logger.debug("Adding Bengali footer event handler");
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new BengaliPageNumberHandler(banglaFooterHtml));
        }
    }


    private String executeJavaScript(String htmlContent) {
        ChromeDriver driver = null;
        Path tempHtmlFile = null;
        
        try {
            logger.debug("Setting up Chrome WebDriver for JavaScript execution");
            WebDriverManager.chromedriver().setup();
            

            tempHtmlFile = java.nio.file.Files.createTempFile("js-execution", ".html");
            java.nio.file.Files.write(tempHtmlFile, htmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");
            
            driver = new ChromeDriver(options);

            String fileUrl = tempHtmlFile.toUri().toString();
            logger.debug("Loading HTML file for JavaScript execution: {}", fileUrl);
            driver.navigate().to(fileUrl);

            String processedHtml = (String) driver.executeScript("return document.documentElement.outerHTML;");
            logger.debug("JavaScript execution completed successfully, processed HTML length: {}", 
                        processedHtml != null ? processedHtml.length() : 0);
            
            return processedHtml;
        } catch (Exception e) {
            logger.error("Failed to execute JavaScript in HTML content", e);
            throw new PdfGenerationException("Failed to execute JavaScript in HTML content", e);
        } finally {
            // Clean up resources
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.warn("Failed to close Chrome driver", e);
                }
            }
            
            // Clean up temporary file
            if (tempHtmlFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempHtmlFile);
                } catch (Exception e) {
                    logger.warn("Failed to delete temporary HTML file: {}", tempHtmlFile, e);
                }
            }
        }
    }

    /**
     * Reads content from a file.
     */
    private String readFileContent(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * Wraps HTML content with CSS styling and applies defensive measures against margin collapse.
     */
    private String wrapHtmlWithCss(String htmlContent, String cssContent, boolean sanitize) {
        // Clean and normalize the HTML content
        String cleanHtmlContent = htmlContent != null ? htmlContent.trim() : "";

        if (sanitize) {
            // Apply defensive HTML processing to prevent margin collapse issues
            cleanHtmlContent = sanitizeHtmlForMarginCollapse(cleanHtmlContent);
        }
        
        // If the content is already a complete HTML document, return it with CSS injected
        if (cleanHtmlContent.toLowerCase().contains("<html") && cleanHtmlContent.toLowerCase().contains("</html>")) {
            // Insert CSS into existing HTML structure
            if (cleanHtmlContent.toLowerCase().contains("<head>")) {
                return cleanHtmlContent.replaceFirst("(?i)<head>", "<head><style>" + cssContent + "</style>");
            } else if (cleanHtmlContent.toLowerCase().contains("<html>")) {
                return cleanHtmlContent.replaceFirst("(?i)<html>", "<html><head><style>" + cssContent + "</style></head>");
            }
        }
        
        // Wrap partial HTML content with defensive structure
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>" + cssContent + "</style></head><body><div class=\"pdf-content-wrapper\">" + cleanHtmlContent + "</div></body></html>";
    }

    private static final String MARGIN_SAFE_STYLE_BLOCK = """
        <style id=\"margin-collapse-fallback\">
            .pdf-content-wrapper.margin-safe,
            .pdf-content-wrapper.margin-safe * {
                margin-top: 0 !important;
                margin-bottom: 0 !important;
            }

            .pdf-content-wrapper.margin-safe section,
            .pdf-content-wrapper.margin-safe header,
            .pdf-content-wrapper.margin-safe footer,
            .pdf-content-wrapper.margin-safe article,
            .pdf-content-wrapper.margin-safe .section {
                padding-top: 1.25rem !important;
                padding-bottom: 1.25rem !important;
                border-top: 0.1pt solid transparent !important;
                border-bottom: 0.1pt solid transparent !important;
            }

            .pdf-content-wrapper.margin-safe h1,
            .pdf-content-wrapper.margin-safe h2,
            .pdf-content-wrapper.margin-safe h3,
            .pdf-content-wrapper.margin-safe h4,
            .pdf-content-wrapper.margin-safe h5,
            .pdf-content-wrapper.margin-safe h6,
            .pdf-content-wrapper.margin-safe p {
                padding-top: 0.35rem !important;
                padding-bottom: 0.35rem !important;
            }
        </style>
        """;

    private String createMarginSafeVariant(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return htmlContent;
        }

        String result = htmlContent;

        if (!result.contains("pdf-content-wrapper margin-safe")) {
            result = result.replaceFirst("<div\\s+class=\"pdf-content-wrapper", "<div class=\"pdf-content-wrapper margin-safe");
        }

        if (result.toLowerCase().contains("margin-collapse-fallback")) {
            return result;
        }

        if (result.toLowerCase().contains("</head>")) {
            return result.replaceFirst("(?i)</head>", MARGIN_SAFE_STYLE_BLOCK + "</head>");
        }

        return MARGIN_SAFE_STYLE_BLOCK + result;
    }

    private String stripUnsupportedTags(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return htmlContent;
        }

        return htmlContent.replaceAll("(?is)<script[^>]*>.*?</script>", "");
    }

    private static class HtmlProcessingResult {
        private final List<HtmlVariant> variants;

        HtmlProcessingResult(List<HtmlVariant> variants) {
            this.variants = variants != null ? variants : new ArrayList<>();
        }

        List<HtmlVariant> getHtmlVariants() {
            List<HtmlVariant> orderedVariants = new ArrayList<>();
            Set<String> seenHtml = new HashSet<>();

            for (HtmlVariant variant : variants) {
                if (variant != null && variant.isValid() && seenHtml.add(variant.getHtml())) {
                    orderedVariants.add(variant);
                }
            }

            return orderedVariants;
        }
    }

    private static class HtmlVariant {
        private final String html;
        private final String description;

        HtmlVariant(String html, String description) {
            this.html = html;
            this.description = description;
        }

        boolean isValid() {
            return html != null && !html.trim().isEmpty();
        }

        String getHtml() {
            return html;
        }

        String getDescription() {
            return description != null ? description : "html variant";
        }
    }

    /**
     * Sanitizes HTML content to prevent margin collapse issues in iText.
     */
    private String sanitizeHtmlForMarginCollapse(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return htmlContent;
        }
        
        logger.debug("Applying HTML sanitization to prevent margin collapse issues");
        
        String sanitized = htmlContent;
        
        // Replace problematic list structures that can cause margin collapse
        sanitized = sanitized
            // Wrap list items in divs to prevent margin collapse
            .replaceAll("(<li[^>]*>)", "$1<div class=\"li-wrapper\">")
            .replaceAll("(</li>)", "</div>$1")
            // Add wrapper divs around complex nested structures
            .replaceAll("(<(?:section|article|aside|nav)[^>]*>)", "$1<div class=\"section-wrapper\">")
            .replaceAll("(</(?:section|article|aside|nav)>)", "</div>$1")
            // Simplify complex margin-causing elements
            .replaceAll("(<(?:blockquote|figure|figcaption)[^>]*>)", "<div class=\"block-wrapper\">")
            .replaceAll("(</(?:blockquote|figure|figcaption)>)", "</div>")
            // Remove problematic attributes that can cause layout issues
            .replaceAll("\\s+style\\s*=\\s*[\"'][^\"']*(?:margin|padding|height|position|transform|animation)[^\"']*[\"']", "")
            // Remove class attributes that might reference problematic CSS
            .replaceAll("\\s+class\\s*=\\s*[\"'][^\"']*(?:animation|transition|transform|flex|grid)[^\"']*[\"']", "");
        
        return sanitized;
    }

    /**
     * Checks if JavaScript execution is enabled.
     */
    private boolean isJavaScriptEnabled(String jsEnable) {
        return "true".equalsIgnoreCase(jsEnable);
    }

    /**
     * Custom tag worker factory combining bookmark support with margin collapse protection.
     */
    private static class MarginSafeBookmarkTagWorkerFactory extends DefaultTagWorkerFactory {
        private static final Set<String> MARGIN_SAFE_BLOCK_TAGS = Set.of(
            "div", "section", "article", "main", "header", "footer", "nav", "aside"
        );

        @Override
        public ITagWorker getCustomTagWorker(IElementNode tag, ProcessorContext context) {
            String tagName = tag.name();
            String normalized = tagName != null ? tagName.toLowerCase(Locale.ROOT) : "";

            if (normalized.matches("h[1-6]")) {
                return new BookmarkTagWorker(tag, context);
            }

            if ("body".equals(normalized)) {
                return new MarginSafeBodyTagWorker(tag, context);
            }

            if (MARGIN_SAFE_BLOCK_TAGS.contains(normalized)) {
                return new MarginSafeDivTagWorker(tag, context);
            }

            return super.getCustomTagWorker(tag, context);
        }
    }

    /**
     * Prevent margin collapsing on common block-level containers.
     */
    private static class MarginSafeDivTagWorker extends DivTagWorker {
        public MarginSafeDivTagWorker(IElementNode element, ProcessorContext context) {
            super(element, context);
        }

        @Override
        public void processEnd(IElementNode element, ProcessorContext context) {
            super.processEnd(element, context);
            disableMarginCollapse(getElementResult());
        }
    }

    private static class MarginSafeBodyTagWorker extends BodyTagWorker {
        public MarginSafeBodyTagWorker(IElementNode element, ProcessorContext context) {
            super(element, context);
        }

        @Override
        public void processEnd(IElementNode element, ProcessorContext context) {
            super.processEnd(element, context);
            disableMarginCollapse(getElementResult());
        }
    }

    /**
     * Custom tag worker for creating bookmarks from heading elements.
     */
    private static class BookmarkTagWorker extends HTagWorker {
        private PdfOutline outline;

        public BookmarkTagWorker(IElementNode element, ProcessorContext context) {
            super(element, context);
        }

        @Override
        public void processEnd(IElementNode element, ProcessorContext context) {
            super.processEnd(element, context);
            disableMarginCollapse(getElementResult());

            String headingText = getHeadingText(element);
            if (headingText != null && !headingText.trim().isEmpty()) {
                createBookmark(headingText, context);
            }
        }

        private String getHeadingText(IElementNode element) {
            StringBuilder text = new StringBuilder();
            extractText(element, text);
            return text.toString().trim();
        }

        private void extractText(IElementNode node, StringBuilder text) {
            if (node.childNodes() != null) {
                for (com.itextpdf.styledxmlparser.node.INode child : node.childNodes()) {
                    if (child instanceof com.itextpdf.styledxmlparser.node.ITextNode) {
                        text.append(((com.itextpdf.styledxmlparser.node.ITextNode) child).wholeText());
                    } else if (child instanceof IElementNode) {
                        extractText((IElementNode) child, text);
                    }
                }
            }
        }

        private void createBookmark(String title, ProcessorContext context) {
            try {
                PdfDocument pdfDocument = context.getPdfDocument();
                if (pdfDocument != null && pdfDocument.getNumberOfPages() > 0) {
                    PdfOutline rootOutline = pdfDocument.getOutlines(false);
                    if (rootOutline == null) {
                        rootOutline = pdfDocument.getOutlines(true);
                    }

                    PdfDestination destination = PdfExplicitDestination.createFitH(
                        pdfDocument.getLastPage(),
                        pdfDocument.getLastPage().getPageSize().getTop()
                    );

                    outline = rootOutline.addOutline(title);
                    outline.addDestination(destination);

                    logger.debug("Created bookmark: {}", title);
                } else {
                    logger.debug("Skipping bookmark creation for '{}' because no pages are available yet", title);
                }
            } catch (Exception e) {
                logger.warn("Failed to create bookmark for: {}", title, e);
            }
        }
    }

    private static void disableMarginCollapse(Object element) {
        if (element instanceof IPropertyContainer) {
            ((IPropertyContainer) element).setProperty(Property.COLLAPSING_MARGINS, Boolean.FALSE);
        }
    }

}