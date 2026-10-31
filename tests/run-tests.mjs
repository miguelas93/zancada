// Batería de pruebas de Zancada (fase 0 del plan nativo).
//   node tests/run-tests.mjs            → compara con tests/golden y falla si algo cambia
//   node tests/run-tests.mjs --update   → reescribe los resultados de referencia (solo si el cambio es intencionado)
// Qué comprueba:
//   1. Carreras simuladas (tests/fixtures/tracks): se "corren" en la app de verdad con un GPS falso y un reloj falso,
//      y km, tiempo, parciales, desnivel, mejores marcas y la pantalla final deben coincidir con la referencia.
//   2. Todas las pantallas se abren con esos datos sin errores de JavaScript, y Rutas no sale vacía.
// Estos mismos ficheros servirán para comprobar que la app nativa en Kotlin calcula exactamente lo mismo.
import { createRequire } from 'node:module';
const { chromium } = createRequire(import.meta.url)('playwright');
import { readFileSync, writeFileSync, readdirSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { startServer } from './tools/server.mjs';

const HERE = dirname(fileURLToPath(import.meta.url)), ROOT = resolve(HERE, '..');
const UPDATE = process.argv.includes('--update'), ONLY = process.argv.find((a) => a.startsWith('--only='))?.slice(7);
const T0 = Date.parse('2026-10-20T08:00:00Z');
const r2 = (x) => (x == null || !isFinite(x) ? null : Math.round(x * 100) / 100);
let failures = 0;
const fail = (msg) => { failures++; console.log('  ✗ ' + msg); };

const { srv, url } = await startServer(ROOT);
const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});

async function newPage(storage, native = false) {
  const ctx = await browser.newContext({ locale: 'es-ES', timezoneId: 'Europe/Madrid', viewport: { width: 400, height: 860 }, serviceWorkers: 'block' });
  await ctx.route('**/*', (r) => (r.request().url().startsWith(url) ? r.continue() : r.abort()));   // sin internet: mapas, clima, IA…
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.addInitScript((st) => {
    if (!sessionStorage.getItem('__seeded')) { sessionStorage.setItem('__seeded', '1'); for (const [k, v] of Object.entries(st)) localStorage.setItem(k, JSON.stringify(v)); }
    let cb = null; window.__geo = (f) => cb && cb(f);
    Object.defineProperty(navigator, 'geolocation', { value: { watchPosition: (ok) => { cb = ok; return 1; }, clearWatch: () => { cb = null; }, getCurrentPosition: () => {} } });
    window.speechSynthesis && (window.speechSynthesis.speak = () => {});
  }, storage);
  if (native) await page.addInitScript(() => {   // imita el APK: Capacitor con los plugins de archivos y compartir
    window.__shared = [];
    window.Capacitor = { isNativePlatform: () => true, Plugins: {
      Filesystem: { writeFile: async (o) => { window.__shared.push({ write: o }); return { uri: 'file:///data/cache/' + o.path }; } },
      Share: { share: async (o) => { window.__shared.push({ share: o }); } } } };
  });
  await page.clock.install({ time: T0 - 1000 });
  await page.clock.pauseAt(T0);   // el reloj solo avanza cuando la prueba lo dice: resultados idénticos en cada ejecución
  await page.goto(url);
  await page.waitForFunction(() => window.__z);
  await page.clock.runFor(1500);
  await page.evaluate(() => document.querySelectorAll('.modal.on').forEach((m) => m.classList.remove('on')));   // novedades, etc.
  return { ctx, page, errors };
}
const BASE = { 'zancada.profile.v1': { name: 'Prueba', goal: 3, done: true }, 'zancada.settings.v1': { voice: '0', autopause: '1' } };

// ---------- 1. carreras simuladas ----------
async function runTrack(tr) {
  const { ctx, page, errors } = await newPage(BASE);
  await page.evaluate(() => window.__z.go('prep'));
  const f0 = tr.fixes[0];
  const fix = (f, now) => page.evaluate(([f, ts]) => window.__geo({ coords: { latitude: f.lat, longitude: f.lon, accuracy: f.acc, altitude: f.alt }, timestamp: ts }), [f, now]);
  await fix(f0, T0); await page.clock.runFor(500);
  await page.click('#startBtn'); await page.clock.runFor(3000);   // cuenta atrás 3-2-1
  const runStart = await page.evaluate(() => window.__z.run.start);
  let now = 0; const evs = [...tr.events];
  const advance = async (to) => { if (to > now) { await page.clock.runFor(to - now); now = to; } };
  for (const f of tr.fixes) {
    while (evs.length && evs[0].at <= f.arrive) { const e = evs.shift(); await advance(e.at); await page.evaluate((ev) => (ev === 'pause' ? window.__z.pauseRun(false) : window.__z.resumeRun()), e.ev); }
    await advance(f.arrive);
    await fix(f, runStart + f.t);
  }
  await advance(tr.end);
  await page.evaluate(() => window.__z.finishRun());
  await page.clock.runFor(300);
  const out = await page.evaluate(() => {
    const z = window.__z, r = z.runs[0], on = document.querySelector('.screen.on')?.id, modal = document.querySelector('#shortModal.on');
    if (!r) return { saved: false, screen: on, askShort: !!modal };
    return { saved: true, screen: on, dist: r.dist, dur: r.dur, nPoints: r.points.length, gain: r.gain, splits: r.splits,
      best: Object.fromEntries([1000, 5000, 10000, 21097.5].map((m) => [m, z.bestEffort(r, m)])),
      bestFor: Object.fromEntries([1000, 5000, 10000].map((m) => [m, z.bestFor(r, m)])),
      cele: { dist: document.querySelector('#cDist').textContent, time: document.querySelector('#celeTime').textContent, pace: document.querySelector('#cPace').textContent },
      run: r };
  });
  await ctx.close();
  const res = out.saved ? { saved: true, screen: out.screen, dist: r2(out.dist), dur: r2(out.dur), nPoints: out.nPoints, gain: out.gain,
    splits: out.splits.map((s) => ({ km: s.km, split: r2(s.split), ...(s.partial ? { partial: true } : {}) })),
    best: Object.fromEntries(Object.entries(out.best).map(([k, v]) => [k, r2(v)])), bestFor: Object.fromEntries(Object.entries(out.bestFor).map(([k, v]) => [k, r2(v)])), cele: out.cele }
    : { saved: false, screen: out.screen, askShort: out.askShort };
  return { res, errors, run: out.run };
}

function compare(name, got, want, path = '') {
  if (typeof want === 'number' && typeof got === 'number') { if (Math.abs(got - want) > 0.011) fail(`${name}${path}: esperaba ${want}, sale ${got}`); return; }
  if (want && typeof want === 'object') {
    const keys = new Set([...Object.keys(want), ...Object.keys(got || {})]);
    for (const k of keys) compare(name, got?.[k], want[k], `${path}.${k}`); return;
  }
  if (got !== want) fail(`${name}${path}: esperaba ${JSON.stringify(want)}, sale ${JSON.stringify(got)}`);
}

const tracks = readdirSync(join(HERE, 'fixtures', 'tracks')).filter((f) => f.endsWith('.json') && (!ONLY || f.startsWith(ONLY))).sort();
const savedRuns = [];
console.log('1. Carreras simuladas');
for (const file of tracks) {
  const tr = JSON.parse(readFileSync(join(HERE, 'fixtures', 'tracks', file), 'utf8')), name = file.replace('.json', '');
  const t = Date.now(); const { res, errors, run } = await runTrack(tr);
  if (run) savedRuns.push(run);
  const gfile = join(HERE, 'golden', name + '.json');
  const before = failures;
  errors.forEach((e) => fail(`${name}: error de JavaScript: ${e}`));
  if (UPDATE) writeFileSync(gfile, JSON.stringify({ track: name, desc: tr.desc, expected: res }, null, 1) + '\n');
  else if (!existsSync(gfile)) fail(`${name}: no hay referencia (ejecuta con --update)`);
  else compare(name, res, JSON.parse(readFileSync(gfile, 'utf8')).expected);
  console.log(`  ${failures === before ? '✓' : '✗'} ${name.padEnd(20)} ${res.saved ? `${(res.dist / 1000).toFixed(2)} km · ${res.cele.time} · ${res.cele.pace}/km` : 'no se guarda'}  (${((Date.now() - t) / 1000).toFixed(1)} s)`);
}

// ---------- 2. todas las pantallas con datos ----------
console.log('2. Pantallas con datos');
{
  const before = failures;
  savedRuns.sort((a, b) => b.start - a.start);
  const { ctx, page, errors } = await newPage({ ...BASE, 'zancada.runs.v1': savedRuns });
  const screens = ['home', 'history', 'routes', 'profile', 'plan', 'clubs', 'heat'];
  for (const s of screens) { await page.evaluate((s) => window.__z.go(s), s); await page.clock.runFor(400); }
  const nRoutes = await page.evaluate(() => { window.__z.go('routes'); return document.querySelectorAll('#routesList > *').length; });
  if (nRoutes < 3) fail(`Rutas: la lista sale casi vacía (${nRoutes} elementos)`);
  for (const r of savedRuns) { await page.evaluate((id) => window.__z.openSummary(id), r.id); await page.clock.runFor(300); }
  await page.evaluate(() => window.__z.openReplay()); await page.clock.runFor(2000);
  const recs = await page.evaluate(() => window.__z.personalRecords().map((x) => x.label + ': ' + x.val));
  if (!savedRuns.length || recs.length < 3) fail(`Perfil: faltan mejores marcas (${recs.join(', ')})`);
  errors.forEach((e) => fail(`pantallas: error de JavaScript: ${e}`));
  await ctx.close();
  console.log(`  ${failures === before ? '✓' : '✗'} ${screens.length} pantallas, ${savedRuns.length} resúmenes y repetición · ${nRoutes} rutas · récords: ${recs.join(' · ')}`);
}

// ---------- 3. copia de seguridad dentro del APK ----------
console.log('3. Copia de seguridad en el APK');
{
  const before = failures;
  // Además de las carreras grabadas, una añadida a mano (cinta), que no tiene puntos.
  const manual = { id: 'm' + (T0 - 2 * 864e5), start: T0 - 2 * 864e5, name: 'Cinta del gimnasio', points: [], dist: 6000, dur: 1980, splits: [], manual: true };
  // Todas las simuladas empiezan a la misma hora: en la copia se reparten en días distintos, como en la vida real.
  const backupRuns = [...savedRuns.map((r, i) => ({ ...r, id: 'r' + (r.start - (i + 3) * 864e5), start: r.start - (i + 3) * 864e5 })), manual];
  const { ctx, page, errors } = await newPage({ ...BASE, 'zancada.runs.v1': backupRuns, 'zancada.shoes.v1': [{ id: 's1', name: 'Prueba', km0: 0, created: T0 }] }, true);
  await page.evaluate(() => { window.__z.go('profile'); document.querySelector('#backupBtn').click(); });
  await page.waitForFunction(() => window.__shared.some((x) => x.share), null, { timeout: 5000 }).catch(() => {});
  const sh = await page.evaluate(() => window.__shared);
  const w = sh.find((x) => x.write)?.write, s2 = sh.find((x) => x.share)?.share;
  if (!w || !s2) fail('Exportar copia no llama a los plugins de archivo y compartir del APK');
  else {
    let data = null; try { data = JSON.parse(w.data); } catch (e) { fail('la copia no es JSON válido'); }
    if (data) {
      if (data.app !== 'zancada') fail('la copia no lleva la marca app=zancada');
      if ((data['zancada.runs.v1'] || []).length !== backupRuns.length) fail(`la copia lleva ${(data['zancada.runs.v1'] || []).length} carreras, esperaba ${backupRuns.length}`);
      // La app nativa importa esta misma copia en sus pruebas (nativa/core BackupImportTest).
      const bfile = join(HERE, 'fixtures/backup/zancada-copia.json');
      if (UPDATE) { mkdirSync(dirname(bfile), { recursive: true }); writeFileSync(bfile, w.data + '\n'); }
      else if (!existsSync(bfile)) fail('falta tests/fixtures/backup/zancada-copia.json (ejecuta con --update)');
      else if (JSON.stringify(Object.keys(JSON.parse(readFileSync(bfile, 'utf8'))).sort()) !== JSON.stringify(Object.keys(data).sort())) fail('la copia ha cambiado de formato: revisa la importación de la app nativa y vuelve a generar con --update');
      if (!(data['zancada.shoes.v1'] || []).length) fail('la copia no lleva las zapatillas');
      if (w.directory !== 'CACHE' || w.encoding !== 'utf8') fail('la copia se escribe en un sitio o formato inesperado');
      if (!(s2.files || [])[0]?.endsWith(w.path)) fail('el menú de compartir no recibe el archivo');
    }
  }
  errors.forEach((e) => fail(`copia: error de JavaScript: ${e}`));
  await ctx.close();
  console.log(`  ${failures === before ? '✓' : '✗'} Exportar copia escribe ${w ? Math.round(w.data.length / 1024) + ' KB' : 'nada'} y abre el menú de compartir`);
}

// ---------- 4. seguridad: un GPX o una copia con HTML en los nombres no puede ejecutar código ----------
console.log('4. Seguridad: nombres con código');
{
  const before = failures, EVIL = '<img src=x onerror="window.__pwned=1">';
  const gpx = (tag) => `<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><${tag === 'trk' ? 'trk' : 'rte'}><name>${EVIL.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')}</name>${tag === 'trk' ? '<trkseg>' : ''}` +
    Array.from({ length: 40 }, (_, i) => `<${tag === 'trk' ? 'trkpt' : 'rtept'} lat="${40.41 + i * 0.0003}" lon="-3.68"><ele>650</ele>${tag === 'trk' ? `<time>${new Date(T0 - 864e5 + i * 10000).toISOString()}</time>` : ''}</${tag === 'trk' ? 'trkpt' : 'rtept'}>`).join('') + `${tag === 'trk' ? '</trkseg></trk>' : '</rte>'}</gpx>`;
  const evilRun = { ...(savedRuns[0] || { points: [], splits: [], dist: 0, dur: 0 }), id: 'evil', start: T0 - 2 * 864e5, name: EVIL, route: { id: 'x', name: EVIL } };
  const { ctx, page, errors } = await newPage({ ...BASE, 'zancada.runs.v1': [evilRun], 'zancada.shoes.v1': [{ id: 's1', name: EVIL, km0: 0, created: T0 }], 'zancada.routes.v1': [], 'zancada.profile.v1': { name: EVIL, goal: 3, done: true } });
  await page.setInputFiles('#gpxFiles', { name: 'a.gpx', mimeType: 'application/gpx+xml', buffer: Buffer.from(gpx('trk')) });
  await page.clock.runFor(500);
  await page.evaluate(() => { window.__z.go('routes'); document.querySelector('[data-mode="mine"]')?.click(); });
  await page.setInputFiles('#routeGpx', { name: 'b.gpx', mimeType: 'application/gpx+xml', buffer: Buffer.from(gpx('rte')) });
  await page.clock.runFor(500);
  for (const sc of ['home', 'history', 'routes', 'profile', 'plan']) { await page.evaluate((sc) => window.__z.go(sc), sc); await page.clock.runFor(300); }
  await page.evaluate(() => { document.querySelector('[data-mode="mine"]')?.click(); window.__z.go('profile'); });
  for (const r of await page.evaluate(() => window.__z.runs.map((x) => x.id))) { await page.evaluate((id) => window.__z.openSummary(id), r); await page.clock.runFor(300); }
  const pwned = await page.evaluate(() => !!window.__pwned || !!document.querySelector('img[src="x"]'));
  const imported = await page.evaluate(() => ({ runs: window.__z.runs.length, routes: JSON.parse(localStorage.getItem('zancada.routes.v1') || '[]').length }));
  if (imported.runs < 2 || imported.routes < 1) fail(`la prueba no llegó a importar los GPX (${JSON.stringify(imported)})`);
  if (pwned) fail('un nombre con código HTML (GPX importado o copia restaurada) se ejecuta en la app');
  errors.forEach((e) => fail(`seguridad: error de JavaScript: ${e}`));
  await ctx.close();
  console.log(`  ${failures === before ? '✓' : '✗'} GPX de carrera, GPX de ruta, zapatillas y nombre con código: no se ejecuta`);
}

await browser.close(); srv.close();
if (UPDATE) console.log('\nReferencias actualizadas en tests/golden/.');
console.log(failures ? `\n${failures} fallo(s).` : '\nTodo en orden.');
process.exit(failures ? 1 : 0);
