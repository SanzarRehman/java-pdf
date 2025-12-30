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
const POOL_SIZE = Math.max(1, Number(process.env.RENDERER_POOL_SIZE || 2));

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

const pool = new AsyncPool(POOL_SIZE);
const browsers = [];
let browserIndex = 0;

async function getBrowser() {
  if (browsers.length < POOL_SIZE) {
    const browser = await puppeteer.launch(buildLaunchOptions());
    browsers.push(browser);
    return browser;
  }

  const browser = browsers[browserIndex % browsers.length];
  browserIndex++;
  return browser;
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

  const browser = await getBrowser();
  const page = await browser.newPage();

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

    await page.pdf({
      path: config.outputPath,
      format: config.format || 'A4',
      landscape: !!config.landscape,
      printBackground: config.printBackground !== false,
      preferCSSPageSize: !!config.preferCSSPageSize,
      margin: config.margin || { top: '20px', right: '20px', bottom: '20px', left: '20px' },
      timeout: 600000,
    });

    const stats = fs.statSync(config.outputPath);
    return { bytes: stats.size };
  } finally {
    try {
      await page.close();
    } catch (_) {
      // ignore
    }
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
