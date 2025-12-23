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

        // Wait for page to be fully loaded
        await page.evaluate(() => {
            return new Promise((resolve) => {
                if (document.readyState === 'complete') {
                    resolve();
                } else {
                    window.addEventListener('load', resolve);
                }
            });
        });

        console.log(`Page fully loaded in ${Date.now() - startLoad}ms`);

        // Wait for fonts to load (with short timeout - don't block too long)
        try {
            await Promise.race([
                page.evaluate(() => document.fonts.ready),
                new Promise(resolve => setTimeout(resolve, 3000))
            ]);
        } catch (e) {
            console.log('Font loading skipped:', e.message);
        }

        // Performance: Optimize images before PDF generation
        await page.evaluate(() => {
            // Lazy load images that aren't visible
            const images = document.querySelectorAll('img');
            images.forEach(img => {
                if (!img.complete) {
                    img.loading = 'eager'; // Force load
                }
            });
        });

        // Small delay for any remaining async operations
        await new Promise(resolve => setTimeout(resolve, 500));

        // PDF options
        const pdfOptions = {
            path: config.outputPath,
            format: config.format || 'A4',
            landscape: config.landscape || false,
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
