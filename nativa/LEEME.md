# Zancada nativa (Kotlin)

App Android nativa que sustituirá a la web empaquetada. Plan completo en la carpeta del proyecto
(`05_Desarrollo/app-nativa/2026-10-31_plan-app-nativa-kotlin_v02.md`).

- `core/`: cálculos de la carrera en Kotlin puro (GPS, distancia, parciales, pausas, autopausa, mejores marcas, formato).
  Traducción línea a línea de la web. `RunSessionParityTest` corre las mismas carreras de `../tests/fixtures`
  y exige los mismos resultados que `../tests/golden`. Se prueba sin Android: `gradle :core:test`.
- `app/`: la app (Jetpack Compose). `RunService` es un servicio en primer plano con el GPS de Google
  (FusedLocationProvider): sigue grabando con la pantalla apagada y enseña una notificación.

## Fase 1 (esta)

Preparar (GPS) → cuenta atrás → carrera (distancia, tiempo, ritmo actual y medio, pausa, autopausa,
voz y vibración en cada km, mantener pulsado para terminar) → resumen con parciales → lista de carreras.
Se instala **al lado** de la beta (`com.zancada.nativa`, icono «Zancada N»): no toca tus datos.

## Fase 2

- **La carrera en curso no se pierde.** `RunService` guarda una foto de `RunSession` (`live.v1.json`) cada 5 s,
  al pausar, al reanudar y en cada km. Si Android cierra el servicio, se vuelve a arrancar (`START_STICKY`)
  y sigue; si Android no deja (segundo plano), se retoma al abrir la app. El tramo sin GPS se une en línea recta.
  Prueba: `RunSessionParityTest.recuperarLaCarreraNoCambiaNada` mata y recupera la sesión en cada lectura
  (y cada 7 y 97) en las 8 carreras de prueba: el resultado tiene que ser idéntico.
- **Importar las carreras de la app actual.** En la beta, Perfil → «Exportar copia»; en la nativa, tocar
  «Traer mis carreras…» y elegir el archivo (selector de Android, sin permiso de almacenamiento). No duplica
  (mismo id o menos de 1 minuto de diferencia) y guarda los objetos completos para no perder campos.
  Prueba: `BackupImportTest` importa la copia REAL que genera la web (`tests/fixtures/backup`, la crea
  `tests/run-tests.mjs --update`) y comprueba carreras, km, tiempos, parciales y desnivel.
- **Dibujo del recorrido** en el resumen (sin calles; el mapa llega con las rutas).

Pendiente: exportar (GPX/copia) desde la nativa, mapa con calles (fase 3).
