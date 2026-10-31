package com.zancada.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.max

/** Igual que la web: redondeo de JavaScript (Math.round: hacia arriba en ,5). */
fun jsRound(x: Double): Long = floor(x + 0.5).toLong()

/** 5146.17 m → "5,15" (como toFixed(2) de JavaScript, sobre el valor exacto). */
fun fmtKm(m: Double): String = BigDecimal(m / 1000).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

/** Segundos → "27:03" o "2:02:50". */
fun fmtTime(sec: Double): String {
    val s = max(0L, jsRound(sec))
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return (if (h > 0) "$h:" else "") + m.toString().padStart(2, '0') + ":" + x.toString().padStart(2, '0')
}

/** Segundos por km → "5:15". Sin dato → "–:––". */
fun fmtPace(secPerKm: Double): String {
    if (!secPerKm.isFinite() || secPerKm <= 0 || secPerKm > 40 * 60) return "–:––"
    val m = floor(secPerKm / 60).toLong()
    // Igual que la web: el resto se redondea por separado (59,6 s → "60"); anotado en tests/README.md.
    val s = jsRound(secPerKm % 60)
    return "$m:" + s.toString().padStart(2, '0')
}
