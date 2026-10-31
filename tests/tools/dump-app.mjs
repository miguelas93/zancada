// Referencia para la app nativa: carga la web con un juego de datos fijo y un reloj fijo, y guarda en
// tests/golden/app.json todo lo que la web calcula (récords, insignias, plan, rutas, guía, textos…).
// La app nativa (nativa/core, AppParityTest) tiene que dar exactamente lo mismo.
// Uso: node tools/dump-app.mjs            → compara con la referencia guardada (falla si cambia)
//      node tools/dump-app.mjs --update   → la vuelve a generar
import { createRequire } from 'node:module';
const { chromium } = createRequire(import.meta.url)('playwright');
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { startServer } from './server.mjs';

const HERE = dirname(fileURLToPath(import.meta.url)), ROOT = resolve(HERE, '../..'), OUT = join(HERE, '../golden/app.json'), DATA = join(HERE, '../fixtures/app-data.json');
const UPDATE = process.argv.includes('--update');
const T0 = Date.parse('2026-10-21T10:30:00+02:00');   // miércoles

// ---- juego de datos (se guarda en fixtures/app-data.json para que la app nativa cargue exactamente lo mismo)
function buildData(ZR) {
  const bk = JSON.parse(readFileSync(join(HERE, '../fixtures/backup/zancada-copia.json'), 'utf8'));
  const runs = bk['zancada.runs.v1'].map((r) => JSON.parse(JSON.stringify(r)));
  // dos carreras esta semana (lunes y martes), copias de otras con otra fecha
  const shift = (r, start, extra) => Object.assign(JSON.parse(JSON.stringify(r)), { id: 'r' + start, start }, extra);
  const mon = Date.parse('2026-10-19T19:10:00+02:00'), tue = Date.parse('2026-10-20T07:40:00+02:00');
  runs.push(shift(runs[6], mon, { feel: 4, note: 'Con Toci por el parque', shoe: 's1' }));
  runs.push(shift(runs[4], tue, { session: { key: 'w2d2', week: 2, title: 'Rodaje suave', m: 5000, type: 'suave', light: false, custom: false }, shoe: 's1' }));
  // una carrera por una ruta recomendada (trazado de la ruta a 3 m/s) para detectar ruta, fantasma y «veces»
  const rr = ZR[0], pts = []; let t = 0, prev = null;
  for (const [lat, lon] of rr.ll) { if (prev) { const d = hav(prev, { lat, lon }); if (d < 1) continue; t += d / 3 * 1000; } pts.push({ lat, lon, t: Math.round(t) }); prev = { lat, lon }; }
  const rs = Date.parse('2026-10-16T18:20:00+02:00');
  runs.push({ id: 'route-' + rs, start: rs, name: 'Por la Dehesa', points: pts, dist: 0, dur: 0, splits: [], route: { id: rr.id, name: rr.name } });
  runs.sort((a, b) => b.start - a.start);
  const plan = { goal: '10k', weeks: 8, days: [0, 2, 4, 6], level: 'medio', start: Date.parse('2026-10-05T00:00:00+02:00'), created: Date.parse('2026-10-06T09:00:00+02:00'), factor: [1, 1, 1, 1, 1, 1, 1, 1], reviewed: 0, remind: { on: false, hour: 8 } };
  return {
    'zancada.runs.v1': runs, 'zancada.plan.v1': plan,
    'zancada.profile.v1': { name: 'Miguel', goal: 4, done: true },
    'zancada.settings.v1': { voice: '1', night: 'auto', map: 'sat', autopause: '1', shoe: 's1', monthkm: '80', bigtext: '0', newsSeen: '0.60', startSeen: '1' },
    'zancada.shoes.v1': [{ id: 's1', name: 'Pegasus 41', km0: 120, created: Date.parse('2026-09-01T10:00:00+02:00') }],
    'zancada.routes.v1': [{ id: 'gpx-1', name: 'Vuelta al barrio', ll: [[40.41, -3.69], [40.412, -3.692], [40.415, -3.689], [40.413, -3.686], [40.41, -3.69]], km: 1.6, gain: null, created: Date.parse('2026-10-01T10:00:00+02:00') }],
  };
}
function hav(a, b) { const R = 6371000, r = Math.PI / 180, dLat = (b.lat - a.lat) * r, dLon = (b.lon - a.lon) * r, h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLon / 2) ** 2; return 2 * R * Math.asin(Math.sqrt(h)); }

const { srv, url } = await startServer(ROOT);
const browser = await chromium.launch();
const ctx = await browser.newContext({ locale: 'es-ES', timezoneId: 'Europe/Madrid', viewport: { width: 400, height: 860 }, serviceWorkers: 'block' });
await ctx.route('**/*', (r) => (r.request().url().startsWith(url) ? r.continue() : r.abort()));
const page = await ctx.newPage();
const errors = []; page.on('pageerror', (e) => errors.push(String(e)));

// 1) primero, sin datos, para leer las rutas recomendadas (ZR) y montar el juego de datos
await page.clock.install({ time: T0 - 1000 }); await page.clock.pauseAt(T0);
await page.goto(url); await page.waitForFunction(() => window.__z);
const ZR = await page.evaluate(() => window.__z.ZR.map((r) => ({ id: r.id, name: r.name, ll: r.ll })));
let data;
if (UPDATE || !existsSync(DATA)) { data = buildData(ZR); data['zancada.runs.v1'] = await page.evaluate((runs) => runs.map((r) => (r.dist ? r : Object.assign(window.__z.finalizeRun(r), { name: r.name, route: r.route }))), data['zancada.runs.v1']); writeFileSync(DATA, JSON.stringify(data) + '\n'); }
else data = JSON.parse(readFileSync(DATA, 'utf8'));
await page.close();

// 2) con los datos
const p2 = await ctx.newPage(); p2.on('pageerror', (e) => errors.push(String(e)));
await p2.addInitScript((st) => { if (!sessionStorage.getItem('s')) { sessionStorage.setItem('s', 1); localStorage.clear(); for (const [k, v] of Object.entries(st)) localStorage.setItem(k, JSON.stringify(v)); } }, data);
await p2.clock.install({ time: T0 - 1000 }); await p2.clock.pauseAt(T0);
await p2.goto(url); await p2.waitForFunction(() => window.__z); await p2.clock.runFor(200);

const out = await p2.evaluate(() => {
  const z = window.__z, R = z.runs, fin = (v) => (typeof v === 'number' && !isFinite(v) ? null : v);
  const clean = (o) => JSON.parse(JSON.stringify(o, (k, v) => (typeof v === 'number' && !isFinite(v) ? null : v)));
  const o = {};
  o.fmt = { pace: [0, 299.6, 300, 359.5, 359.4, 3600, 2401, 2399.6].map(z.fmtPace), time: [0, 59.5, 61, 3599.5, 3600, 7322].map(z.fmtTime), km: [0, 5, 994.9, 1005, 21097.5].map(z.fmtKm),
    kmS: [0, 500, 1000, 1500, 21097.5, 3333].map(z.fmtKmS), m: [5, 44, 950, 949, 1234, 12000].map(z.fmtM), dur: [59, 600, 3599, 3600, 5000].map(z.fmtDur), away: [0.2, 0.95, 1, 3.33, 12.6].map(z.fmtAway),
    min: [60, 90, 150, 300].map(z.fmtMin), list: [[], ['a'], ['a', 'b'], ['a', 'b', 'c']].map(z.listEs), date: R.slice(0, 4).map((r) => z.fmtDate(r.start)), runName: [7, 12, 19, 20, 23].map((h) => z.runName(new Date(2026, 9, 21, h).getTime())) };
  o.weekStart = [Date.now(), R[0].start, R[R.length - 1].start].map(z.weekStart);
  const ws = z.weekStats(); o.weekStats = { count: ws.count, daysDone: [...ws.daysDone].sort(), streak: ws.streak, today: ws.today };
  o.records = z.personalRecords().map((x) => ({ label: x.label, val: x.val, run: x.r?.id || null }));
  o.bestStreak = z.bestStreak(); o.raceBase = z.raceBase(); o.userPace = z.userPace();
  o.targetPace = ['suave', 'largo', 'tempo', 'series', 'carrera', 'otro'].map(z.targetPace);
  o.badges = z.badges().map((b) => ({ id: b.id, when: b.when }));
  o.shoeKm = z.shoes.map((s) => z.shoeKm(s));
  o.perRun = R.map((r) => ({ id: r.id, bestFor5: z.bestFor(r, 5000), bestFor10: z.bestFor(r, 10000), bestFor1: z.bestFor(r, 1000), effort1: fin(z.bestEffort(r, 1000)), effort5: fin(z.bestEffort(r, 5000)),
    compare: z.compareRun(r), insights: z.runInsights(r), segs: r.points?.length > 5 ? z.paceSegments(r.points).map((s) => [s.ll.length, Math.round(s.dist * 1000) / 1000, Math.round(s.pace * 1000) / 1000]) : [],
    detect: z.detectRoute(r), sessDone: r.session ? z.sessDone(r) : null }));
  o.bestOnRoute = z.ZR.slice(0, 3).map((x) => z.bestOnRoute(x.id)?.id || null);
  const ref = R.find((r) => r.route); o.ghost = [0, 30000, 120000, 9e9].map((t) => z.ghostDistAt(ref, t)).concat([0, 500, 1000, 1e9].map((d) => z.ghostTimeAt(ref, d)));
  // Actividad: lo pintado en pantalla
  z.renderHistory(); const txt = (s) => document.querySelector(s)?.textContent.replace(/\s+/g, ' ').trim();
  o.history = { monthKm: txt('#monthKm'), monthStats: txt('#monthStats'), weeksSub: txt('#weeksSub'), weekAxis: txt('#weekAxis'), weeksBars: txt('#weeksBars'), calTitle: txt('#calTitle'), calSub: txt('#calSub'),
    monthGoal: txt('#monthGoal'), challenges: txt('#challenges'), trendNote: txt('#trendNote'), trendSub: txt('#trendSub'), monthsSub: txt('#monthsSub'), months: txt('#monthsList'), paces: txt('#paces'), records: txt('#recGrid'),
    list: [...document.querySelectorAll('#histList .hist')].map((b) => b.textContent.replace(/\s+/g, ' ').trim()) };
  z.renderProfile(); o.profile = { goal: txt('#profGoal'), stats: txt('#profStats'), prs: txt('#prList'), badgeCount: txt('#badgeCount'), shoes: txt('#shoeList') };
  z.renderHome(); o.home = { date: txt('#homeDate'), hello: txt('#homeHello'), weekKm: txt('#weekKm'), weekGoal: txt('#weekGoal'), weekStats: txt('#weekStats'), nudge: txt('#homeNudge'), plan: txt('#planCard'), recap: txt('#recapCard'), last: txt('#lastRun') };
  // Plan
  const P = z.plan; o.plan = { factor: P.factor, reviewed: P.reviewed, lastReview: P.lastReview, weekKm: Array.from({ length: P.weeks }, (_, w) => z.planWeekKm(P, w)), hist: z.histWeeklyKm(),
    sessions: z.planStatus(P).map((w) => w.map((s) => ({ key: s.key, type: s.type, title: s.title, desc: s.desc, m: s.m, dayIdx: s.dayIdx, date: s.date, state: s.state, run: s.run?.id || null, moved: !!s.moved, struct: s.struct }))),
    today: z.todaySession()?.key || null, next: z.nextSession(z.planStatus(P))?.key || null, light: z.planSessions(P).flat().slice(0, 8).map((s) => { const l = z.lightSession(s); return [l.title, l.m]; }) };
  z.renderPlan(); o.planScreen = { title: txt('#planTitle'), sub: txt('#planSub'), pred: txt('#planPred'), week: txt('#planWeekCard'), weeks: txt('#planWeeks'), sessions: [...document.querySelectorAll('#planSessions .sess')].map((b) => b.textContent.replace(/\s+/g, ' ').trim()) };
  // Guía y tramos
  o.guide = z.planSessions(P)[3].map((s) => z.buildGuide(s));
  o.tramos = z.TRAMOS.map((t) => z.tramosSession(t[0])).concat([z.tramosSession('custom', { reps: 5, on: 600, off: 300 }), z.tramosSession('custom', { t: true, reps: 6, on: 90, off: 60 })]);
  o.goals = ['3000', '5000', '10000', 't20', 't30', 't60'].map(z.goalSession);
  // Rutas
  o.routes = { n: z.ZR.length, meta: z.ZR.map((r) => { const m = z.routeMeta(r); return [r.id, Math.round(m.time * 1000) / 1000, m.diff[0]]; }), poly: z.ZR.slice(0, 2).map((r) => r.ll.slice(0, 5)) };
  const rt = z.rt, lists = {};
  for (const [here, zone, dist, surf, sort] of [[null, null, 'all', 'all', 'near'], [null, 'sanfernando', 'all', 'all', 'near'], [{ lat: 40.4153, lon: -3.6845 }, null, 'all', 'all', 'near'], [{ lat: 40.4153, lon: -3.6845 }, 'cerca', 't30', 'all', 'near'], [null, 'madrid', 'all', 'tierra', 'long'], [null, 'madrid', 't60', 'all', 'flat'], [null, 'madrid', 'all', 'all', 'new'], [null, 'madrid', 'all', 'asfalto', 'short']]) {
    Object.assign(rt, { here, zone, dist, surf, sort }); const l = z.recList(); lists[[JSON.stringify(here), zone, dist, surf, sort].join('|')] = l.map((r) => [r.id, r.away == null ? null : Math.round(r.away * 1e6) / 1e6]);
    if (here && zone === null) { const t = z.pickToday(l); lists.today = t ? { id: t.r.id, why: t.why } : null; }
  }
  rt.here = null; o.routes.lists = lists; o.routes.nearCity = (rt.here = { lat: 36.47, lon: -6.19 }, z.nearCity()); rt.here = null;
  o.routes.mine = z.myRoutes().map((r) => [r.id, r.name, r.km, r.gain, r.src, r.sub]);
  o.nav = z.ZR.slice(0, 4).map((r) => { const n = z.buildNav(r.ll); return { pts: n.pts.length, total: Math.round(n.total * 1000) / 1000, turns: n.turns.map((t) => [t.i, Math.round(t.d * 100) / 100, Math.round(t.dv * 100) / 100, t.dir, t.sharp]) }; });
  o.geo = { bearing: z.bearing({ lat: 40.41, lon: -3.7 }, { lat: 40.42, lon: -3.69 }), dest: z.dest({ lat: 40.41, lon: -3.7 }, 75, 1500), compass: [0, 44, 46, 180, 359, -30].map(z.compass), ang: [[10, 350], [350, 10], [0, 180]].map(([a, b]) => z.angDiff(a, b)) };
  // GPX y textos
  const gpx = '<?xml version="1.0"?><gpx><metadata><name>Mi <b>carrera</b> &amp; más</name></metadata><trk><trkseg>' + Array.from({ length: 60 }, (_, i) => `<trkpt lat="${40.41 + i * 0.00025}" lon="${-3.68 + Math.sin(i / 5) * 0.0002}"><ele>${650 + Math.round(Math.sin(i / 8) * 9)}</ele><time>${new Date(Date.parse('2026-10-10T07:00:00Z') + i * 9000).toISOString()}</time></trkpt>`).join('') + '</trkseg></trk></gpx>';
  const g = z.parseGpx(gpx); o.gpx = { run: g && { id: g.id, start: g.start, name: g.name, dist: g.dist, dur: g.dur, splits: g.splits, gain: g.gain, n: g.points.length }, route: (() => { const x = z.parseRouteGpx(gpx); return x && { name: x.name, km: x.km, gain: x.gain, n: x.ll.length }; })(), out: z.runGpx(R.find((r) => r.points?.length > 1 && r.points.length < 900)) };
  o.wx = [{ t: 3, app: 1, wind: 30, rain: 60, now: false }, { t: 15, app: 15, wind: 8, rain: 10, now: false }, { t: 27, app: 29, wind: 5, rain: 0, now: true }, { t: 20, app: 21, wind: 12, rain: 50, now: false }].map(z.wxText);
  const hours = Array.from({ length: 24 }, (_, i) => i);
  o.bestHour = [z.bestHour({ hourly: { precipitation_probability: hours.map((h) => (h < 15 ? 70 : 5)), temperature_2m: hours.map((h) => 8 + h * 0.4) }, daily: { sunrise: ['2026-10-21T08:10'], sunset: ['2026-10-21T19:05'] } }, 10), z.bestHour({ hourly: { precipitation_probability: hours.map(() => 0), temperature_2m: hours.map(() => 12) }, daily: {} }, 10)];
  o.coach = { ctx: z.coachCtx(), clean: z.coachClean('Hola **tú**.\n\n---\nPowered by Pollinations 🌸 sponsor') };
  o.misc = { feel: z.FEEL_L, stretches: z.STRETCHES.length, warmup: z.WARMUP.length, demo: (() => { const d = z.demoRun(); return [d.points.length, Math.round(d.dist * 1000) / 1000]; })() };
  return clean(o);
});
await browser.close(); srv.close();
if (errors.length) { console.error('Errores de JavaScript:', errors); process.exit(1); }
const text = JSON.stringify(out, null, 1) + '\n';
if (UPDATE || !existsSync(OUT)) { writeFileSync(OUT, text); console.log('Referencia guardada en tests/golden/app.json'); }
else if (readFileSync(OUT, 'utf8') !== text) { writeFileSync(OUT + '.nuevo', text); console.error('✗ La web ya no calcula lo mismo que tests/golden/app.json (diferencias en app.json.nuevo)'); process.exit(1); }
else console.log('✓ La web sigue calculando lo mismo (tests/golden/app.json)');
