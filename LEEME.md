# Zancada · beta web 0.1 (28 oct 2026)

Versión muy beta para probar en el móvil desde el navegador. Sin cuenta: todo se guarda en el propio móvil.

## Qué hace
- Graba la carrera con el GPS del móvil (pide permiso de ubicación).
- Distancia, tiempo y ritmo en directo; parciales por km y aviso de voz en cada km.
- Pausa, bloqueo de pantalla y "mantén pulsado para terminar".
- Celebración con XP, objetivo semanal (3 carreras) y racha de semanas.
- Resumen con la ruta sobre mapa satélite (Esri) o de calles (CARTO), parciales y compartir.
- Repetición animada: el punto recorre la ruta sobre el mapa real, con km, tiempo y ritmo sincronizados, velocidad 1×/2×/4× y barra para saltar.
- Historial y perfil con ajustes (voz, modo noche al correr, tipo de mapa). Modo noche: automático de 20:00 a 7:00 o con el móvil en oscuro.
- Se puede "Añadir a pantalla de inicio" (PWA).

## Cómo probarla
El GPS del navegador solo funciona en https. Publicada en GitHub Pages: abrir la URL en el móvil, aceptar la ubicación, pulsar el botón azul.
En el perfil hay un botón para cargar una carrera de ejemplo y ver resumen y repetición sin salir a correr.

## Archivos
- `index.html` · toda la app (HTML, CSS y JS en un archivo)
- `manifest.json`, `sw.js`, `icon-192.png`, `icon-512.png` · instalación como app
- `vendor/leaflet/` · librería de mapas Leaflet 1.9.4 (BSD-2)

## Limitaciones conocidas
- En iPhone, Safari corta el GPS si la pantalla se apaga o la app pasa a segundo plano: dejar la pantalla encendida (hay bloqueo de pantalla dentro de la app).
- Los datos viven en el navegador del móvil; si se borran los datos del sitio, se pierden las carreras.
- No hay cuenta, amigos ni ligas: eso llega con la app nativa.
