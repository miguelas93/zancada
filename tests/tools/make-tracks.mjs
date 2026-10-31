// Genera carreras GPS de prueba, siempre iguales (semilla fija).
// Cada pista es una lista de lecturas tal como las entregaría el GPS:
//   { t: ms desde el inicio (hora de la lectura), arrive: ms en que llega a la app, lat, lon, acc, alt }
// y eventos { at: ms, ev: 'pause' | 'resume' }.
// Uso: node tests/tools/make-tracks.mjs   (reescribe tests/fixtures/tracks/*.json)
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'fixtures', 'tracks');
mkdirSync(OUT, { recursive: true });

function rng(seed) { return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
const gauss = (r) => { let u = 0, v = 0; while (!u) u = r(); while (!v) v = r(); return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v); };
const LAT0 = 40.4153, LON0 = -3.6845; // Retiro, Madrid
const M_LAT = 111320, M_LON = 111320 * Math.cos(LAT0 * Math.PI / 180);
const toLL = (x, y) => ({ lat: +(LAT0 + y / M_LAT).toFixed(7), lon: +(LON0 + x / M_LON).toFixed(7) });

// Recorrido: función s (metros recorridos) → {x, y}. Velocidad v(t) en m/s.
const circle = (R) => (s) => ({ x: R * Math.sin(s / R), y: R - R * Math.cos(s / R) });
// Zigzag a ±30° (calles que giran): cada metro de s es un metro de recorrido.
const zigzag = (seg) => (s) => { const k = Math.floor(s / seg), f = s - k * seg, c = Math.cos(Math.PI / 6), sn = Math.sin(Math.PI / 6); return { x: s * c, y: (k % 2 ? seg - f : f) * sn }; };
const straight = () => (s) => ({ x: s * 0.8, y: s * 0.6 });

function build({ seed, path, total, speed, hz = 1, noise = 3, acc = [6, 15], alt = () => 650, altNoise = 0.8, extra = () => null }) {
  const r = rng(seed), fixes = [], events = [];
  let t = 0, s = 0; const dt = 1000 / hz;
  const st = { r, fixes, events, skip: 0 };
  // Error del GPS con memoria (AR(1)): se desvía poco a poco, como un GPS real, en vez de saltar cada segundo.
  const a = 0.97, k = Math.sqrt(1 - a * a); let ex = gauss(r) * noise, ey = gauss(r) * noise, ez = gauss(r) * altNoise;
  while (s < total) {
    t += dt; const v = typeof speed === 'function' ? speed(s, t, r) : speed; s += v * dt / 1000;
    const hook = extra(st, s, t); if (hook === 'skip') continue;
    ex = a * ex + k * noise * gauss(r); ey = a * ey + k * noise * gauss(r);
    const p = path(s), n = { x: p.x + ex, y: p.y + ey };
    const ll = toLL(n.x, n.y);
    fixes.push({ t, arrive: t + Math.round(r() * 300), ...ll, acc: Math.round(acc[0] + r() * (acc[1] - acc[0])), alt: +(alt(s) + (ez = a * ez + k * altNoise * gauss(r))).toFixed(1) });
  }
  return { fixes, events, end: t + 1500 };
}

const tracks = {
  // 2 vueltas a un circuito de 2,5 km a ~5:20/km
  'parque-5k': { desc: 'Dos vueltas a un parque, GPS bueno, 1 lectura/s', ...build({ seed: 1, path: circle(400), total: 5030, speed: (s, t, r) => 3.1 + 0.15 * Math.sin(t / 60000) + (r() - 0.5) * 0.2, alt: (s) => 650 + 6 * Math.sin(s / 400) }) },
  // Bajo árboles: lecturas imprecisas (se descartan) y saltos imposibles
  'arboles-ruido': { desc: 'Bajo árboles: 10 % de lecturas con ±55-90 m y saltos de 80 m', ...build({ seed: 2, path: zigzag(300), total: 3200, speed: 2.9, noise: 5, acc: [8, 48], extra: (st, s, t) => {
    const r = st.r; if (r() < 0.1) { const p = zigzag(300)(s); st.fixes.push({ t, arrive: t + 100, ...toLL(p.x + 40, p.y - 30), acc: 55 + Math.round(r() * 35), alt: 650 }); return 'skip'; }
    if (r() < 0.01) { const p = zigzag(300)(s); st.fixes.push({ t, arrive: t + 100, ...toLL(p.x + 80, p.y + 10), acc: 12, alt: 650 }); return 'skip'; }
    return null; } }) },
  // Pausa manual de 60 s andando 40 m (no debe contar), luego sigue
  'pausa-manual': { desc: 'Pausa manual de 60 s a los 2 km; lo andado en pausa no cuenta', ...(() => {
    let paused = false, done = false;
    const b = build({ seed: 3, path: straight(), total: 3600, speed: (s) => (paused ? 0.7 : 3.0), extra: (st, s, t) => {
      if (!done && !paused && s >= 2000) { paused = true; st.pauseT = t; st.events.push({ at: t, ev: 'pause' }); }
      if (paused && t - st.pauseT >= 60000) { paused = false; done = true; st.events.push({ at: t, ev: 'resume' }); }
      return null; } });
    return b; })() },
  // Semáforo: quieto 30 s con autopausa, luego sigue sola
  'semaforo-autopausa': { desc: 'Parado 30 s en un semáforo con autopausa activada', ...(() => {
    let stopT = null;
    return build({ seed: 4, path: straight(), total: 2500, noise: 1.5, speed: (s, t) => { if (s >= 1200 && stopT == null) stopT = t; return stopT != null && t - stopT < 30000 ? 0 : 3.0; } }); })() },
  // Pantalla bloqueada: 120 s sin lecturas y luego llegan de golpe (como el plugin al despertar); después 90 s sin nada
  'pantalla-bloqueada': { desc: 'Lecturas que llegan de golpe tras 120 s y un hueco de 90 s sin GPS', ...(() => {
    const b = build({ seed: 5, path: circle(350), total: 4000, speed: 3.2 });
    b.fixes.forEach((f) => { if (f.t > 600000 && f.t <= 720000) f.arrive = 720500; });
    b.fixes = b.fixes.filter((f) => !(f.t > 900000 && f.t <= 990000));
    return b; })() },
  // Cuestas: subida de 60 m con ruido de altura
  'cuestas': { desc: 'Ida y vuelta con 60 m de subida, ruido de altímetro ±3 m', ...build({ seed: 6, path: straight(), total: 4000, speed: (s) => (s < 2000 ? 2.6 : 3.3), alt: (s) => 650 + (s < 2000 ? s * 0.03 : 60 - (s - 2000) * 0.03), altNoise: 3 }) },
  // Carrera corta: 30 m, la app pregunta si guardarla
  'corta': { desc: 'Solo 20 m: la app pregunta si guardarla (se descarta)', ...build({ seed: 7, path: straight(), total: 20, speed: 2.5, noise: 0.5 }) },
  // Media maratón a 0,5 lecturas/s
  'media-maraton': { desc: 'Media maratón (21,1 km), 1 lectura cada 2 s', ...build({ seed: 8, path: circle(1200), total: 21150, speed: (s, t, r) => 3.0 - s / 21100 * 0.25 + (r() - 0.5) * 0.15, hz: 0.5, alt: (s) => 650 + 10 * Math.sin(s / 1200) }) },
};

for (const [name, tr] of Object.entries(tracks)) {
  tr.fixes.sort((a, b) => a.arrive - b.arrive || a.t - b.t);
  writeFileSync(join(OUT, name + '.json'), JSON.stringify({ name, desc: tr.desc, end: tr.end, events: tr.events, fixes: tr.fixes }) + '\n');
  console.log(name.padEnd(20), tr.fixes.length, 'lecturas');
}
