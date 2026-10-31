package com.zancada.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Fechas en español, escritas a mano para que salgan igual que en la web (Chrome, es-ES) en cualquier móvil:
 * "mié, 21 oct", "21 de octubre de 2026", "sept"…
 */
class EsDate(val zone: ZoneId) {
    companion object {
        val WD_SHORT = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
        val WD_LONG = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
        val MON_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
        val MON_LONG = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    }

    fun z(ts: Long): ZonedDateTime = Instant.ofEpochMilli(ts).atZone(zone)
    private fun ZonedDateTime.wd() = dayOfWeek.value - 1   // 0 = lunes
    private fun ZonedDateTime.mi() = monthValue - 1

    /** "mié, 21 oct" */
    fun wdDayMon(ts: Long) = z(ts).let { "${WD_SHORT[it.wd()]}, ${it.dayOfMonth} ${MON_SHORT[it.mi()]}" }
    /** "10:30" */
    fun hm(ts: Long) = z(ts).let { "%02d:%02d".format(it.hour, it.minute) }
    /** "mié, 21 oct · 10:30" (fmtDate de la web) */
    fun fmtDate(ts: Long) = wdDayMon(ts) + " · " + hm(ts)
    /** "miércoles, 21 de octubre" */
    fun wdLongDayMonLong(ts: Long) = z(ts).let { "${WD_LONG[it.wd()]}, ${it.dayOfMonth} de ${MON_LONG[it.mi()]}" }
    /** "miércoles 21" */
    fun wdLongDay(ts: Long) = z(ts).let { "${WD_LONG[it.wd()]} ${it.dayOfMonth}" }
    /** "octubre de 2026" */
    fun monYear(ts: Long) = z(ts).let { "${MON_LONG[it.mi()]} de ${it.year}" }
    /** "octubre" */
    fun monLong(ts: Long) = z(ts).let { MON_LONG[it.mi()] }
    /** "21 oct" */
    fun dayMon(ts: Long) = z(ts).let { "${it.dayOfMonth} ${MON_SHORT[it.mi()]}" }
    /** "21 oct 2026" */
    fun dayMonYear(ts: Long) = z(ts).let { "${it.dayOfMonth} ${MON_SHORT[it.mi()]} ${it.year}" }
    /** "21 de octubre de 2026" */
    fun dayMonLongYear(ts: Long) = z(ts).let { "${it.dayOfMonth} de ${MON_LONG[it.mi()]} de ${it.year}" }
    /** "oct 26" */
    fun monShortYY(ts: Long) = z(ts).let { "${MON_SHORT[it.mi()]} ${(it.year % 100).toString().padStart(2, '0')}" }
    /** "21/10/2026" */
    fun numeric(ts: Long) = z(ts).let { "${it.dayOfMonth}/${it.monthValue}/${it.year}" }
    /** Día de la semana, 0 = lunes. */
    fun dow(ts: Long) = z(ts).wd()
    fun hour(ts: Long) = z(ts).hour
}
