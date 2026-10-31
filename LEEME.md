# Zancada · beta 0.4 · plan de entrenamiento y rutas (31 oct 2026)

Versión muy beta para probar en el móvil desde el navegador. Sin cuenta: todo se guarda en el propio móvil.

## Qué hace
- Graba la carrera con el GPS del móvil (pide permiso de ubicación).
- Distancia, tiempo y ritmo en directo; parciales por km y aviso de voz en cada km.
- Pausa, bloqueo de pantalla y "mantén pulsado para terminar".
- Pantalla de fin de carrera con distancia, tiempo, ritmo y progreso del objetivo semanal.
- Resumen con la ruta sobre mapa de calles (OpenStreetMap, en gris) o satélite (Esri), parciales y compartir.
- Repetición animada: el punto recorre la ruta sobre el mapa real, con km, tiempo y ritmo sincronizados, velocidad 1×/2×/4× y barra para saltar.
- Actividad del mes (km por día y lista de carreras), Clubs (avance: llegará con las cuentas) y Perfil con mejores marcas (1 km, 5K, 10K, media y maratón, calculadas del GPS) y ajustes.
- Diseño "Volt": tinta + un único acento lima, Space Grotesk para cifras e Inter para texto. Toda la app pasa a modo noche de 20:00 a 7:00 o con el móvil en oscuro (se puede forzar en Perfil).
- Se puede "Añadir a pantalla de inicio" (PWA) o instalar el APK de Android (ver abajo).

## Novedades 0.4
- **Plan de entrenamiento** (tarjeta en Inicio): objetivo (5K, 10K, media o hábito), días de la semana, nivel y 6–12 semanas. Genera cada semana rodajes suaves, series, tempo y rodaje largo, con semana de descarga cada cuatro y afinado antes de la carrera.
- **Revisión semanal automática**: cada lunes compara lo corrido con lo previsto y sube (+5 %) o baja (−10 % / −15 %) la carga de las semanas siguientes. El motivo se ve en Inicio y en el plan.
- **Sesión guiada**: al salir a correr el día de una sesión, Preparar la propone ("Sesión del plan" / "Carrera libre"). En series y tempo la pantalla de carrera marca el tramo actual (calentamiento, serie 3 de 7, recupera, vuelta a la calma) con metros restantes y aviso de voz.
- **Avisos**: en el APK, notificación del móvil a la hora elegida los días de entreno (sin servidor, se programan en el propio teléfono). En la web no hay avisos; la sesión del día se ve en Inicio.
- **Rutas** (pestaña nueva, en el sitio de Clubs, que pasa a Perfil hasta que haya cuentas): "Para ti" genera tres bucles de 3 a 15 km desde donde estás con el enrutador peatonal de OpenStreetMap (Valhalla, FOSSGIS), con desnivel positivo, dificultad y tiempo estimado a tu ritmo. "Mis recorridos" guarda rutas y permite repetir cualquier carrera. Al correr una ruta se dibuja en el mapa y la app avisa si te sales más de 60 m.
- Correcciones: el botón atrás del móvil vuelve a la pantalla anterior y nunca cierra la app en plena carrera; el GPS se apaga al salir de Preparar sin correr; lo que andes en pausa ya no suma distancia.

## Cómo probarla
El GPS del navegador solo funciona en https. Publicada en GitHub Pages: abrir la URL en el móvil, aceptar la ubicación, pulsar el botón lima.
En el perfil hay un botón para cargar una carrera de ejemplo y ver resumen y repetición sin salir a correr.

## Archivos
- `index.html` · toda la app (HTML, CSS y JS en un archivo)
- `manifest.json`, `sw.js`, `icon-192.png`, `icon-512.png` · instalación como app
- `vendor/leaflet/` · librería de mapas Leaflet 1.9.4 (BSD-2)

## Limitaciones conocidas
- Las rutas "Para ti" dependen de un servicio público de OpenStreetMap: con mucha carga puede fallar o tardar; se reintenta con "Generar otras".
- Los avisos del plan solo existen en el APK; Android 13+ pide permiso de notificaciones la primera vez.
- En iPhone, Safari corta el GPS si la pantalla se apaga o la app pasa a segundo plano: dejar la pantalla encendida (hay bloqueo de pantalla dentro de la app).
- Los datos viven en el navegador del móvil; si se borran los datos del sitio, se pierden las carreras.
- No hay cuenta, amigos ni ligas: eso llega con la app nativa.
