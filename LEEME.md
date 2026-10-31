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

## Novedades 0.34 (31 oct 2026)

- **Ordenar rutas**: en Recomendadas puedes ordenar por cercanía, más cortas, más largas, más llanas o las que aún no has corrido.
- **Actividad**: el porcentaje de la semana frente a las anteriores solo sale cuando hay base suficiente y no se dispara con semanas casi vacías.

## Novedades 0.33 (31 oct 2026)
- Actividad › Calendario del mes: cada día corrido lleva un círculo lima proporcional a los km; toca el día para abrir la carrera. Con las flechas ves los meses anteriores.

## Novedades 0.32 (31 oct 2026)
- Estiramientos guiados de 5 minutos al terminar (pantalla de fin de carrera y resumen): 7 posturas de 40 s (gemelos, cuádriceps, isquios, glúteo) con explicación, cuenta atrás, voz y vibración al cambiar. Se puede pausar o saltar.

## Novedades 0.31 (31 oct 2026)
- Avisos de ritmo en sesiones guiadas (plan y tramos): la tarjeta de guía muestra el ritmo objetivo del tramo ("~6:03/km") y, si llevas 20 s fuera de margen, la voz te dice "Vas rápido, afloja a…" o "Un poco lento, sube a…" (como mucho una vez por minuto). Se puede desactivar en Perfil. No se usa en corre/anda.

## Novedades 0.30 (31 oct 2026)
- Arreglos tras una revisión a fondo:
  - Los tramos por tiempo (corre/anda, fartlek) se daban por "a medias" si no llegabas a unos km estimados; ahora cuentan por minutos ("12 de 29 min").
  - Un entreno por tramos ya no concede la insignia de "Sesión del plan cumplida".
  - Con "Tiempo" como dato grande, a partir de una hora el número se reduce para que no se salga de la pantalla.

## Novedades 0.29 (31 oct 2026)
- Perfil › "Zancada beta": "Invitar a un amigo" (comparte web y APK) y "Avisar de un fallo" (abre un aviso en GitHub con la versión y el móvil ya rellenados), más el enlace a las novedades.

## Novedades 0.28 (31 oct 2026)
- Rutas › Mis recorridos: "Importar una ruta GPX" (de un amigo, Wikiloc, Komoot…): se guarda con su trazado, km y desnivel, y se puede correr con guía como cualquier otra.
- Arreglo: los "Bucles desde aquí" volvían a salir sin desnivel por un choque de nombres en el código; ya lo calculan bien.

## Novedades 0.27 (31 oct 2026)
- Historial: buscador por nombre, ruta, sesión, objetivo o notas (aparece a partir de 6 carreras), combinable con los filtros.

## Novedades 0.26 (31 oct 2026)
- Resumen: botón "Volver a correr este recorrido": abre Preparar con ese trazado (o la ruta recomendada que corriste) y te compara en directo con tu mejor tiempo.
- Perfil › "Vibrar en los avisos": el móvil vibra con cada aviso de voz (km, tramos, giros), útil con la voz apagada o con música.

## Novedades 0.25 (31 oct 2026)
- Ficha de ruta: "Ahora mismo allí" (clima y ropa en la salida, a qué hora anochece o amanece, y aviso si vas de noche a un camino de tierra) y "Parecidas" (tres rutas de la misma zona con distancia similar, para seguir descubriendo sin volver a la lista).

## Novedades 0.24 (31 oct 2026)
- Bienvenida: eliges "Empiezo de cero" o "Ya corro". Con "Ya corro" no aparece la tarjeta de corre/anda en Inicio.
- Actividad › "Por meses": totales de los últimos 6 meses con carreras (km, número, tiempo) con barras comparables.

## Novedades 0.23 (31 oct 2026)
- La imagen para compartir lleva una etiqueta "RÉCORD · …" cuando la carrera batió alguno y, abajo, la comparación con tu última carrera parecida. El texto que acompaña a la imagen también menciona el récord.

## Novedades 0.22 (31 oct 2026)
- Resumen de la carrera: si has batido un récord (mejor km, 5 km, 10 km, más larga, más desnivel) lo dice arriba del todo, y compara el ritmo con tu última carrera parecida (misma ruta o distancia similar): "27 s/km más rápido que el 28 oct".

## Novedades 0.21 (31 oct 2026)
- Inicio muestra el clima de ahora y qué ropa llevar (misma fuente que Preparar), en cuanto la app conoce tu posición.
- Tarjeta "¿Empiezas de cero?" en Inicio (si llevas menos de 3 carreras y no tienes plan): te lleva directo al "Corre 1 min · anda 1,5 ×8" guiado por voz. Se puede quitar con "Ya corro".
- En los tramos por tiempo, la voz avisa "Diez segundos" antes de cada cambio.

## Novedades 0.20 (31 oct 2026)
- Tramos por tiempo: la guía puede avanzar con el reloj en vez de con los metros. Nuevas plantillas "Corre 1 min · anda 1,5 ×8" y "Corre 3 min · anda 1,5 ×5" para empezar a correr (estilo De cero a 5 km), y "Fartlek 10 × 1 min". En "A mi gusto" puedes elegir metros o minutos.
- La tarjeta de guía muestra el tiempo que queda del tramo y la voz avisa en cada cambio ("Corre, 1 min", "Anda 1,5 min").

## Novedades 0.19 (31 oct 2026)
- Entrenador IA (en Plan): preguntas sugeridas de un toque (semana que viene, carga, molestias, series, estrategia de carrera) y botones que abren ChatGPT o Claude con tu contexto y la pregunta ya escritos. El contexto incluye ahora tus récords. Sigue sin salir nada del móvil hasta que tú lo envíes.

## Novedades 0.18 (31 oct 2026)
- Actividad › "Tus ritmos": previsión de 5 km, 10 km y media maratón (fórmula de Riegel sobre tu mejor 5 o 10 km, o tu ritmo reciente) y tus ritmos de entreno (suave, largo, tempo, series).
- En carrera, toca el número grande para alternar Distancia → Tiempo → Ritmo medio; el dato desplazado baja al hueco pequeño. Se recuerda tu elección.

## Novedades 0.17 (31 oct 2026)
- Actividad › "Tus récords": mejor km, mejor 5 km y 10 km (a partir de los parciales), carrera más larga, más desnivel y mejor racha de semanas. Toca uno para abrir la carrera.
- Perfil › Datos: botón CSV que exporta todas tus carreras (fecha, km, tiempo, ritmo, desnivel, sensación, ruta, notas) para Excel o Sheets.

## Novedades 0.16 (31 oct 2026)
- Rutas › Recomendadas: el filtro ya no es por km sino por el tiempo que tienes ("¿Cuánto tiempo tienes?": 20, 30, 45 o más de 45 min), calculado a tu ritmo real y con el desnivel.
- Tarjeta "Para hoy" al principio de la lista: una propuesta razonada (cerca de ti, 25–50 min, que no hayas corrido esta semana) que cambia cada día, con "Correrla" y "Ver ficha".

## Novedades 0.15 (31 oct 2026)
- Objetivo en la carrera libre: en Preparar › Libre eliges 3, 5 o 10 km, o 20, 30 o 60 min. La tarjeta de carrera muestra lo que falta y la voz avisa al cumplirlo ("lo que corras ahora suma"). Se recuerda la última elección y queda anotado en el resumen.
- Ficha de ruta: "Compartir la ruta" (texto con km, desnivel, suelo y enlace a la salida en Google Maps, por Telegram/WhatsApp o al portapapeles) y "GPX de la ruta" para llevarla al reloj o a otra app.

## Novedades 0.14 (madrugada del 31 de octubre)
- **Rutas en el mapa**: botón Lista / Mapa en la pestaña Rutas. En el mapa ves todas las rutas recomendadas (o tus recorridos) con su trazado y una etiqueta con los km en la salida; respeta los filtros y toca cualquiera para abrir su ficha.
- **Perfil de desnivel en tus carreras**: si la carrera tiene altitud (GPS del móvil o GPX importado), el resumen muestra el perfil y el desnivel positivo.

## Novedades 0.13 (madrugada del 31 de octubre)
- **Ruta cerca** en Inicio: si ya diste permiso de ubicación, una tarjeta te enseña la ruta recomendada más cercana (a menos de 3 km), con minimapa, distancia y cuántas veces la has corrido. Toca y vas a su ficha.
- **Detección de ruta**: si sales a correr libre y tu trazado coincide con una ruta recomendada o guardada, la carrera se etiqueta sola; así la próxima vez el fantasma ya tiene con qué compararte.
- Botón "Usar" en el entreno a tu gusto.

## Novedades 0.12 (madrugada del 31 de octubre)
- **Entreno por tramos** (Preparar → Tramos): sin necesidad de plan, elige una plantilla clásica (6 × 400 m, 8 × 200 m, 5 × 1000 m, tempo 4 km, pirámide, progresivo 6 km) o monta la tuya (repeticiones × metros y recuperación). La app te guía por voz en cada tramo como en las sesiones del plan y recuerda la última que usaste.
- **Cuenta atrás**: la tarjeta del plan en Inicio dice cuántos días faltan para tu 5K, 10K o media.

## Novedades 0.11 (madrugada del 31 de octubre)
- **Texto más grande** (ajuste en Perfil) para leer mejor en carrera o con sol. Tamaños mínimos subidos (pestañas, insignias) y contorno visible al navegar con teclado o lector.
- **Desnivel en tus carreras**: la app guarda la altitud del GPS (y el `<ele>` de los GPX importados) y calcula el desnivel positivo con filtro de ruido; sale en el resumen y en Mis recorridos.
- **Aviso de carga**: si una semana llevas más de un 35 % de km sobre tu media de las cuatro anteriores, la lectura de la carrera te lo dice (regla del 10 %).
- El resumen se abre siempre desde arriba.

## Novedades 0.10 (madrugada del 31 de octubre)
- **Importar carreras GPX** (Perfil → Tus datos): trae tu historial de Strava, Garmin o cualquier app que exporte GPX (varios archivos a la vez). Se calculan parciales y mejores marcas como si fueran carreras propias; las repetidas se saltan.
- **Añadir una carrera a mano** (Actividad): cinta o reloj sin GPS. Cuenta para la semana, el plan, las insignias y las zapatillas; sin mapa ni parciales.
- **La semana pasada** en Inicio (de lunes a miércoles): carreras, km, tiempo, comparación con la anterior y la carrera más larga.
- **Avisos de voz**: ahora cada km, cada 500 m o cada 5 minutos (tiempo, distancia y ritmo), o sin voz.

## Novedades 0.9 (madrugada del 31 de octubre)
- **Fantasma**: al repetir una ruta (recomendada, guardada o una carrera tuya desde Mis recorridos), la pantalla de carrera te compara en directo con tu mejor tiempo en ella: metros por delante o por detrás y segundos de diferencia, y la voz lo dice en cada km. La ficha de la ruta muestra cuántas veces la has corrido y tu mejor tiempo; el resumen te dice si has batido tu marca en esa ruta.
- **Mapa de todas tus carreras** (Actividad): todos tus recorridos superpuestos; donde más repites se ve más intenso. Calles o satélite.
- **Pantalla bloqueada** en carrera: además de la distancia, tiempo, ritmo y la indicación de la guía o la ruta.
- **Bienvenida**: una pantalla nueva explica las tres partes de la app (Correr, Plan, Rutas).

## Novedades 0.8 (madrugada del 31 de octubre)
- **Insignias** (Perfil): 16 logros calculados a partir de tus carreras: primera carrera, 5K, 10K, media, 10 y 50 carreras, 100/500/1000 km, 3 y 10 semanas seguidas, madrugador, nocturno, sesión del plan, primera ruta y 5 rutas distintas. Al terminar una carrera, la pantalla final y la voz te dicen si has ganado alguna.
- **Clima al preparar** (open-meteo, sin cuenta): temperatura y sensación, viento y probabilidad de lluvia, con **consejo de ropa**. El tiempo que hacía se guarda en la carrera y sale en el resumen.
- **Objetivo de km al mes** (ajuste en Perfil): tarjeta en Actividad con progreso, lo que falta y los km por semana que necesitas.
- **Actividad**: filtros Todas · Del plan · Con ruta · Largas · Con notas. Arreglada la gráfica de 12 semanas, que salía vacía.
- **Resumen**: nombre editable (lápiz), sensación de 1 a 5 (Muy duro → Volando) y notas libres por carrera.
- **Guía por ruta**: los avisos de giro por voz se espacian (mínimo 25 s salvo giros cerrados o inminentes) para que no te hable sin parar en caminos sinuosos.

## Novedades 0.7 (noche del 31 de octubre)
- **Rutas v2**: la pestaña es ahora una lista con filtros (cerca de ti / Madrid / San Fernando, distancia, suelo) y un minimapa del trazado en cada tarjeta. Cada ruta tiene **ficha completa**: mapa grande, km, desnivel, tiempo a tu ritmo, nivel, **perfil de desnivel**, suelo, por qué ir y cómo llegar (a cuánto estás de la salida, transporte público). 16 rutas con **trazado fijo** calculado sobre OpenStreetMap (BRouter) y guardado en la app (`routes-data.js`): ya no dependen de ningún servicio externo. "Bucle desde aquí" sigue usando el enrutador en línea.
- **Compartir la carrera como imagen** (botón compartir del resumen): tarjeta 1080×1350 con el trazado y los datos, lista para Telegram, WhatsApp o Instagram.
- **Zapatillas** (Perfil): añade tus pares, cada carrera suma km al par en uso y a los 700 km avisa.
- **Autopausa** (ajuste, activada): si te paras 10 s con buen GPS, pausa sola; al moverte 12 m, sigue.
- **Copia de seguridad** (Perfil): exportar e importar todos tus datos en un archivo. **Exportar GPX** de cada carrera para Strava, Garmin, etc.
- **Resumen hablado** al terminar: distancia, tiempo, ritmo y mejor kilómetro.
- **Actividad**: gráfica de las últimas 12 semanas con media de las 4 últimas y variación.
- **Lectura de la carrera** en el resumen: comparación con tus últimas carreras, reparto del esfuerzo (segunda mitad más rápida o salida demasiado alegre), regularidad, récord de distancia, carreras de la semana y estado de la sesión del plan. Sin IA, con reglas.
- **Plan**: ritmo objetivo por sesión (~mm:ss/km a partir de tu ritmo habitual, como hace Runna); botón **"Hoy no estoy al 100 %"** en Preparar que recorta la sesión un 40 % manteniendo la estructura; **"Copiar mi contexto para la IA"** prepara un resumen de tu plan y tus últimas carreras para pegarlo en ChatGPT, Gemini o Claude.

## Novedades 0.6
- **Te lleva hasta la salida de la ruta**: al elegir una ruta, Preparar dice a cuántos metros está la salida y dibuja una línea lima hasta ella; en carrera la tarjeta muestra "Hacia la salida · 480 m · calentamiento" con una flecha que apunta hacia allí, y avisa por voz al llegar. Si la salida está a más de 2,5 km, recomienda ir en transporte (Cómo llegar, en Rutas).
- **Guía ligera por la ruta**: flecha que indica hacia dónde sigue el camino, metros hasta el siguiente cruce y km que quedan. Voz solo en los giros claros ("en 80 metros, a la izquierda"), al salirte ("la ruta está a 130 m a tu derecha") y al terminar. Sin instrucciones constantes.
- La barra de pestañas queda fija abajo y ya no se solapa con el contenido al hacer scroll (Perfil, Actividad, Inicio).

## Novedades 0.5
- **Rutas recomendadas**: la pestaña Rutas abre ahora en "Recomendadas", 16 sitios investigados para correr tranquilo sin semáforos ni gente: 12 en Madrid (Dehesa de la Villa, Rodríguez Sahagún, anillo del Canal en el Parque de Santander, Ciudad Universitaria, Parque del Oeste, Madrid Río, Casa de Campo, Retiro, El Pardo, Juan Carlos I, Valdebebas) y 4 en San Fernando (Camposoto–Punta del Boquerón, Carrascón, Tres Amigos, vía a Chiclana). Cada tarjeta dice la zona, a cuántos km estás, el suelo, por qué ir y tiene "Cómo llegar" (abre Google Maps en transporte público). Se ordenan por cercanía.
- El trazado real de cada ruta lo calcula el enrutador peatonal de OpenStreetMap a partir de puntos de paso fijados a mano y se guarda en el móvil; mientras llega se muestra un trazado orientativo. La investigación está en `01_Investigacion/2026-10-31_rutas-correr-madrid-san-fernando_v01.md`.
- Los bucles generados desde tu posición siguen disponibles en "Bucle desde aquí".

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
