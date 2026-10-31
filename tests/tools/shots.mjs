// Capturas de las pantallas de la beta (para copiar su aspecto en la app nativa). Uso: node tools/shots.mjs <carpeta>
import { createRequire } from 'node:module';
const { chromium } = createRequire(import.meta.url)('playwright');
import { readFileSync } from 'node:fs';
import { startServer } from './server.mjs';
const out = process.argv[2] || '/tmp/shots';
const bk = JSON.parse(readFileSync(new URL('../fixtures/backup/zancada-copia.json', import.meta.url)));
const { srv, url } = await startServer(new URL('../..', import.meta.url).pathname);
const browser = await chromium.launch();
const ctx = await browser.newContext({ locale: 'es-ES', viewport: { width: 400, height: 860 }, deviceScaleFactor: 1.5, serviceWorkers: 'block' });
await ctx.route('**/*', (r) => (r.request().url().startsWith(url) ? r.continue() : r.abort()));
const page = await ctx.newPage();
await page.addInitScript(() => { let cb = null; window.__geo = (f) => cb && cb(f); Object.defineProperty(navigator, 'geolocation', { value: { watchPosition: (ok) => { cb = ok; return 1; }, clearWatch: () => {}, getCurrentPosition: () => {} } }); });
await page.addInitScript((st) => { if (!sessionStorage.getItem('s')) { sessionStorage.setItem('s', 1); for (const [k, v] of Object.entries(st)) if (k.startsWith('zancada.')) localStorage.setItem(k, JSON.stringify(v)); } }, bk);
await page.goto(url); await page.waitForFunction(() => window.__z); await page.waitForTimeout(800);
await page.evaluate(() => document.querySelectorAll('.modal.on').forEach((m) => m.classList.remove('on')));
for (const s of ['home', 'history', 'profile', 'prep', 'plan', 'routes']) { await page.evaluate((s) => window.__z.go(s), s); await page.waitForTimeout(500); await page.screenshot({ path: `${out}/${s}.png` }); }
const id = bk['zancada.runs.v1'][4].id;
await page.evaluate((id) => window.__z.openSummary(id), id); await page.waitForTimeout(600); await page.screenshot({ path: `${out}/summary.png`, fullPage: true });
await page.evaluate(() => window.__z.go('home')); await page.evaluate(() => window.__z.go('prep')); await page.waitForTimeout(300);
const fix = (i) => page.evaluate((i) => window.__geo({ coords: { latitude: 40.41 + i * 0.00004, longitude: -3.68, accuracy: 6, altitude: 650 }, timestamp: Date.now() }), i);
await fix(0); await page.waitForTimeout(300); await page.screenshot({ path: `${out}/prep-ok.png` });
await page.click('#startBtn'); await page.waitForTimeout(1200); await page.screenshot({ path: `${out}/countdown.png` });
for (let i = 1; i < 12; i++) { await page.waitForTimeout(500); await fix(i); }
await page.screenshot({ path: `${out}/run.png` });
await browser.close(); srv.close();
