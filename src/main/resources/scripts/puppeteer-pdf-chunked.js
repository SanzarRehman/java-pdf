/**
 * Chunked Puppeteer PDF Generation Script
 * 
 * Optimized for VERY LARGE HTML documents (50MB+)
 * Splits HTML into chunks at SAFE boundaries (complete elements only), 
 * generates PDFs in parallel, then merges.
 * 
 * Works in both local development (puppeteer) and Docker (puppeteer-core).
 * 
 * Usage: node puppeteer-pdf-chunked.js <config-file-path>
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
const { execSync } = require('child_process');

// Detect environment
const isDocker = fs.existsSync('/.dockerenv') || process.env.PUPPETEER_EXECUTABLE_PATH;
const cpuCount = os.cpus().length;
const LOW_MEMORY_MODE = process.env.LOW_MEMORY_MODE === 'true' || process.env.LOW_MEMORY === 'true';
const DEFAULT_CHUNK_SIZE_MB = LOW_MEMORY_MODE ? 1 : 5; // New default chunk size
// Default parallelism tuned to avoid OOM spikes; can be overridden via config.parallelism
const DEFAULT_MAX_PARALLEL = LOW_MEMORY_MODE ? 1 : Math.min(4, Math.max(1, cpuCount));
const HARD_MAX_CHUNK_MB = 20; // Guardrail: prevent runaway memory from huge chunk requests
const HARD_MAX_PARALLEL = LOW_MEMORY_MODE ? 1 : 8; // Hard cap to avoid runaway memory
let CURRENT_CHUNK_SIZE_MB = DEFAULT_CHUNK_SIZE_MB;
let CURRENT_MAX_PARALLEL = DEFAULT_MAX_PARALLEL;
const MAX_RETRIES = 2; // Reduced retries
const USE_BROWSER_POOL = false; // MEMORY FIX: Never use browser pool - one browser per chunk

// Chromium executable path - check multiple locations
const CHROME_PATH = process.env.PUPPETEER_EXECUTABLE_PATH || 
                    process.env.CHROME_PATH ||
                    process.env.CHROME_BIN ||
                    (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : null) ||
                    (fs.existsSync('/usr/bin/chromium-browser') ? '/usr/bin/chromium-browser' : null) ||
                    (fs.existsSync('/usr/bin/google-chrome') ? '/usr/bin/google-chrome' : null);

console.log(`Chunked PDF Generator defaults: CPUs=${cpuCount}, Parallel=${DEFAULT_MAX_PARALLEL}, ChunkSize=${DEFAULT_CHUNK_SIZE_MB}MB${LOW_MEMORY_MODE ? ' [LOW MEMORY MODE]' : ''}`);
console.log(`Environment: ${isDocker ? 'Docker' : 'Local'}, Chrome: ${CHROME_PATH || 'bundled'}`);

if (!global.gc) {
    console.log('Tip: run Node with --expose-gc for better memory reclamation');
}

const LOG_MEMORY = process.env.LOG_MEMORY === 'true' || process.env.DEBUG_MEMORY === 'true';

function bytesToMb(bytes) {
    return (bytes / 1024 / 1024).toFixed(1);
}

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
 * Determine the print scale. Fit-to-width auto-shrinking is not supported; only an
 * explicit scale override is honored (Chromium's allowed range is [0.1, 2.0]).
 */
async function computeFitScale(page, config) {
    // Print media must be emulated so the layout matches the generated PDF.
    await page.emulateMediaType('print');

    if (typeof config.scale === 'number' && config.scale > 0) {
        return Math.min(2, Math.max(0.1, config.scale));
    }

    return 1;
}

function logMemory(label) {
    if (!LOG_MEMORY) return;
    const m = process.memoryUsage();
    console.log(
        `[mem] ${label} rss=${bytesToMb(m.rss)}MB heapUsed=${bytesToMb(m.heapUsed)}MB heapTotal=${bytesToMb(m.heapTotal)}MB external=${bytesToMb(m.external)}MB arrayBuffers=${bytesToMb(m.arrayBuffers || 0)}MB`
    );
}

// Browser pool removed - each worker reuses a single lightweight browser + page

function getBrowserLaunchOptions() {
    return {
        headless: 'new',
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
            '--disable-dev-shm-usage',
            '--disable-gpu',
            '--disable-software-rasterizer',
            '--disable-vulkan',
            '--disable-extensions',
            '--disable-background-networking',
            '--no-first-run',
            '--disable-default-apps',
            '--disable-features=site-per-process,IsolateOrigins',
            '--disable-site-isolation-trials',
            '--disable-web-security',
            '--no-zygote',
            '--disable-accelerated-2d-canvas',
            '--use-gl=swiftshader',
            '--js-flags=--max-old-space-size=256', // MEMORY FIX: Reduced from 512MB to 256MB
            '--single-process',
            '--disable-background-timer-throttling',
            '--disable-backgrounding-occluded-windows',
            '--disable-renderer-backgrounding',
            '--disable-features=TranslateUI',
            '--disable-ipc-flooding-protection',
            '--disable-hang-monitor',
        ],
        executablePath: CHROME_PATH || undefined,
        timeout: 180000,
        protocolTimeout: 300000
    };
};

async function launchBrowser() {
    const launchOptions = getBrowserLaunchOptions();
    return puppeteer.launch(launchOptions);
}

function isJsEnabled(config) {
    const value = config?.jsEnable ?? config?.jsEnabled;
    if (typeof value === 'boolean') return value;
    if (typeof value === 'string') return value.toLowerCase() === 'true';
    return false;
}

async function preparePageForChunking(page, config) {
    page.setDefaultTimeout(90000);
    page.setDefaultNavigationTimeout(90000);
    await page.setViewport({ width: 800, height: 600, deviceScaleFactor: 1 });

    // Request interception is one of the biggest levers for memory and speed.
    // Keep CSS + fonts; allow scripts only if JS is explicitly enabled.
    const allowScripts = isJsEnabled(config);
    await page.setRequestInterception(true);
    page.removeAllListeners('request');
    page.on('request', (request) => {
        const url = request.url();
        if (url.startsWith('file://') || url.startsWith('data:')) {
            request.continue();
            return;
        }

        const resourceType = request.resourceType();
        if (resourceType === 'document' || resourceType === 'stylesheet' || resourceType === 'font') {
            request.continue();
            return;
        }

        if (allowScripts && resourceType === 'script') {
            request.continue();
            return;
        }

        request.abort();
    });
}

async function generatePdf() {
    const configPath = process.argv[2];
    
    if (!configPath) {
        console.error('Usage: node puppeteer-pdf-chunked.js <config-file-path>');
        process.exit(1);
    }

    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    logMemory('after config load');
    // Apply per-request tuning overrides if provided
    CURRENT_CHUNK_SIZE_MB = config.chunkSizeMb && config.chunkSizeMb > 0 ? config.chunkSizeMb : DEFAULT_CHUNK_SIZE_MB;
    CURRENT_MAX_PARALLEL = config.parallelism && config.parallelism > 0 ? config.parallelism : DEFAULT_MAX_PARALLEL;

    // Guardrails to avoid runaway memory from extreme inputs
    if (CURRENT_CHUNK_SIZE_MB > HARD_MAX_CHUNK_MB) {
        console.log(`chunkSizeMb=${CURRENT_CHUNK_SIZE_MB}MB requested, clamping to ${HARD_MAX_CHUNK_MB}MB for safety`);
        CURRENT_CHUNK_SIZE_MB = HARD_MAX_CHUNK_MB;
    }
    if (CURRENT_MAX_PARALLEL > HARD_MAX_PARALLEL) {
        console.log(`parallelism=${CURRENT_MAX_PARALLEL} requested, clamping to ${HARD_MAX_PARALLEL} for safety`);
        CURRENT_MAX_PARALLEL = HARD_MAX_PARALLEL;
    }
    console.log(`Using tuning: chunkSize=${CURRENT_CHUNK_SIZE_MB}MB, parallel=${CURRENT_MAX_PARALLEL}`);
    const htmlPath = config.htmlPath;
    const outputPath = config.outputPath;
    
    // Get file size without loading into memory
    const htmlStats = fs.statSync(htmlPath);
    const htmlSizeMB = (htmlStats.size / 1024 / 1024).toFixed(2);
    
    console.log(`HTML size: ${htmlSizeMB}MB`);
    logMemory('after stat');
    
    // For smaller files, use single-pass generation
    if (parseFloat(htmlSizeMB) < 10) {
        console.log('Small file detected, using single-pass generation...');
        await generateSinglePdf(config);
        return;
    }
    
    console.log('Large file detected, using chunked STREAMING generation...');
    console.log('Processing HTML in chunks to minimize memory usage...');
    
    // Force garbage collection if available
    if (global.gc) {
        global.gc();
        console.log('Garbage collection triggered');
    }
    logMemory('before read html');
    
    // Read HTML content - we need it for splitting but will free it ASAP
    let htmlContent = fs.readFileSync(htmlPath, 'utf8');
    logMemory('after read html');
    console.log('Validating and splitting HTML at safe boundaries...');
    
    const startTime = Date.now();
    
    // Split HTML into chunks at SAFE boundaries only
    const chunks = splitHtmlSafely(htmlContent);
    console.log(`Split into ${chunks.length} chunks (validated)`);
    logMemory('after split');
    
    // *** MEMORY OPTIMIZATION: Free the original HTML from memory ***
    htmlContent = null;
    if (global.gc) {
        global.gc();
        console.log('Memory freed after splitting');
    }
    logMemory('after null html + gc');
    
    // Create temp directory for chunk PDFs
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pdf-chunks-'));
    const chunkPdfs = new Array(chunks.length);
    
    try {
        // *** MEMORY OPTIMIZATION: Write chunks to disk and free from memory incrementally ***
        console.log('Writing chunk HTML files and freeing memory...');
        const chunkFiles = [];
        for (let i = 0; i < chunks.length; i++) {
            const chunkHtmlPath = path.join(tempDir, `chunk-${i}.html`);
            const chunkPdfPath = path.join(tempDir, `chunk-${i}.pdf`);
            fs.writeFileSync(chunkHtmlPath, chunks[i].html);
            chunkFiles.push({ index: i, htmlPath: chunkHtmlPath, pdfPath: chunkPdfPath });
            // Free chunk from memory immediately after writing
            chunks[i] = null;
        }
        
        // Force garbage collection to reclaim chunk memory
        if (global.gc) {
            global.gc();
            console.log('Chunk memory freed');
        }
        logMemory('after writing chunk htmls');
        
        const workers = Math.max(1, Math.min(CURRENT_MAX_PARALLEL, chunkFiles.length));
        console.log(`Processing ${chunkFiles.length} chunks with ${workers} worker(s) (chunkSize=${CURRENT_CHUNK_SIZE_MB}MB)...`);
        const processStart = Date.now();
        let completed = 0;

        const queue = [...chunkFiles];

        const runWorker = async () => {
            // Reuse a single lightweight browser + single page (tab) per worker to minimize RAM churn.
            let browser = await launchBrowser();
            let page = null;
            try {
                page = await browser.newPage();
                await preparePageForChunking(page, config);
            } catch (e) {
                try { if (page) await page.close(); } catch (_) {}
                try { if (browser) await browser.close(); } catch (_) {}
                throw e;
            }
            while (queue.length > 0) {
                const chunk = queue.shift();
                if (!chunk) break;
                try {
                    await generateChunkPdf(chunk.htmlPath, chunk.pdfPath, config, 0, browser, page);
                    chunkPdfs[chunk.index] = chunk.pdfPath;
                    completed++;
                    // Delete HTML file immediately after processing to free memory
                    try { fs.unlinkSync(chunk.htmlPath); } catch (e) {}
                    // Progress update every 5 chunks or at completion
                    if (completed % 5 === 0 || completed === chunkFiles.length) {
                        const elapsed = (Date.now() - processStart) / 1000;
                        const rate = completed / Math.max(elapsed, 0.001);
                        const remaining = (chunkFiles.length - completed) / Math.max(rate, 0.001);
                        console.log(`  Progress: ${completed}/${chunkFiles.length} chunks (${(completed/chunkFiles.length*100).toFixed(0)}%) - ETA: ${remaining.toFixed(0)}s`);
                        if (global.gc) { global.gc(); }
                    }
                } catch (error) {
                    console.error(`  Chunk ${chunk.index + 1} failed: ${error.message}`);
                    try {
                        // If browser died, restart it
                        if (!browser.isConnected()) {
                            try { await browser.close(); } catch (e) {}
                            browser = await launchBrowser();
                            try { if (page) await page.close(); } catch (e) {}
                            page = await browser.newPage();
                            await preparePageForChunking(page, config);
                        }
                        await generateChunkPdf(chunk.htmlPath, chunk.pdfPath, config, 0, browser, page);
                        chunkPdfs[chunk.index] = chunk.pdfPath;
                        completed++;
                    } catch (retryError) {
                        console.error(`  Chunk ${chunk.index + 1} failed on retry: ${retryError.message}`);
                        throw retryError;
                    }
                }
            }
            try { if (page) await page.close(); } catch (e) {}
            try { await browser.close(); } catch (e) {}
        };

        const workerPromises = Array.from({ length: workers }, () => runWorker());
        await Promise.all(workerPromises);
        logMemory('after rendering chunks');

        const processTime = (Date.now() - processStart) / 1000;
        console.log(`All ${chunkFiles.length} chunks processed in ${processTime.toFixed(1)}s (${(chunkFiles.length/processTime).toFixed(1)} chunks/sec)`)
        
        // Merge all chunk PDFs
        console.log('Merging PDFs...');
        const validPdfs = chunkPdfs.filter(p => p && fs.existsSync(p));
        if (validPdfs.length !== chunks.length) {
            console.warn(`Warning: Only ${validPdfs.length}/${chunks.length} chunks succeeded`);
        }
        
        await mergePdfs(validPdfs, outputPath);
        logMemory('after merge');
        
        const totalTime = (Date.now() - startTime) / 1000;
        const stats = fs.statSync(outputPath);
        const sizeMB = (stats.size / 1024 / 1024).toFixed(2);
        
        console.log(`PDF generated successfully: ${sizeMB}MB in ${totalTime.toFixed(1)}s`);
        console.log(`Speed: ${(parseFloat(htmlSizeMB) / totalTime).toFixed(2)} MB/s input, ${(chunks.length / totalTime).toFixed(1)} chunks/s`);
        
    } finally {
        // Cleanup temp directory
        try {
            fs.rmSync(tempDir, { recursive: true, force: true });
        } catch (e) {
            console.warn('Cleanup warning:', e.message);
        }
        // Final garbage collection
        if (global.gc) {
            global.gc();
        }
        logMemory('final');
    }
}

// generateChunkPdfWithBrowser removed - no longer using browser pool

/**
 * Split HTML safely - handles both regular elements AND large single tables.
 * For large tables, splits by rows while preserving table structure.
 */
function splitHtmlSafely(html) {
    const chunkSizeBytes = CURRENT_CHUNK_SIZE_MB * 1024 * 1024;
    const chunks = [];
    
    // Extract head section
    const headMatch = html.match(/<head[^>]*>[\s\S]*?<\/head>/i);
    const headContent = headMatch ? headMatch[0] : '<head><meta charset="UTF-8"></head>';
    
    // Extract style tags from anywhere
    const styleMatches = html.match(/<style[^>]*>[\s\S]*?<\/style>/gi) || [];
    const allStyles = styleMatches.join('\n');
    
    // Extract body content
    const bodyMatch = html.match(/<body[^>]*>([\s\S]*)<\/body>/i);
    if (!bodyMatch) {
        return [{ html: html }];
    }
    
    const bodyContent = bodyMatch[1];
    
    // Check if this is a single large table
    const tableCount = (bodyContent.match(/<table[\s>]/gi) || []).length;
    const trCount = (bodyContent.match(/<tr[\s>]/gi) || []).length;
    
    if (tableCount === 1 && trCount > 100) {
        console.log(`Detected single large table with ${trCount} rows - splitting by rows`);
        return splitTableByRows(bodyContent, headContent, allStyles, chunkSizeBytes);
    }
    
    // Otherwise, use standard element-based splitting
    const topLevelElements = extractTopLevelElements(bodyContent);
    console.log(`Found ${topLevelElements.length} top-level elements`);
    
    let currentChunkElements = [];
    let currentSize = 0;
    
    for (const element of topLevelElements) {
        const elementSize = Buffer.byteLength(element, 'utf8');
        
        if (currentSize + elementSize > chunkSizeBytes && currentChunkElements.length > 0) {
            chunks.push(createChunkHtml(currentChunkElements.join('\n'), headContent, allStyles));
            currentChunkElements = [element];
            currentSize = elementSize;
        } else {
            currentChunkElements.push(element);
            currentSize += elementSize;
        }
    }
    
    if (currentChunkElements.length > 0) {
        chunks.push(createChunkHtml(currentChunkElements.join('\n'), headContent, allStyles));
    }
    
    // Validate chunks
    for (let i = 0; i < chunks.length; i++) {
        const validation = validateHtml(chunks[i].html);
        if (!validation.valid) {
            console.warn(`Chunk ${i + 1} validation warning: ${validation.issues.join(', ')}`);
        }
    }
    
    return chunks;
}

/**
 * Split a large single table by rows, preserving table structure.
 * Each chunk gets the table header and a subset of rows.
 */
function splitTableByRows(bodyContent, headContent, styles, chunkSizeBytes) {
    const chunks = [];
    
    // Extract table opening tag with attributes
    const tableOpenMatch = bodyContent.match(/<table[^>]*>/i);
    const tableOpen = tableOpenMatch ? tableOpenMatch[0] : '<table>';
    
    // Extract colgroup/col if present
    const colgroupMatch = bodyContent.match(/<colgroup[^>]*>[\s\S]*?<\/colgroup>/i) ||
                          bodyContent.match(/(<col[^>]*>)+/gi);
    const colgroup = colgroupMatch ? (Array.isArray(colgroupMatch) ? colgroupMatch.join('') : colgroupMatch[0]) : '';
    
    // Extract thead if present (for header row)
    const theadMatch = bodyContent.match(/<thead[^>]*>[\s\S]*?<\/thead>/i);
    const thead = theadMatch ? theadMatch[0] : '';
    
    // Extract first row as header if no thead
    let headerRow = '';
    if (!thead) {
        const firstRowMatch = bodyContent.match(/<tr[^>]*>[\s\S]*?<\/tr>/i);
        if (firstRowMatch) {
            headerRow = firstRowMatch[0];
        }
    }
    
    // Iterate rows sequentially instead of materializing all rows in memory.
    const rowRegex = /<tr[^>]*>[\s\S]*?<\/tr>/gi;
    let match;
    let isFirst = !thead; // Skip first row if using it as header
    let dataRowCount = 0;
    
    // Calculate overhead size (table structure we add to each chunk)
    const tableStructure = tableOpen + colgroup + (thead || headerRow);
    const overheadSize = Buffer.byteLength(tableStructure + '</tbody></table>', 'utf8');
    const availableSize = chunkSizeBytes - overheadSize - 1000; // 1KB buffer
    
    // Group rows into chunks
    let currentRows = [];
    let currentSize = 0;

    while ((match = rowRegex.exec(bodyContent)) !== null) {
        if (isFirst) {
            isFirst = false;
            continue;
        }

        const row = match[0];
        dataRowCount++;
        const rowSize = Buffer.byteLength(row, 'utf8');

        if (currentSize + rowSize > availableSize && currentRows.length > 0) {
            const chunkBody = tableOpen + colgroup + (thead || headerRow) +
                             '<tbody>' + currentRows.join('\n') + '</tbody></table>';
            chunks.push(createChunkHtml(chunkBody, headContent, styles));

            currentRows = [row];
            currentSize = rowSize;
        } else {
            currentRows.push(row);
            currentSize += rowSize;
        }
    }
    
    // Last chunk
    if (currentRows.length > 0) {
        const chunkBody = tableOpen + colgroup + (thead || headerRow) + 
                         '<tbody>' + currentRows.join('\n') + '</tbody></table>';
        chunks.push(createChunkHtml(chunkBody, headContent, styles));
    }
    
    console.log(`Extracted ${dataRowCount} data rows from table`);
    console.log(`Split table into ${chunks.length} chunks`);
    
    // Validate
    for (let i = 0; i < chunks.length; i++) {
        const validation = validateHtml(chunks[i].html);
        if (!validation.valid) {
            console.warn(`Chunk ${i + 1} validation: ${validation.issues.join(', ')}`);
        } else {
            console.log(`Chunk ${i + 1}: valid ✓`);
        }
    }
    
    return chunks;
}

/**
 * Extract complete top-level elements from body content.
 * Properly tracks nesting to never split mid-element.
 */
function extractTopLevelElements(bodyContent) {
    // IMPORTANT: Avoid building strings char-by-char (O(n^2) behavior).
    // Track indices and slice from the original bodyContent instead.
    const elements = [];
    let depth = 0;
    let inTag = false;
    let tagName = '';
    let isClosingTag = false;
    let isSelfClosing = false;
    let elementStart = 0;
    let sawNonWhitespaceAtDepth0 = false;
    
    // Self-closing tags that don't need matching close tags
    const voidElements = new Set([
        'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
        'link', 'meta', 'param', 'source', 'track', 'wbr'
    ]);
    
    const content = bodyContent;
    const len = content.length;

    // Start from first non-whitespace to avoid empty elements.
    while (elementStart < len && /\s/.test(content[elementStart])) elementStart++;

    for (let i = elementStart; i < len; i++) {
        const char = content[i];

        if (depth === 0 && !inTag && !sawNonWhitespaceAtDepth0 && !/\s/.test(char)) {
            sawNonWhitespaceAtDepth0 = true;
        }

        if (char === '<') {
            inTag = true;
            tagName = '';
            isClosingTag = content[i + 1] === '/';
            isSelfClosing = false;
            continue;
        }

        if (!inTag) continue;

        if (char === '>') {
            inTag = false;
            isSelfClosing = content[i - 1] === '/' || voidElements.has(tagName.toLowerCase());

            if (isClosingTag) {
                depth = Math.max(0, depth - 1);
            } else if (!isSelfClosing && tagName && !tagName.startsWith('!')) {
                depth += 1;
            }

            if (depth === 0) {
                const raw = content.slice(elementStart, i + 1);
                if (sawNonWhitespaceAtDepth0) {
                    elements.push(raw.trim());
                }
                sawNonWhitespaceAtDepth0 = false;

                elementStart = i + 1;
                while (elementStart < len && /\s/.test(content[elementStart])) elementStart++;
                i = elementStart - 1;
            }

            continue;
        }

        if (char === ' ' || char === '\n' || char === '\t' || char === '/') {
            continue;
        }

        if (!isClosingTag || char !== '/') {
            if (tagName.length < 20) {
                tagName += char;
            }
        }
    }

    // Remaining tail content (e.g., text nodes not wrapped in an element)
    if (elementStart < len) {
        const tail = content.slice(elementStart);
        if (tail.trim()) {
            elements.push(tail.trim());
        }
    }

    return elements;
}

/**
 * Create a complete HTML document from body content
 */
function createChunkHtml(bodyContent, headContent, styles) {
    // If styles are already in head, don't duplicate
    const hasStylesInHead = headContent.includes('<style');
    const styleBlock = hasStylesInHead ? '' : styles;
    
    return {
        html: `<!DOCTYPE html>
<html>
${headContent}
${styleBlock}
<body style="margin: 0; padding: 0;">
${bodyContent}
</body>
</html>`
    };
}

/**
 * Validate HTML for common issues
 */
function validateHtml(html) {
    const issues = [];
    
    // Check for unclosed tables
    const tableOpens = (html.match(/<table[\s>]/gi) || []).length;
    const tableCloses = (html.match(/<\/table>/gi) || []).length;
    if (tableOpens !== tableCloses) {
        issues.push(`Unclosed tables: ${tableOpens} opens, ${tableCloses} closes`);
    }
    
    // Check for unclosed divs
    const divOpens = (html.match(/<div[\s>]/gi) || []).length;
    const divCloses = (html.match(/<\/div>/gi) || []).length;
    if (divOpens !== divCloses) {
        issues.push(`Unclosed divs: ${divOpens} opens, ${divCloses} closes`);
    }
    
    // Check for unclosed tr
    const trOpens = (html.match(/<tr[\s>]/gi) || []).length;
    const trCloses = (html.match(/<\/tr>/gi) || []).length;
    if (trOpens !== trCloses) {
        issues.push(`Unclosed tr: ${trOpens} opens, ${trCloses} closes`);
    }
    
    // Check for unclosed td
    const tdOpens = (html.match(/<td[\s>]/gi) || []).length;
    const tdCloses = (html.match(/<\/td>/gi) || []).length;
    if (tdOpens !== tdCloses) {
        issues.push(`Unclosed td: ${tdOpens} opens, ${tdCloses} closes`);
    }
    
    return {
        valid: issues.length === 0,
        issues: issues
    };
}

async function generateChunkPdf(htmlPath, outputPath, config, retryCount = 0, sharedBrowser = null, sharedPage = null) {
    // MEMORY FIX: Use minimal launch options, single process
    const launchOptions = {
        headless: 'new',
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
            '--disable-dev-shm-usage',
            '--disable-gpu',
            '--disable-software-rasterizer',
            '--disable-vulkan',
            '--disable-extensions',
            '--disable-background-networking',
            '--no-first-run',
            '--disable-default-apps',
            '--disable-web-security',
            '--js-flags=--max-old-space-size=256', // MEMORY FIX: Reduced from 1024MB
            '--no-zygote',
            '--single-process', // MEMORY FIX: Critical for memory reduction
            '--disable-accelerated-2d-canvas',
            '--use-gl=swiftshader',
        ],
        timeout: 90000, // Reduced timeout
        protocolTimeout: 180000
    };
    
    // Use system Chromium in Docker
    if (CHROME_PATH) {
        launchOptions.executablePath = CHROME_PATH;
    }
    
    let browser = sharedBrowser;
    let page = sharedPage;
    const ownsBrowser = !browser;
    try {
        if (!browser) {
            browser = await puppeteer.launch(launchOptions);
        }
        // If no shared page is provided (e.g., single-pass fallback), create and configure one.
        const ownsPage = !page;
        if (!page) {
            page = await browser.newPage();
            await preparePageForChunking(page, config);
        }
        
        // Navigate from a blank state to limit history/memory
        try { await page.goto('about:blank'); } catch (e) {}
        await page.goto(`file://${path.resolve(htmlPath)}`, {
            waitUntil: 'domcontentloaded',
            timeout: 60000
        });

        // Optional: inject CSS provided via config without rewriting HTML.
        if (config.cssContent && String(config.cssContent).trim().length > 0) {
            try {
                await page.addStyleTag({ content: String(config.cssContent) });
            } catch (e) {
                console.warn(`Failed to inject cssContent for chunk: ${e && e.message ? e.message : e}`);
            }
        }

        // Minimal wait
        await new Promise(resolve => setTimeout(resolve, 200));

        const scale = await computeFitScale(page, config);

        await page.pdf({
            path: outputPath,
            format: config.format || 'A4',
            landscape: config.landscape || false,
            scale: scale,
            printBackground: config.printBackground !== false,
            preferCSSPageSize: config.preferCSSPageSize || false,
            margin: config.margin || { top: '20px', right: '20px', bottom: '20px', left: '20px' },
            timeout: 180000
        });
        
        return true;
    } catch (error) {
        if (retryCount < MAX_RETRIES) {
            console.warn(`Chunk failed, retry ${retryCount + 1}/${MAX_RETRIES}: ${error.message}`);
            // Wait before retry
            await new Promise(resolve => setTimeout(resolve, 1000 * (retryCount + 1)));
            // If we got a page crash or navigation error, discard the page and recreate on retry.
            try { if (page && !sharedPage) await page.close(); } catch (_) {}
            return generateChunkPdf(htmlPath, outputPath, config, retryCount + 1, browser, sharedPage);
        }
        
        throw error;
    } finally {
        // Cleanup only if we own the page/browser. When a shared page is used, the worker closes it.
        if (page && !sharedPage) {
            try {
                await page.removeAllListeners();
                await page.close();
            } catch (e) {}
        }
        if (ownsBrowser && browser) {
            try { await browser.close(); } catch (e) {}
        }
        // Force garbage collection
        if (global.gc) {
            global.gc();
        }
    }
}

async function generateSinglePdf(config) {
    const launchOptions = {
        headless: 'new',
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox', 
            '--disable-dev-shm-usage',
            '--disable-gpu',
            '--disable-software-rasterizer',
            '--disable-vulkan',
            '--disable-extensions',
            '--no-first-run',
            '--disable-default-apps',
            '--disable-features=site-per-process,IsolateOrigins',
            '--disable-site-isolation-trials',
            '--no-zygote',
            '--disable-accelerated-2d-canvas',
            '--use-gl=swiftshader',
            '--js-flags=--max-old-space-size=4096',
        ],
        timeout: 600000,
        protocolTimeout: 600000
    };
    
    // Use system Chromium in Docker
    if (CHROME_PATH) {
        launchOptions.executablePath = CHROME_PATH;
    }
    
    const browser = await puppeteer.launch(launchOptions);

    try {
        const page = await browser.newPage();
        page.setDefaultTimeout(600000);
        
        const startLoad = Date.now();
        await page.goto(`file://${path.resolve(config.htmlPath)}`, {
            waitUntil: 'domcontentloaded',
            timeout: 600000
        });
        console.log(`DOM loaded in ${Date.now() - startLoad}ms`);

        // Optional: inject CSS provided via config without rewriting HTML.
        if (config.cssContent && String(config.cssContent).trim().length > 0) {
            try {
                await page.addStyleTag({ content: String(config.cssContent) });
            } catch (e) {
                console.warn(`Failed to inject cssContent: ${e && e.message ? e.message : e}`);
            }
        }

        const scale = await computeFitScale(page, config);

        const startPdf = Date.now();
        await page.pdf({
            path: config.outputPath,
            format: config.format || 'A4',
            landscape: config.landscape || false,
            scale: scale,
            printBackground: config.printBackground !== false,
            preferCSSPageSize: config.preferCSSPageSize || false,
            margin: config.margin || { top: '20px', right: '20px', bottom: '20px', left: '20px' },
            timeout: 600000
        });
        
        const stats = fs.statSync(config.outputPath);
        console.log(`PDF generated: ${(stats.size / 1024 / 1024).toFixed(2)}MB in ${((Date.now() - startPdf) / 1000).toFixed(1)}s`);
    } finally {
        await browser.close();
    }
}

async function mergePdfs(pdfPaths, outputPath) {
    if (pdfPaths.length === 0) {
        throw new Error('No PDFs to merge');
    }
    
    if (pdfPaths.length === 1) {
        fs.copyFileSync(pdfPaths[0], outputPath);
        return;
    }
    
    // Try using pdfunite (from poppler-utils) or pdftk
    try {
        // Check if pdfunite is available (common on macOS with Homebrew)
        execSync('which pdfunite', { stdio: 'ignore' });
        const cmd = `pdfunite ${pdfPaths.map(p => `"${p}"`).join(' ')} "${outputPath}"`;
        execSync(cmd, { stdio: 'inherit' });
        console.log(`Merged ${pdfPaths.length} PDFs using pdfunite`);
        return;
    } catch (e) {
        // pdfunite not available
    }
    
    // Try pdftk
    try {
        execSync('which pdftk', { stdio: 'ignore' });
        const cmd = `pdftk ${pdfPaths.map(p => `"${p}"`).join(' ')} cat output "${outputPath}"`;
        execSync(cmd, { stdio: 'inherit' });
        console.log(`Merged ${pdfPaths.length} PDFs using pdftk`);
        return;
    } catch (e) {
        // pdftk not available
    }
    
    // Try qpdf
    try {
        execSync('which qpdf', { stdio: 'ignore' });
        const cmd = `qpdf --empty --pages ${pdfPaths.map(p => `"${p}"`).join(' ')} -- "${outputPath}"`;
        execSync(cmd, { stdio: 'inherit' });
        console.log(`Merged ${pdfPaths.length} PDFs using qpdf`);
        return;
    } catch (e) {
        // qpdf not available
    }
    
    // Fallback: use pdf-lib (Node.js)
    console.log('No system PDF merger found, using pdf-lib...');
    await mergePdfsWithPdfLib(pdfPaths, outputPath);
}

async function mergePdfsWithPdfLib(pdfPaths, outputPath) {
    // Dynamic import for pdf-lib
    let PDFDocument;
    try {
        const pdfLib = require('pdf-lib');
        PDFDocument = pdfLib.PDFDocument;
    } catch (e) {
        console.error('pdf-lib not installed. Install with: npm install pdf-lib');
        console.error('Or install a system PDF merger: brew install poppler (for pdfunite)');
        throw new Error('No PDF merger available');
    }
    
    // *** MEMORY OPTIMIZATION: Merge in batches to avoid loading all PDFs at once ***
    const BATCH_SIZE = 10; // Merge 10 PDFs at a time
    
    if (pdfPaths.length <= BATCH_SIZE) {
        // Small enough to merge directly
        const mergedPdf = await PDFDocument.create();
        
        for (let i = 0; i < pdfPaths.length; i++) {
            const pdfBytes = fs.readFileSync(pdfPaths[i]);
            const pdf = await PDFDocument.load(pdfBytes);
            const pages = await mergedPdf.copyPages(pdf, pdf.getPageIndices());
            pages.forEach(page => mergedPdf.addPage(page));
            
            // Progress for large merges
            if (i > 0 && i % 10 === 0) {
                console.log(`  Merged ${i}/${pdfPaths.length} PDFs...`);
            }
        }
        
        const mergedBytes = await mergedPdf.save();
        fs.writeFileSync(outputPath, mergedBytes);
        console.log(`Merged ${pdfPaths.length} PDFs using pdf-lib`);
        return;
    }
    
    // Large number of PDFs - use batch merging
    console.log(`Batch merging ${pdfPaths.length} PDFs in groups of ${BATCH_SIZE}...`);
    const tempDir = path.dirname(pdfPaths[0]);
    let intermediatePdfs = [...pdfPaths];
    let round = 0;
    
    while (intermediatePdfs.length > 1) {
        round++;
        const nextRoundPdfs = [];
        
        for (let i = 0; i < intermediatePdfs.length; i += BATCH_SIZE) {
            const batch = intermediatePdfs.slice(i, i + BATCH_SIZE);
            
            if (batch.length === 1) {
                nextRoundPdfs.push(batch[0]);
                continue;
            }
            
            const mergedPdf = await PDFDocument.create();
            
            for (const pdfPath of batch) {
                const pdfBytes = fs.readFileSync(pdfPath);
                const pdf = await PDFDocument.load(pdfBytes);
                const pages = await mergedPdf.copyPages(pdf, pdf.getPageIndices());
                pages.forEach(page => mergedPdf.addPage(page));
            }
            
            // If this is the final merge, write to output
            if (intermediatePdfs.length <= BATCH_SIZE) {
                const mergedBytes = await mergedPdf.save();
                fs.writeFileSync(outputPath, mergedBytes);
                console.log(`Merged ${pdfPaths.length} PDFs using pdf-lib (${round} rounds)`);
                return;
            }
            
            // Write intermediate result
            const intermediatePath = path.join(tempDir, `merged-round${round}-${i}.pdf`);
            const mergedBytes = await mergedPdf.save();
            fs.writeFileSync(intermediatePath, mergedBytes);
            nextRoundPdfs.push(intermediatePath);
            
            console.log(`  Round ${round}: merged batch ${Math.floor(i/BATCH_SIZE)+1}/${Math.ceil(intermediatePdfs.length/BATCH_SIZE)}`);
        }
        
        // Clean up previous intermediate files (except original chunks)
        if (round > 1) {
            for (const oldPdf of intermediatePdfs) {
                if (oldPdf.includes('merged-round') && !nextRoundPdfs.includes(oldPdf)) {
                    try { fs.unlinkSync(oldPdf); } catch(e) {}
                }
            }
        }
        
        intermediatePdfs = nextRoundPdfs;
        
        // Trigger GC between rounds
        if (global.gc) global.gc();
    }
    
    // Copy final result
    if (intermediatePdfs.length === 1) {
        fs.copyFileSync(intermediatePdfs[0], outputPath);
        console.log(`Merged ${pdfPaths.length} PDFs using pdf-lib (${round} rounds)`);
    }
}

generatePdf().catch(err => {
    console.error('Error:', err.message);
    process.exit(1);
});
