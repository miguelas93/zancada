// Servidor local para las pruebas: sirve la web del repo tal cual, pero en index.html
// añade un "gancho" que expone funciones internas en window.__z (solo en las pruebas;
// el index.html publicado no cambia).
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };
export const HOOK = `{ const pt = L.Map.prototype.panTo; L.Map.prototype.panTo = function (c, o) { return pt.call(this, c, Object.assign({}, o, { animate: false })); }; }   // sin animaciones de mapa: las pruebas van más rápido
Object.assign(window, { __z: { run, runFix, onFix, finishRun, finalizeRun, haversine, bestEffort, bestFor, elevGain, personalRecords, badges, weekStats,
  fmtPace, fmtTime, fmtKm, go, pauseRun, resumeRun, openSummary, openReplay, get runs() { return runs; }, get settings() { return settings; } } });
// Para la app nativa: todo lo que calcula la web, para comparar resultados (tests/tools/dump-app.mjs).
Object.assign(window.__z, { fmtDate, fmtKmS, fmtM, fmtDur, fmtAway, fmtMin, listEs, runName, weekStart, bestStreak, raceBase, userPace, targetPace, shoeKm, compareRun, runInsights,
  paceSegments, sessDone, detectRoute, bestOnRoute, ghostDistAt, ghostTimeAt, planWeekKm, planSessions, planStatus, reviewPlan, lightSession, todaySession, nextSession, histWeeklyKm,
  buildGuide, tramosSession, goalSession, TRAMOS, routeMeta, recList, pickToday, withAway, myRoutes, nearCity, decodePoly6, buildNav, navProgress, bearing, angDiff, compass, dest,
  parseGpx, runGpx, parseRouteGpx, wxText, bestHour, coachCtx, coachSys, coachClean, phScore, demoRun, ZR, GOALS, LEVELS, BADGE_DEFS, FEEL_L, STRETCHES, WARMUP,
  renderHistory, renderProfile, renderHome, renderPlan, renderRoutes, renderChallenges, renderMonthGoal, renderTrend, renderMonths, renderPaces, renderCalendar, renderRecap,
  get plan() { return plan; }, set plan(v) { plan = v; }, get rt() { return rt; }, get savedRoutes() { return savedRoutes; }, set savedRoutes(v) { savedRoutes = v; },
  get shoes() { return shoes; }, get profile() { return profile; }, set runsV(v) { runs = v; } });\n`;

export function startServer(root, port = 0) {
  const srv = createServer(async (req, res) => {
    try {
      let p = decodeURIComponent(new URL(req.url, 'http://x').pathname); if (p.endsWith('/')) p += 'index.html';
      const file = normalize(join(root, p)); if (!file.startsWith(root)) throw new Error('fuera');
      let body = await readFile(file);
      if (p === '/index.html') { const s = body.toString('utf8'), i = s.lastIndexOf('})();'); if (i < 0) throw new Error('no encuentro el final del script principal'); body = s.slice(0, i) + HOOK + s.slice(i); }
      res.writeHead(200, { 'content-type': TYPES[extname(file)] || 'application/octet-stream' }); res.end(body);
    } catch (e) { res.writeHead(404); res.end(); }
  });
  return new Promise((ok) => srv.listen(port, '127.0.0.1', () => ok({ srv, url: `http://localhost:${srv.address().port}/` })));
}
