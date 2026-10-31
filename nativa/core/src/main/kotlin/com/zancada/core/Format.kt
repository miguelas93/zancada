package com.zancada.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Igual que la web: redondeo de JavaScript (Math.round: hacia arriba en ,5). */
fun jsRound(x: Double): Long = floor(x + 0.5).toLong()
fun jsRoundI(x: Double): Int = floor(x + 0.5).toInt()

/** toFixed de JavaScript: redondea el valor binario exacto, ,5 hacia arriba. */
fun toFixed(x: Double, n: Int): String {
    if (x.isNaN()) return "NaN"
    if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
    val s = BigDecimal(abs(x)).setScale(n, RoundingMode.HALF_UP).toPlainString()
    return if (x < 0) "-$s" else s
}

/** String(número) de JavaScript para los valores normales de la app (4.2 → "4.2", 5.0 → "5"). */
fun jsNum(x: Double): String {
    if (x.isNaN()) return "NaN"
    if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
    if (x == floor(x) && abs(x) < 1e21) return x.toLong().toString()
    return BigDecimal(x.toString()).stripTrailingZeros().toPlainString()
}

/** 4,2 con coma decimal, como String(km).replace('.', ','). */
fun commaNum(x: Double) = jsNum(x).replace('.', ',')

/** 5146.17 m → "5,15". */
fun fmtKm(m: Double): String = toFixed(m / 1000, 2).replace('.', ',')

/** 5000 → "5", 1500 → "1,5" (km sin ceros de más). */
fun fmtKmS(m: Double): String = toFixed(m / 1000, if (m % 1000 != 0.0) 1 else 0).replace('.', ',')

/** Segundos → "27:03" o "2:02:50". */
fun fmtTime(sec: Double): String {
    val s = max(0L, jsRound(sec))
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return (if (h > 0) "$h:" else "") + m.toString().padStart(2, '0') + ":" + x.toString().padStart(2, '0')
}

/**
 * Segundos por km → "5:15". Sin dato → "–:––".
 * [web] = true reproduce el fallo de la web (redondea los segundos por separado: 299,6 s → "4:60").
 * La app nativa usa la versión arreglada: primero redondea el total (299,6 s → "5:00").
 */
fun fmtPace(secPerKm: Double, web: Boolean = false): String {
    if (!secPerKm.isFinite() || secPerKm <= 0 || secPerKm > 40 * 60) return "–:––"
    if (web) {
        val m = floor(secPerKm / 60).toLong()
        val s = jsRound(secPerKm % 60)
        return "$m:" + s.toString().padStart(2, '0')
    }
    val t = jsRound(secPerKm)
    return "${t / 60}:" + (t % 60).toString().padStart(2, '0')
}

/** "10 m", "950 m", "1,2 km". */
fun fmtM(m: Double): String = if (m < 950) "${max(10L, jsRound(m / 10) * 10)} m" else "${fmtKmS(m)} km"

/** 1974 s → "33 min", 4980 s → "1 h 23 min". */
fun fmtDur(s: Double): String {
    val t = fmtTime(s)
    Regex("^(\\d+):(\\d+):\\d+$").find(t)?.let { return "${it.groupValues[1]} h ${it.groupValues[2]} min" }
    Regex("^(\\d+):\\d+$").find(t)?.let { return "${it.groupValues[1]} min" }
    return t
}

/** Distancia a una ruta: "a 200 m", "a 3,3 km", "a 13 km". */
fun fmtAway(km: Double): String = when {
    km < 1 -> "a ${jsRound(km * 1000)} m"
    km < 10 -> "a ${commaNum(jsRound(km * 10) / 10.0)} km"
    else -> "a ${jsRound(km)} km"
}

/** 90 s → "1,5 min", 60 → "1 min". */
fun fmtMin(sec: Int): String = if (sec % 60 != 0) "${sec / 60},${jsRound((sec % 60) / 6.0)} min" else "${sec / 60} min"

/** ["a","b","c"] → "a, b y c". */
fun listEs(a: List<String>): String = if (a.size < 2) a.joinToString("") else a.dropLast(1).joinToString(", ") + " y " + a.last()

/** Primera letra en mayúscula. */
fun cap(s: String) = if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)

/** Quita < y > (no pueden colar etiquetas) y recorta. */
fun safeText(t: String?, n: Int = 60): String = (t ?: "").replace(Regex("[<>]"), "").trim().take(n)

/** Plural sencillo: n carrera(s). */
fun pl(n: Int, one: String, many: String = one + "s") = if (n == 1) one else many
