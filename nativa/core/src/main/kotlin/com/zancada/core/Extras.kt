package com.zancada.core

import kotlin.math.roundToInt

/*
 * Funciones nuevas de la app nativa (no existen en la web): carga de entrenamiento, cadencia,
 * resumen para el widget y textos del modo seguridad por SMS.
 */

private const val DAY_MS = 86_400_000L

/** Aviso de carga: los km de los últimos 7 días frente a la media de las 4 semanas anteriores. */
data class LoadWarn(val week: Double, val avg: Double, val pct: Int, val text: String)

fun App.loadWarn(): LoadWarn? {
    val t = now()
    fun km(from: Long, to: Long) = runs.filter { it.start > from && it.start <= to }.sumOf { it.dist } / 1000
    val week = km(t - 7 * DAY_MS, t)
    val prev = (1..4).map { k -> km(t - 7 * (k + 1) * DAY_MS, t - 7 * k * DAY_MS) }
    if (prev.count { it > 0 } < 3) return null          // poca historia: no hay con qué comparar
    val avg = prev.average()
    if (avg < 5 || week < 8) return null                  // volúmenes pequeños: el porcentaje engaña
    val pct = ((week / avg - 1) * 100).roundToInt()
    if (pct < 30) return null
    return LoadWarn(week, avg, pct,
        "En los últimos 7 días llevas ${fmtKmS(week * 1000)} km, un $pct % más que tu media de las 4 semanas anteriores (${fmtKmS(avg * 1000)} km). " +
            "Subir tanto de golpe aumenta el riesgo de lesión: la próxima, mejor suave o descanso.")
}

/** Consejo de cadencia (pasos por minuto) de una carrera. */
fun cadenceTip(cad: Int?): String? = when {
    cad == null || cad < 100 -> null
    cad < 155 -> "Cadencia de $cad pasos por minuto: algo baja. Prueba pasos un poco más cortos y rápidos; suele reducir el impacto en rodillas."
    cad > 195 -> "Cadencia de $cad pasos por minuto: muy alta. Si vas cómodo, perfecto; si no, alarga un poco la zancada."
    else -> "Cadencia de $cad pasos por minuto: dentro de lo habitual (160–185)."
}

/** Media de pasos por minuto en movimiento. */
fun cadenceOf(steps: Int, movingSec: Double): Int? = if (steps < 60 || movingSec < 60) null else (steps / (movingSec / 60)).roundToInt().takeIf { it in 60..260 }

/** Modo seguridad: textos de los SMS. */
object Sos {
    fun mapUrl(lat: Double, lon: Double) = "https://maps.google.com/?q=${"%.5f".format(java.util.Locale.ROOT, lat)},${"%.5f".format(java.util.Locale.ROOT, lon)}"
    /** Mensaje al salir: el corredor lo envía con un toque (WhatsApp, SMS…). */
    fun start(lat: Double?, lon: Double?) =
        "Salgo a correr con Zancada. Si en un buen rato no te escribo, llámame." + (if (lat != null && lon != null) " Salgo de aquí: ${mapUrl(lat, lon)}" else "")
    fun here(dist: Double, el: Double, lat: Double, lon: Double) = "Estoy corriendo con Zancada: llevo ${fmtKm(dist)} km en ${fmtTime(el)}. Estoy aquí: ${mapUrl(lat, lon)}"
    fun end(dist: Double, el: Double) = "He terminado: ${fmtKm(dist)} km en ${fmtTime(el)}. Todo bien 👍"
    /** Solo números de teléfono: dígitos, espacios y un + inicial. */
    fun cleanPhone(s: String): String = s.trim().let { t -> (if (t.startsWith("+")) "+" else "") + t.filter { it.isDigit() } }.take(16)
    fun validPhone(s: String) = cleanPhone(s).count { it.isDigit() } in 6..15
}
