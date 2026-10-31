// Zancada beta · service worker mínimo: deja la app abrir sin red, pero nunca cachea mapas ni GPS.
const CACHE = 'zancada-beta-v30';
const SHELL = ['./', './index.html', './manifest.json', './icon-192.png', './icon-512.png', './vendor/leaflet/leaflet.js', './vendor/leaflet/leaflet.css', './routes-data.js'];
self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (url.origin !== location.origin) return; // mapas y fuentes van siempre a red
  e.respondWith(fetch(e.request).then((r) => { const copy = r.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); return r; }).catch(() => caches.match(e.request)));
});
