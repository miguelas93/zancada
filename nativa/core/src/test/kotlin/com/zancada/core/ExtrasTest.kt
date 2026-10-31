package com.zancada.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtrasTest {
    private val now = 1_761_900_000_000L
    private val day = 86_400_000L
    private fun run(daysAgo: Double, km: Double) = Run(id = "r$daysAgo", start = now - (daysAgo * day).toLong(), dist = km * 1000, dur = km * 360)
    private fun app(runs: List<Run>) = App(AppData(runs = runs.sortedByDescending { it.start }), now = { now })

    @Test
    fun avisaSiLaSemanaSubeMasDeUn30PorCiento() {
        val prev = (1..4).flatMap { w -> listOf(run(7.0 * w + 2, 5.0), run(7.0 * w + 4, 5.0)) }   // 10 km/semana
        val w = app(prev + listOf(run(1.0, 8.0), run(3.0, 7.0))).loadWarn()                      // 15 km: +50 %
        assertNotNull(w); assertEquals(50, w.pct); assertTrue(w.text.contains("15 km"))
    }

    @Test
    fun noAvisaConSubidaNormalOPocaHistoria() {
        val prev = (1..4).flatMap { w -> listOf(run(7.0 * w + 2, 5.0), run(7.0 * w + 4, 5.0)) }
        assertNull(app(prev + listOf(run(1.0, 6.0), run(3.0, 6.0))).loadWarn())   // +20 %
        assertNull(app(listOf(run(9.0, 5.0), run(1.0, 20.0))).loadWarn())        // una sola semana previa
    }

    @Test
    fun cadenciaYTelefono() {
        assertEquals(170, cadenceOf(1700, 600.0))
        assertNull(cadenceOf(10, 600.0))
        assertEquals("+34600111222", Sos.cleanPhone(" +34 600 11 12 22 "))
        assertTrue(Sos.validPhone("600 111 222")); assertTrue(!Sos.validPhone("12"))
        assertEquals("https://maps.google.com/?q=40.41680,-3.70380", Sos.mapUrl(40.4168, -3.7038))
    }
}
