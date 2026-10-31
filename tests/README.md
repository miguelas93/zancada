# Pruebas de Zancada

Batería automática para que un cambio nuevo no rompa lo que ya funcionaba (regresiones).
Se ejecuta sola en GitHub en cada cambio (`.github/workflows/apk.yml`, trabajo «Pruebas»):
**si falla, no se compila ni se publica el APK.**

## Qué comprueba

1. **Carreras simuladas** (`fixtures/tracks/`). Ocho carreras con GPS falso y reloj falso que se «corren» en la app de verdad:
   parque 5K, bajo árboles (lecturas malas y saltos), pausa manual, semáforo con autopausa, pantalla bloqueada
   (lecturas que llegan de golpe y un hueco sin GPS), cuestas, carrera de 20 m y media maratón.
   Km, tiempo, parciales, desnivel, mejores marcas y la pantalla final tienen que coincidir con `golden/`.
2. **Pantallas**: con esas carreras guardadas se abren Inicio, Actividad, Rutas, Perfil, Plan, Clubs, mapa de calor,
   cada resumen y la repetición sin un solo error de JavaScript; Rutas no puede salir vacía y Perfil tiene que tener récords.
3. **Copia de seguridad en el APK**: «Exportar copia» escribe el archivo con todas las carreras y abre el menú de compartir.

Los mismos `fixtures/` y `golden/` servirán para comprobar que la **app nativa en Kotlin** calcula exactamente lo mismo.

## Cómo se usa

```bash
cd tests && npm install && npx playwright install chromium   # una vez
node tests/run-tests.mjs              # comprobar
node tests/run-tests.mjs --update     # aceptar un cambio INTENCIONADO de resultados (revisa el diff de golden/)
node tests/run-tests.mjs --only=parque
node tests/tools/make-tracks.mjs      # regenerar las carreras (semilla fija: salen siempre iguales)
```

Regla: cada fallo que aparezca en la app se convierte primero en una prueba que falla, y luego se arregla.

## Cosas detectadas al crear las pruebas (a revisar, no cambiadas aún)

- **Desnivel inflado con altímetro ruidoso**: en `cuestas` (60 m reales de subida, ruido ±3 m) la app suma +108 m.
  El filtro de 4 m se queda corto. Se corregirá a propósito (actualizando `golden/`) en la versión nativa.
- **El ruido del GPS suma distancia**: con buen GPS sale ≈ +2–3 % (5,15 km en un 5K de 5,03), bajo árboles ≈ +6 %.
  Strava suaviza el trazado; la nativa debería hacerlo también.
- **Terminar en pausa**: si terminas la carrera estando en pausa, `elapsed()` descuenta la pausa dos veces y el tiempo sale corto. La versión Kotlin lo replica a propósito (paridad) hasta corregirlo en las dos a la vez.
- **Ritmo «5:60»**: `fmtPace` redondea los segundos aparte, así que 5:59,6 se escribe «5:60». Igual en Kotlin, a corregir en las dos.

## App nativa

`nativa/core` (Kotlin) usa estos mismos `fixtures/` y `golden/`: `cd nativa && ./gradlew :core:test` tiene que dar lo mismo que la web.
