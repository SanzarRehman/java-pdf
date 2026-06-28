#!/usr/bin/env node

/**
 * Persistent Chromium renderer server (single-container best practice).
 *
 * Goal:
 * - Keep a small pool of Chromium instances warm.
 * - Accept render jobs over localhost HTTP.
 * - Avoid spawning a new Node/Chromium per request.
 *
 * Note: For "chunked" renders we currently invoke the existing chunked CLI script.
 * This keeps behavior consistent for huge documents. (Can be upgraded later to fully pooled chunking.)
 */

'use strict';

const http = require('http');
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

let puppeteer;
try {
  puppeteer = require('puppeteer-core');
} catch (e) {
  puppeteer = require('puppeteer');
}

const PORT = Number(process.env.RENDERER_SERVER_PORT || 3001);
const HOST = process.env.RENDERER_SERVER_HOST || '127.0.0.1';

// ONE shared browser + a set of REUSED pages (navigated, not recreated). CONCURRENCY =
// how many renders run at once = number of reusable pages kept alive. Each parallel
// render costs ~1GB RAM, so size to the RAM/throughput trade-off you want. Capped at 8
// (Chromium parallelism has diminishing returns; Gotenberg caps at 6). Default 2.
const CONCURRENCY = Math.min(8, Math.max(1,
  Number(process.env.RENDERER_CONCURRENCY || process.env.RENDERER_POOL_SIZE || 2)));
// Recycle a reused page after this many renders to fight leak creep.
const PAGE_RECYCLE_AFTER = Math.max(1, Number(process.env.RENDERER_PAGE_RECYCLE_AFTER || 200));
// Recycle the whole browser after this many total renders (defends against deeper leaks).
const BROWSER_RECYCLE_AFTER = Math.max(1, Number(process.env.RENDERER_BROWSER_RECYCLE_AFTER || 1000));
// Back-compat alias used in log output.
const POOL_SIZE = CONCURRENCY;

// Post-render PDF compression via qpdf: 'auto'/'on' = compress if qpdf present,
// 'off' = skip. Safe no-op when qpdf is missing (local dev).
const COMPRESS = (process.env.RENDERER_COMPRESS || 'auto').toLowerCase();

// Paper width in inches for supported page formats, portrait + landscape.
// Mirrors puppeteer-pdf.js so the pooled server produces identical output.
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
 * over-wide elements (e.g. wide tables) fit within the printable page width. The layout
 * is measured at the (wide) viewport where columns have room, then the whole page -- font
 * included -- is scaled down to the paper, so cells like "MCH, ZFA" stay on one line
 * instead of wrapping. Returns a scale in Chromium's allowed range [0.1, 2.0]; shrink-only.
 *
 * Ported from puppeteer-pdf.js so the pooled renderer-server matches the CLI renderer.
 */
async function computeFitScale(page, config) {
  // Explicit override always wins.
  if (typeof config.scale === 'number' && config.scale > 0) {
    return Math.min(2, Math.max(0.1, config.scale));
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

const CHROME_PATH =
  process.env.PUPPETEER_EXECUTABLE_PATH ||
  process.env.CHROME_PATH ||
  process.env.CHROME_BIN ||
  (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : null) ||
  (fs.existsSync('/usr/bin/chromium-browser') ? '/usr/bin/chromium-browser' : null) ||
  (fs.existsSync('/usr/bin/google-chrome') ? '/usr/bin/google-chrome' : null);

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

function readJsonBody(req, limitBytes = 2 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limitBytes) {
        reject(new Error('Request body too large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      try {
        const raw = Buffer.concat(chunks).toString('utf8');
        resolve(raw ? JSON.parse(raw) : {});
      } catch (e) {
        reject(e);
      }
    });
    req.on('error', reject);
  });
}

function buildLaunchOptions() {
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
      '--no-zygote',
      '--disable-features=site-per-process,IsolateOrigins',
      '--disable-site-isolation-trials',
      '--disable-background-timer-throttling',
      '--disable-backgrounding-occluded-windows',
      '--disable-renderer-backgrounding',
    ],
    executablePath: CHROME_PATH || undefined,
    timeout: 180000,
    protocolTimeout: 300000,
  };
}

async function preparePage(page, config) {
  page.setDefaultTimeout(600000);
  page.setDefaultNavigationTimeout(600000);

  const deviceScaleFactor = config && config.highQuality ? 2 : 1;
  await page.setViewport({ width: 1200, height: 1600, deviceScaleFactor });

  await page.setRequestInterception(true);
  page.removeAllListeners('request');
  page.on('request', (request) => {
    const url = request.url();
    if (url.startsWith('file://') || url.startsWith('data:')) {
      request.continue();
      return;
    }

    // Block external fetches by default.
    // This prevents unexpected network hangs/SSRF-like behavior.
    request.abort();
  });
}

class AsyncPool {
  constructor(concurrency) {
    this.concurrency = concurrency;
    this.active = 0;
    this.queue = [];
  }

  run(task) {
    return new Promise((resolve, reject) => {
      const start = async () => {
        this.active++;
        try {
          const result = await task();
          resolve(result);
        } catch (e) {
          reject(e);
        } finally {
          this.active--;
          this._drain();
        }
      };

      if (this.active < this.concurrency) {
        start();
      } else {
        this.queue.push(start);
      }
    });
  }

  _drain() {
    while (this.active < this.concurrency && this.queue.length > 0) {
      const next = this.queue.shift();
      next();
    }
  }
}

const pool = new AsyncPool(CONCURRENCY);

// ---- Single shared browser (item 1: reuse one browser instance) ----
let sharedBrowser = null;
let browserLaunching = null;
let totalRenders = 0;          // lifetime renders, drives browser recycling

async function getSharedBrowser() {
  if (sharedBrowser) return sharedBrowser;
  if (browserLaunching) return browserLaunching;     // coalesce concurrent launches
  browserLaunching = (async () => {
    const b = await puppeteer.launch(buildLaunchOptions());
    b.on('disconnected', () => { if (sharedBrowser === b) { sharedBrowser = null; freePages.length = 0; } });
    sharedBrowser = b;
    browserLaunching = null;
    return b;
  })();
  return browserLaunching;
}

// ---- Reusable page pool (item 1: reuse pages across jobs instead of new/close) ----
// Pages are navigated to new content per job and returned to the free list, NOT closed.
// Each page tracks a use-count and is recycled after PAGE_RECYCLE_AFTER renders.
const freePages = [];          // [{ page, uses }]

async function acquirePage() {
  // Periodic whole-browser recycle to defend against leak creep.
  if (totalRenders > 0 && totalRenders % BROWSER_RECYCLE_AFTER === 0 && sharedBrowser) {
    const old = sharedBrowser;
    sharedBrowser = null;
    freePages.length = 0;
    try { await old.close(); } catch (_) {}
  }

  const browser = await getSharedBrowser();
  let slot = freePages.pop();
  if (slot && slot.uses >= PAGE_RECYCLE_AFTER) {
    try { await slot.page.close(); } catch (_) {}
    slot = null;
  }
  if (!slot) {
    slot = { page: await browser.newPage(), uses: 0 };
  }
  return slot;
}

async function releasePage(slot) {
  if (!slot || !slot.page) return;
  slot.uses++;
  try {
    // Drop the heavy DOM/JS of the finished doc so the idle page holds minimal RAM.
    await slot.page.goto('about:blank', { waitUntil: 'domcontentloaded', timeout: 5000 });
    freePages.push(slot);
  } catch (_) {
    try { await slot.page.close(); } catch (__) {}
  }
}

// Post-compress a generated PDF with qpdf (recompress flate + object streams).
// Safe no-op when qpdf is missing (spawn 'error') or RENDERER_COMPRESS=off, so it
// never breaks local dev. qpdf exit 0 = clean, 3 = warnings-but-output-produced.
function compressPdf(file) {
  return new Promise((resolve) => {
    if (COMPRESS === 'off') return resolve(false);
    const tmp = `${file}.qpdf.tmp`;
    const args = ['--object-streams=generate', '--compress-streams=y', '--recompress-flate', '--', file, tmp];
    let child;
    try {
      child = spawn('qpdf', args, { stdio: 'ignore' });
    } catch (_) {
      return resolve(false);
    }
    child.on('error', () => resolve(false)); // qpdf not installed
    child.on('close', (code) => {
      if ((code === 0 || code === 3) && fs.existsSync(tmp)) {
        try { fs.renameSync(tmp, file); return resolve(true); } catch (_) {}
      }
      try { if (fs.existsSync(tmp)) fs.unlinkSync(tmp); } catch (_) {}
      resolve(false);
    });
  });
}

async function renderSingle(configPath) {
  if (!configPath || typeof configPath !== 'string') {
    throw new Error('configPath is required');
  }
  if (!fs.existsSync(configPath)) {
    throw new Error(`Config file not found: ${configPath}`);
  }

  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  if (!config.htmlPath || !config.outputPath) {
    throw new Error('Config must include htmlPath and outputPath');
  }

  const slot = await acquirePage();
  const page = slot.page;

  try {
    await preparePage(page, config);

    const htmlUrl = `file://${path.resolve(config.htmlPath)}`;
    await page.goto(htmlUrl, { waitUntil: 'domcontentloaded', timeout: 600000 });

    // Optional: inject CSS provided via config without rewriting HTML.
    if (config.cssContent && String(config.cssContent).trim().length > 0) {
      try {
        await page.addStyleTag({ content: String(config.cssContent) });
      } catch (e) {
        // keep going; CSS injection is best-effort
      }
    }

    // Bounded font wait
    try {
      await Promise.race([
        page.evaluate(() => (document.fonts ? document.fonts.ready : Promise.resolve())),
        new Promise((resolve) => setTimeout(resolve, 2000)),
      ]);
    } catch (_) {
      // ignore
    }

    // wkhtmltopdf-style smart shrink: scale wide content down to fit the page width so
    // narrow columns don't wrap. No-op (scale=1) when content already fits.
    const fitScale = await computeFitScale(page, config);

    await page.pdf({
      path: config.outputPath,
      format: config.format || 'A4',
      landscape: !!config.landscape,
      printBackground: config.printBackground !== false,
      preferCSSPageSize: !!config.preferCSSPageSize,
      // Tagged (accessible) PDF defaults to true in puppeteer and embeds a structure
      // tree — for huge tables that bloats output massively. Off by default (opt-in
      // via config.tagged=true), matching Gotenberg's --disable-pdf-tagging.
      tagged: config.tagged === true,
      scale: fitScale,
      margin: config.margin || { top: '20px', right: '20px', bottom: '20px', left: '20px' },
      timeout: 600000,
    });

    const rawBytes = fs.statSync(config.outputPath).size;
    // Optional post-compress pass (qpdf): recompress/object-stream the PDF.
    // No-op if qpdf is absent (e.g. local dev) or RENDERER_COMPRESS=off.
    await compressPdf(config.outputPath);
    const stats = fs.statSync(config.outputPath);
    return { bytes: stats.size, rawBytes };
  } finally {
    // Item 1: reuse the page across jobs (return to pool) instead of closing it.
    totalRenders++;
    await releasePage(slot);
  }
}

function runChunkedCli(configPath) {
  return new Promise((resolve, reject) => {
    const script = '/app/scripts/puppeteer-pdf-chunked.js';
    const child = spawn('node', [script, configPath], {
      stdio: ['ignore', 'pipe', 'pipe'],
      env: process.env,
    });

    let output = '';
    child.stdout.on('data', (d) => {
      output += d.toString('utf8');
      // keep memory lines visible in Java logs too
      if (d.toString('utf8').includes('[mem]')) {
        process.stdout.write(d);
      }
    });
    child.stderr.on('data', (d) => {
      output += d.toString('utf8');
    });

    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) {
        resolve({ ok: true, output });
      } else {
        reject(new Error(`chunked renderer failed (exit=${code}): ${output}`));
      }
    });
  });
}

async function shutdown() {
  for (const b of browsers) {
    try {
      await b.close();
    } catch (_) {
      // ignore
    }
  }
  process.exit(0);
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    json(res, 200, {
      ok: true,
      host: HOST,
      port: PORT,
      poolSize: POOL_SIZE,
      chrome: CHROME_PATH || 'bundled',
      node: process.version,
      cpus: os.cpus().length,
    });
    return;
  }

  if (req.method === 'POST' && req.url === '/render') {
    try {
      const body = await readJsonBody(req);
      const configPath = body.configPath;
      const scriptName = (body.scriptName || '').toString();

      const start = Date.now();

      // Decide mode based on scriptName
      const result = await pool.run(async () => {
        if (scriptName.includes('chunked')) {
          return runChunkedCli(configPath);
        }
        return renderSingle(configPath);
      });

      json(res, 200, { ok: true, durationMs: Date.now() - start, result });
    } catch (e) {
      json(res, 500, { ok: false, error: e && e.message ? e.message : String(e) });
    }
    return;
  }

  json(res, 404, { ok: false, error: 'not found' });
});

server.listen(PORT, HOST, () => {
  console.log(
    `renderer-server listening on http://${HOST}:${PORT} poolSize=${POOL_SIZE} chrome=${CHROME_PATH || 'bundled'}`
  );
});
