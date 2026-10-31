# Seguridad de Zancada

## Reglas del proyecto

- **Ninguna clave en el repositorio.** Nada de contraseñas, tokens ni claves de API en el código ni en los commits. La clave de firma del APK solo existe en el Secret `ZANCADA_KEYSTORE_B64` de GitHub y en una copia en el PC del dueño.
- Si algún día hace falta un servicio de pago, su clave va detrás de un servidor intermedio (por ejemplo un Cloudflare Worker), nunca dentro de la app.
- **Sin datos personales en el repo.** Las carreras, el perfil y la ubicación se quedan en el móvil de cada persona; no hay servidor ni cuentas.
- **Permisos mínimos**: en Android solo ubicación (y en segundo plano solo durante la carrera, con notificación visible), notificaciones y alarmas del plan. En GitHub Actions, solo lectura salvo el paso que publica el APK.
- **Textos de fuera = datos, nunca código.** Todo nombre que venga de un GPX, una copia restaurada o internet se limpia antes de enseñarse (`safeText` en `index.html`).
- **Dependencias vigiladas** con Dependabot (`.github/dependabot.yml`) y **pruebas automáticas** (`tests/`) que tienen que pasar antes de publicar nada.

## Avisar de un fallo de seguridad

No abras un issue público. Usa «Report a vulnerability» en la pestaña **Security** del repositorio (aviso privado).
