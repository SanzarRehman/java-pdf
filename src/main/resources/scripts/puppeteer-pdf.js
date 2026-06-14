/**
 * Puppeteer PDF Generation Script (Optimized for Performance)
 * 
 * This script is used by ChromiumPdfRenderer to generate PDFs using headless Chromium.
 * Works in both local development (puppeteer) and Docker (puppeteer-core).
 * 
 * Usage: node puppeteer-pdf.js <config-file-path>
 * 
 * Config file format (JSON):
 * {
 *   "htmlPath": "/path/to/input.html",
 *   "outputPath": "/path/to/output.pdf",
 *   "format": "A4",
 *   "landscape": false,
 *   "printBackground": true,
 *   "margin": { "top": "20px", "right": "20px", "bottom": "20px", "left": "20px" }
 * }
 * 
 * Requirements:
 *   Local: npm install puppeteer
 *   Docker: npm install puppeteer-core (Chromium installed via apk)
 */

// Try puppeteer-core first (Docker), fallback to puppeteer (local dev)
let puppeteer;
try {
    puppeteer = require('puppeteer-core');
} catch (e) {
    puppeteer = require('puppeteer');
}

const fs = require('fs');
const path = require('path');
const os = require('os');

// Detect environment
const isDocker = fs.existsSync('/.dockerenv') || process.env.PUPPETEER_EXECUTABLE_PATH;
const isMacOS = os.platform() === 'darwin';
const cpuCount = os.cpus().length;

// Chromium executable path - check multiple locations
const CHROME_PATH = process.env.PUPPETEER_EXECUTABLE_PATH || 
                    process.env.CHROME_PATH ||
                    process.env.CHROME_BIN ||
                    (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : null) ||
                    (fs.existsSync('/usr/bin/chromium-browser') ? '/usr/bin/chromium-browser' : null) ||
                    (fs.existsSync('/usr/bin/google-chrome') ? '/usr/bin/google-chrome' : null);

console.log(`Platform: ${os.platform()}, CPUs: ${cpuCount}, Memory: ${Math.round(os.totalmem() / 1024 / 1024 / 1024)}GB`);
console.log(`Environment: ${isDocker ? 'Docker' : 'Local'}, Chrome: ${CHROME_PATH || 'bundled'}`);

// Paper width in CSS pixels (96px/in) for supported page formats, portrait + landscape.
const PAPER_WIDTH_IN = {
    A3: { portrait: 11.69, landscape: 16.54 },
    A4: { portrait: 8.27, landscape: 11.69 },
    A5: { portrait: 5.83, landscape: 8.27 },
    LETTER: { portrait: 8.5, landscape: 11 },
    LEGAL: { portrait: 8.5, landscape: 14 },
};

/** Parse a CSS margin string (e.g. "20px", "0.5in") into CSS pixels. */
function parsePxMargin(value, fallback) {
    if (value == null) return fallback;
    const m = String(value).match(/([\d.]+)\s*(px|in|cm|mm)?/i);
    if (!m) return fallback;
    const num = parseFloat(m[1]);
    if (isNaN(num)) return fallback;
    switch ((m[2] || 'px').toLowerCase()) {
        case 'in': return num * 96;
        case 'cm': return num * 37.7952755906;
        case 'mm': return num * 3.7795275591;
        default: return num;
    }
}

/**
 * Compute a print scale that emulates wkhtmltopdf "smart shrinking": shrink content so
 * over-wide elements (e.g. wide tables) fit within the printable page width.
 * Returns a scale in Chromium's allowed range [0.1, 2.0]. Shrink-only (never enlarges).
 */
async function computeFitScale(page, config) {
    // Explicit override always wins.
    if (typeof config.scale === 'number' && config.scale > 0) {
        const s = Math.min(2, Math.max(0.1, config.scale));
        console.log(`Using explicit scale override: ${s}`);
        return s;
    }

    // Default ON unless explicitly disabled.
    if (config.fitToWidth === false) {
        return 1;
    }

    try {
        // Measure under print layout so the result matches the generated PDF.
        await page.emulateMediaType('print');

        const landscape = config.landscape || false;
        const format = (config.format || 'A4').toUpperCase();
        const paper = PAPER_WIDTH_IN[format] || PAPER_WIDTH_IN.A4;
        const widthIn = landscape ? paper.landscape : paper.portrait;

        const marginLeftPx = parsePxMargin(config.margin && config.margin.left, 20);
        const marginRightPx = parsePxMargin(config.margin && config.margin.right, 20);
        const printableWidthPx = (widthIn * 96) - marginLeftPx - marginRightPx;

        const contentWidthPx = await page.evaluate(() => {
            const docEl = document.documentElement;
            const body = document.body;
            let max = Math.max(
                docEl ? docEl.scrollWidth : 0,
                body ? body.scrollWidth : 0
            );
            for (const t of document.querySelectorAll('table')) {
                max = Math.max(max, t.scrollWidth, t.offsetWidth);
            }
            return max;
        });

        let scale = 1;
        if (contentWidthPx > printableWidthPx && contentWidthPx > 0) {
            scale = Math.max(0.1, Math.min(1, printableWidthPx / contentWidthPx));
        }
        console.log(`Fit-to-width: printableWidth=${printableWidthPx.toFixed(1)}px contentWidth=${contentWidthPx}px scale=${scale.toFixed(4)}`);
        return scale;
    } catch (e) {
        console.warn(`Fit-to-width computation failed, using scale=1: ${e && e.message ? e.message : e}`);
        return 1;
    }
}

async function generatePdf() {
    const configPath = process.argv[2];
    
    if (!configPath) {
        console.error('Usage: node puppeteer-pdf.js <config-file-path>');
        process.exit(1);
    }

    if (!fs.existsSync(configPath)) {
        console.error(`Config file not found: ${configPath}`);
        process.exit(1);
    }

    let config;
    try {
        config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    } catch (e) {
        console.error(`Failed to parse config file: ${e.message}`);
        process.exit(1);
    }

    // Validate required fields
    if (!config.htmlPath) {
        console.error('Config missing required field: htmlPath');
        process.exit(1);
    }
    if (!config.outputPath) {
        console.error('Config missing required field: outputPath');
        process.exit(1);
    }

    console.log(`Generating PDF from: ${config.htmlPath}`);
    console.log(`Output: ${config.outputPath}`);

    // Build Chrome args - optimized for headless PDF generation in Docker
    const chromeArgs = [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-web-security',
        '--allow-file-access-from-files',
        '--disable-extensions',
        '--disable-background-networking',
        '--disable-sync',
        '--disable-translate',
        '--metrics-recording-only',
        '--no-first-run',
        '--disable-default-apps',
        '--mute-audio',
        '--hide-scrollbars',
        
        // Disable GPU/graphics - required for Docker/containers
        '--disable-gpu',
        '--disable-software-rasterizer',
        '--disable-vulkan',
        '--disable-accelerated-2d-canvas',
        '--disable-canvas-aa',
        '--disable-2d-canvas-clip-aa',
        '--disable-gl-drawing-for-tests',
        '--use-gl=swiftshader',
        '--no-zygote',
        
        // Performance optimizations
        '--disable-hang-monitor',
        '--disable-prompt-on-repost',
        '--disable-domain-reliability',
        '--disable-component-update',
        '--disable-breakpad',
        '--disable-ipc-flooding-protection',
        
        // Memory optimizations for large documents
        '--js-flags=--max-old-space-size=4096',
        '--disable-features=site-per-process,IsolateOrigins',
        '--disable-site-isolation-trials',
        
        // Rendering optimizations
        '--font-render-hinting=none',
        '--disable-lcd-text',
        '--run-all-compositor-stages-before-draw',
        
        // Stability in containers
        '--disable-background-timer-throttling',
        '--disable-backgrounding-occluded-windows',
        '--disable-renderer-backgrounding',
    ];

    console.log('Starting Chromium in headless mode...');

    let browser;
    try {
        const launchOptions = {
            headless: 'new',
            args: chromeArgs,
            timeout: 600000,           // 10 minutes browser launch timeout
            protocolTimeout: 600000    // 10 minutes for Chrome DevTools Protocol calls (PDF generation)
        };
        
        // Use system Chromium in Docker
        if (CHROME_PATH) {
            launchOptions.executablePath = CHROME_PATH;
        }
        
        browser = await puppeteer.launch(launchOptions);

        const page = await browser.newPage();
        
        // Set default timeout for all operations to 10 minutes
        page.setDefaultTimeout(600000);
        page.setDefaultNavigationTimeout(600000);

        // Performance: Disable unnecessary features
        await page.setRequestInterception(true);
        page.on('request', (request) => {
            // Block external resources that slow things down
            const resourceType = request.resourceType();
            const url = request.url();
            
            // Allow local files and data URIs
            if (url.startsWith('file://') || url.startsWith('data:')) {
                request.continue();
                return;
            }
            
            // Block external tracking, analytics, and unnecessary resources
            if (['media', 'websocket', 'manifest', 'other'].includes(resourceType)) {
                request.abort();
            } else {
                request.continue();
            }
        });

        // Enable caching for better performance
        await page.setCacheEnabled(true);

        // Set viewport - use 1x scale for faster rendering (2x doubles work)
        // Only use 2x if high quality is needed
        const deviceScaleFactor = config.highQuality ? 2 : 1;
        await page.setViewport({
            width: 1200,
            height: 1600,
            deviceScaleFactor: deviceScaleFactor
        });

        console.log(`Device scale factor: ${deviceScaleFactor}x`);

        // Load HTML file
        const htmlUrl = `file://${path.resolve(config.htmlPath)}`;
        console.log(`Loading: ${htmlUrl}`);
        
        const startLoad = Date.now();
        
        // For local files, use simpler wait strategy with longer timeout
        await page.goto(htmlUrl, {
            waitUntil: 'domcontentloaded',
            timeout: 600000
        });

        console.log(`DOM loaded in ${Date.now() - startLoad}ms`);

        // Optional: inject CSS provided via config without rewriting HTML.
        if (config.cssContent && String(config.cssContent).trim().length > 0) {
            try {
                console.log(`Injecting cssContent (${String(config.cssContent).length} chars)`);
                await page.addStyleTag({ content: String(config.cssContent) });
            } catch (e) {
                console.warn(`Failed to inject cssContent: ${e && e.message ? e.message : e}`);
            }
        }

        // Avoid waiting on the full window 'load' event by default.
        // For large pages (many images/fonts), waiting for 'load' can be very slow.
        // Instead, use bounded waits that cover the common cases.

        const fastMode = config.fastMode === true;

        // Fonts (bounded)
        try {
            await Promise.race([
                page.evaluate(() => (document.fonts ? document.fonts.ready : Promise.resolve())),
                new Promise(resolve => setTimeout(resolve, fastMode ? 500 : 2000))
            ]);
        } catch (e) {
            // ignore
        }

        // Images (bounded)
        try {
            await page.evaluate(() => {
                const imgs = Array.from(document.images || []);
                for (const img of imgs) {
                    try { img.loading = 'eager'; } catch (_) {}
                }
            });

            await Promise.race([
                page.waitForFunction(
                    () => {
                        const imgs = Array.from(document.images || []);
                        return imgs.every(i => i.complete);
                    },
                    { timeout: fastMode ? 500 : 3000 }
                ),
                new Promise(resolve => setTimeout(resolve, fastMode ? 500 : 3000))
            ]);
        } catch (e) {
            // ignore
        }

        // Small delay for any remaining microtasks/async layout
        await new Promise(resolve => setTimeout(resolve, fastMode ? 50 : 150));

        // Auto fit-to-width (wkhtmltopdf smart-shrinking equivalent).
        const scale = await computeFitScale(page, config);

        // PDF options
        const pdfOptions = {
            path: config.outputPath,
            format: config.format || 'A4',
            landscape: config.landscape || false,
            scale: scale,
            printBackground: config.printBackground !== false,
            preferCSSPageSize: config.preferCSSPageSize || false,
            displayHeaderFooter: false,
            margin: config.margin || {
                top: '20px',
                right: '20px',
                bottom: '20px',
                left: '20px'
            },
            timeout: 600000  // 10 minutes for PDF generation
        };

        // Generate PDF with progress logging
        console.log('Starting PDF generation...');
        const startPdf = Date.now();
        
        await page.pdf(pdfOptions);

        const pdfTime = Date.now() - startPdf;
        const stats = fs.statSync(config.outputPath);
        const sizeMB = (stats.size / 1024 / 1024).toFixed(2);
        
        console.log(`PDF generated successfully: ${sizeMB}MB in ${(pdfTime / 1000).toFixed(1)}s`);
        console.log(`Total time: ${((Date.now() - startLoad) / 1000).toFixed(1)}s`);

    } catch (error) {
        console.error(`PDF generation failed: ${error.message}`);
        if (error.stack) {
            console.error(error.stack);
        }
        process.exit(1);
    } finally {
        if (browser) {
            await browser.close();
        }
    }
}

// Run the script
generatePdf().catch(err => {
    console.error('Unexpected error:', err.message);
    process.exit(1);
});
