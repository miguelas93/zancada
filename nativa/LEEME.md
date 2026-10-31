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

Pendiente para la fase 2: guardar la carrera en curso por si Android cierra la app, migrar los datos
de la beta, mapa, exportar/importar.
